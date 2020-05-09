/* 
 * Copyright (c) 2011-2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.MyAction;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.notification.NotificationData;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.vavr.control.Try;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.notification.NotificationEventType.SERVICE_RUNNING;

/**
 * This service asynchronously executes commands, mostly related to communication
 * between this Android Device and Social networks.
 */
public class MyService extends Service {
    private static final String TAG = MyService.class.getSimpleName();
    private final static long STOP_ON_INACTIVITY_AFTER_SECONDS = 10;

    protected final long instanceId = InstanceId.next();
    private volatile MyContext myContext = MyContext.EMPTY;
    private volatile long startedForegrounLastTime = 0;
    /** No way back */
    private volatile boolean mForcedToStop = false;
    private volatile long latestActivityTime = 0;

    /** Flag to control the Service state persistence */
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile long initializedTime = 0;
    /** We are stopping this service */
    private final AtomicBoolean isStopping = new AtomicBoolean(false);

    private final AtomicReference<QueueExecutor> executorRef = new AtomicReference<>();
    private final AtomicReference<HeartBeat> heartBeatRef = new AtomicReference<>();

    /**
     * The reference to the wake lock used to keep the CPU from stopping during
     * background operations.
     */
    private final AtomicReference<PowerManager.WakeLock> wakeLockRef = new AtomicReference<>();

    private static final AtomicBoolean widgetsInitialized = new AtomicBoolean(false);

    private MyServiceState getServiceState() {
        if (initialized.get()) {
            if (isStopping.get()) {
                return MyServiceState.STOPPING;
            } else {
                return MyServiceState.RUNNING;
            }
        }
        return MyServiceState.STOPPED;
    }

    private boolean isStopping() {
        return isStopping.get();
    }
    
    @Override
    public void onCreate() {
        MyLog.v(TAG, () -> "MyService " + instanceId + " created");
        myContext = myContextHolder.initialize(this).getNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.v(TAG, () -> "MyService " + instanceId + " onStartCommand: startid=" + startId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground();
        }
        receiveCommand(intent, startId);
        return START_NOT_STICKY;
    }

    /** See https://stackoverflow.com/questions/44425584/context-startforegroundservice-did-not-then-call-service-startforeground */
    private void startForeground() {
        long currentTimeMillis = System.currentTimeMillis();
        if (Math.abs(currentTimeMillis - startedForegrounLastTime) < 1000) return;

        startedForegrounLastTime = currentTimeMillis;
        final NotificationData data = new NotificationData(SERVICE_RUNNING, Actor.EMPTY, currentTimeMillis);
        myContext.getNotifier().createNotificationChannel(data);
        startForeground(SERVICE_RUNNING.notificationId(), myContext.getNotifier().getAndroidNotification(data));
    }

    @GuardedBy("serviceStateLock")
    private BroadcastReceiver intentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            MyLog.v(TAG, () -> "MyService " + instanceId + " onReceive " + intent.toString());
            receiveCommand(intent, 0);
        }
    };
    
    private void receiveCommand(Intent intent, int startId) {
        CommandData commandData = CommandData.fromIntent(myContext, intent);
        switch (commandData.getCommand()) {
            case STOP_SERVICE:
                MyLog.v(TAG, () -> "MyService " + instanceId + " command " + commandData.getCommand() + " received");
                stopDelayed(false);
                break;
            case BROADCAST_SERVICE_STATE:
                if (isStopping.get()) {
                    stopDelayed(false);
                }
                broadcastAfterExecutingCommand(commandData);
                break;
            default:
                latestActivityTime = System.currentTimeMillis();
                if (isForcedToStop()) {
                    stopDelayed(true);
                } else {
                    ensureInitialized();
                    startStopExecution();
                }
                break;
        }
    }

    private boolean isForcedToStop() {
        return mForcedToStop || myContextHolder.isShuttingDown();
    }

    private void broadcastAfterExecutingCommand(CommandData commandData) {
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
        .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }
    
    void ensureInitialized() {
        if (initialized.get() || isStopping.get()) return;

        if (!myContext.isReady()) {
            myContext = myContextHolder.initialize(this).getBlocking();
            if (!myContext.isReady()) return;
        }

        if (initialized.compareAndSet(false, true)) {
            initializedTime = System.currentTimeMillis();
            registerReceiver(intentReceiver, new IntentFilter(MyAction.EXECUTE_COMMAND.getAction()));
            if (widgetsInitialized.compareAndSet(false, true)) {
                AppWidgets.of(myContext).updateViews();
            }
            reviveHeartBeat();
            MyLog.d(TAG, "MyService " + instanceId + " initialized");
            MyServiceEventsBroadcaster.newInstance(myContext, getServiceState()).broadcast();
        }
    }

    private void reviveHeartBeat() {
        final HeartBeat previous = heartBeatRef.get();
        boolean replace = previous == null;
        if (!replace && !previous.isReallyWorking()) {
            replace = true;
        }
        if (replace) {
            HeartBeat current = new HeartBeat();
            if (heartBeatRef.compareAndSet(previous, current)) {
                if (previous != null) {
                    previous.cancelLogged(true);
                }
                Try<Void> result = AsyncTaskLauncher.execute(TAG, current);
                if (result.isFailure()) {
                    heartBeatRef.compareAndSet(current, null);
                    MyLog.w(TAG, "MyService " + instanceId + " Failed to revive heartbeat. " + result);
                }
            }
        }
    }

    private void startStopExecution() {
        if (!initialized.get()) return;

        if (isStopping.get() || !myContext.isReady() || isForcedToStop() || (
                !isAnythingToExecuteNow()
                && RelativeTime.moreSecondsAgoThan(latestActivityTime, STOP_ON_INACTIVITY_AFTER_SECONDS)
                && !isExecutorReallyWorkingNow())) {
            stopDelayed(false);
        } else if (isAnythingToExecuteNow()) {
            startExecution();
        }
    }

    private void startExecution() {
        acquireWakeLock();
        try {
            ensureExecutorStarted();
        } catch (Exception e) {
            MyLog.i(TAG, "Couldn't start executor", e);
            stopExecutor(true);
            releaseWakeLock();
        }
    }
    
    private void ensureExecutorStarted() {
        final String method = "ensureExecutorStarted";
        MyStringBuilder logMessageBuilder = new MyStringBuilder();
        QueueExecutor previous = executorRef.get();
        boolean replace = previous == null;
        if ( !replace && previous.completedBackgroundWork()) {
            logMessageBuilder.withComma("Removing completed Executor " + previous);
            replace = true;
        }
        if ( !replace && !isExecutorReallyWorkingNow()) {
            logMessageBuilder.withComma("Cancelling stalled Executor " + previous);
            replace = true;
        }
        if (replace) {
            // For now let's have only ONE working thread
            // (it seems there is some problem in parallel execution...)
            QueueExecutor current = new QueueExecutor();
            if (replaceExecutor(logMessageBuilder, previous, current)) {
                logMessageBuilder.withComma("Starting new Executor " + current);
                if (AsyncTaskLauncher.execute(TAG, current).isFailure()) {
                    logMessageBuilder.withComma("Failed to start new executor");
                    replaceExecutor(logMessageBuilder, current, null);
                }
            }
        } else {
            logMessageBuilder.append(" There is an Executor already " + previous);
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(TAG, () -> "MyService " + instanceId + " " + method + "; " + logMessageBuilder);
        }
    }

    private boolean replaceExecutor(MyStringBuilder logMessageBuilder, QueueExecutor previous, QueueExecutor current) {
        if (executorRef.compareAndSet(previous, current)) {
            if (previous == null) {
                logMessageBuilder.withComma(current == null
                        ? "No executor"
                        : "Executor set to " + current);
            } else {
                if (previous.needsBackgroundWork()) {
                    logMessageBuilder.withComma("Cancelling previous");
                    previous.cancelLogged(true);
                }
                logMessageBuilder.withComma(current == null
                        ? "Removed executor " + previous
                        : "Replaced executor " + previous + " with " + current);
            }
            return true;
        }
        return false;
    }

    private void acquireWakeLock() {
        PowerManager.WakeLock previous = wakeLockRef.get();
        if (previous == null) {
            MyLog.v(TAG, () -> "MyService " + instanceId + " acquiring wakelock");
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                MyLog.w(TAG, "No Power Manager ???");
                return;
            }

            PowerManager.WakeLock current = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, MyService.class.getName());
            if (current != null && wakeLockRef.compareAndSet(previous, current)) {
                current.acquire();
            }
        }
    }

    private boolean isAnythingToExecuteNow() {
        return myContext.queues().isAnythingToExecuteNow() || isExecutorReallyWorkingNow();
    }
    
    private boolean isExecutorReallyWorkingNow() {
        return Optional.ofNullable(executorRef.get()).map(QueueExecutor::isReallyWorking).orElse(false);
    }
    
    @Override
    public void onDestroy() {
        if (initialized.get()) {
            mForcedToStop = true;
            MyLog.v(TAG, () -> "MyService " + instanceId + " onDestroy");
            stopDelayed(true);
        }
        MyLog.v(TAG, () -> "MyService " + instanceId + " destroyed");
        MyLog.setNextLogFileName();
    }
    
    /**
     * Notify background processes that the service is stopping.
     * Stop if background processes has finished.
     * Persist everything that we'll need on next Service creation and free resources
     */
    private void stopDelayed(boolean forceNow) {
        if (isStopping.compareAndSet(false, true)) {
            MyLog.v(TAG, () -> "MyService " + instanceId + " stopping" + (forceNow ? ", forced" : ""));
        }
        startedForegrounLastTime = 0;
        if (!stopExecutor(forceNow) && !forceNow) {
            return;
        }
        unInitialize();
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
                .setEvent(MyServiceEvent.ON_STOP).broadcast();
    }

    private void unInitialize() {
        if (!initialized.compareAndSet(true, false)) return;

        try {
            unregisterReceiver(intentReceiver);
        } catch (Exception e) {
            MyLog.d(TAG, "MyService " + instanceId + " on unregisterReceiver", e);
        }
        mForcedToStop = false;

        final HeartBeat heartBeat = heartBeatRef.get();
        if (heartBeat != null && heartBeatRef.compareAndSet(heartBeat, null)) {
            heartBeat.cancelLogged(true);
        }

        AsyncTaskLauncher.cancelPoolTasks(MyAsyncTask.PoolEnum.SYNC);
        releaseWakeLock();
        stopSelf();
        myContext.getNotifier().clearAndroidNotification(SERVICE_RUNNING);
        MyLog.i(TAG, "MyService " + instanceId + " stopped, myServiceWorkMs:" + (System.currentTimeMillis() - initializedTime));
        isStopping.set(false);
    }

    private boolean stopExecutor(boolean forceNow) {
        final String method = "couldStopExecutor";
        MyStringBuilder logMessageBuilder = new MyStringBuilder();

        QueueExecutor executor = executorRef.get();
        boolean success = executor == null;
        boolean doStop = !success;
        if (doStop && executor.needsBackgroundWork() && executor.isReallyWorking() ) {
            if (forceNow) {
                logMessageBuilder.withComma("Cancelling working Executor;");
            } else {
                logMessageBuilder.withComma("Cannot stop now Executor " + executor);
                doStop = false;
            }
        }
        if (doStop) {
            success = replaceExecutor(logMessageBuilder, executor, null);
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(TAG, () -> "MyService " + instanceId + " " + method + "; " + logMessageBuilder);
        }
        return success;
    }

    private void releaseWakeLock() {
        PowerManager.WakeLock wakeLock = wakeLockRef.get();
        if (wakeLock != null && wakeLockRef.compareAndSet(wakeLock, null)) {
            MyLog.v(TAG, () -> "MyService " + instanceId + " releasing wakelock");
            wakeLock.release();
        }
    }

    private class QueueExecutor extends MyAsyncTask<Void, Void, Boolean> implements CommandExecutorParent {
        private volatile CommandData currentlyExecuting = null;
        private static final long MAX_EXECUTION_TIME_SECONDS = 60;
        private final static String TAG = "QueueExecutor";

        QueueExecutor() {
            super(TAG, PoolEnum.SYNC);
        }

        @Override
        protected Boolean doInBackground2(Void aVoid) {
            MyLog.v(this, () -> "Started, " + myContext.queues().totalSizeToExecute() + " commands to process");
            myContext.queues().moveCommandsFromSkippedToMainQueue();
            final String breakReason;
            do {
                if (isStopping()) {
                    breakReason = "isStopping";
                    break;
                }
                if (isCancelled()) {
                    breakReason = "Cancelled";
                    break;
                }
                if (RelativeTime.secondsAgo(backgroundStartedAt) > MAX_EXECUTION_TIME_SECONDS) {
                    breakReason = "Executed too long";
                    break;
                }
                if (executorRef.get() != this) {
                    breakReason = "Other executor";
                    break;
                }
                CommandData commandData = myContext.queues().pollQueue();
                currentlyExecuting = commandData;
                currentlyExecutingSince = System.currentTimeMillis();
                if (commandData == null) {
                    breakReason = "No more commands";
                    break;
                }
                ConnectionState connectionState = myContext.getConnectionState();
                if (commandData.getCommand().getConnectionRequired().isConnectionStateOk(connectionState)) {
                    MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
                            .setCommandData(commandData)
                            .setEvent(MyServiceEvent.BEFORE_EXECUTING_COMMAND).broadcast();
                    CommandExecutorStrategy.executeCommand(commandData, this);
                } else {
                    commandData.getResult().incrementNumIoExceptions();
                    commandData.getResult().setMessage("Expected '"
                            + commandData.getCommand().getConnectionRequired()
                            + "', but was '" + connectionState + "' connection");
                }
                if (commandData.getResult().shouldWeRetry()) {
                    myContext.queues().addToQueue(QueueType.RETRY, commandData);
                } else if (commandData.getResult().hasError()) {
                    myContext.queues().addToQueue(QueueType.ERROR, commandData);
                }
                broadcastAfterExecutingCommand(commandData);
                addSyncOfThisToQueue(commandData);
            } while (true);
            MyLog.v(this, () -> "Ended, " + breakReason + ", " + myContext.queues().totalSizeToExecute() + " commands left");
            myContext.queues().save();
            return true;
        }

        private void addSyncOfThisToQueue(CommandData commandDataExecuted) {
            if (commandDataExecuted.getResult().hasError()
                    || commandDataExecuted.getCommand() != CommandEnum.UPDATE_NOTE
                    || !SharedPreferencesUtil.getBoolean(
                            MyPreferences.KEY_SYNC_AFTER_NOTE_WAS_SENT, false)) {
                return;
            }
            myContext.queues().addToQueue(QueueType.CURRENT, CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                    commandDataExecuted.getTimeline().myAccountToSync, TimelineType.HOME)
                    .setInForeground(commandDataExecuted.isInForeground()));
        }

        @Override
        protected void onFinish(Boolean aBoolean, boolean success) {
            latestActivityTime = System.currentTimeMillis();
            MyLog.v(TAG, success ? "onExecutorSuccess" : "onExecutorFailure");
            currentlyExecuting = null;
            currentlyExecutingSince = 0;
            reviveHeartBeat();
            startStopExecution();
        }

        @Override
        public boolean isStopping() {
            return MyService.this.isStopping();
        }

        @Override
        public String classTag() {
            return TAG;
        }

        @Override
        public String toString() {
            MyStringBuilder sb = new MyStringBuilder();
            if (currentlyExecuting != null && currentlyExecutingSince > 0) {
                sb.withComma("currentlyExecuting",currentlyExecuting);
                sb.withComma("since",RelativeTime.getDifference(getBaseContext(), currentlyExecutingSince));
            }
            if (isStopping()) {
                sb.withComma("stopping");
            }
            sb.withComma(super.toString());
            return sb.toKeyValue(this);
        }

    }
    
    private class HeartBeat extends MyAsyncTask<Void, Long, Void> {
        private final static String TAG = "HeartBeat";
        private static final long HEARTBEAT_PERIOD_SECONDS = 11;
        private volatile long previousBeat = createdAt;
        private volatile long mIteration = 0;

        HeartBeat() {
            super(TAG, PoolEnum.SYNC);
        }

        @Override
        protected Void doInBackground2(Void aVoid) {
            MyLog.v(this, () -> "Started");
            String breakReason = "";
            for (long iteration = 1; iteration < 10000; iteration++) {
                final HeartBeat heartBeat = heartBeatRef.get();
                if (heartBeat != null && heartBeat != this && heartBeat.isReallyWorking() ) {
                    breakReason = "Other instance found: " + heartBeat;
                    break;
                }

                if (isCancelled()) {
                    breakReason = "Cancelled";
                    break;
                }
                if (DbUtils.waitMs("HeartBeatSleeping",
                        Math.toIntExact(java.util.concurrent.TimeUnit.SECONDS.toMillis(HEARTBEAT_PERIOD_SECONDS)))) {
                    breakReason = "InterruptedException";
                    break;
                }
                if (!initialized.get()) {
                    breakReason = "Not initialized";
                    break;
                }
                publishProgress(iteration);
            }
            String breakReasonVal = breakReason;
            MyLog.v(this, () -> "Ended " + breakReasonVal + "; " + this);
            heartBeatRef.compareAndSet(this, null);
            return null;
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            mIteration = values[0];
            previousBeat = MyLog.uniqueCurrentTimeMS();
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, () -> "onProgressUpdate; " + this);
            }
            if (MyLog.isDebugEnabled() && RelativeTime.moreSecondsAgoThan(createdAt,
                    QueueExecutor.MAX_EXECUTION_TIME_SECONDS)) {
                MyLog.d(this, AsyncTaskLauncher.threadPoolInfo());
            }
            startStopExecution();
        }

        @Override
        public String toString() {
            return instanceTag() + "; " + super.toString();
        }

        @Override
        public String instanceTag() {
            return super.instanceTag() + "-it" + mIteration;
        }

        @Override
        public String classTag() {
            return TAG;
        }

        @Override
        public boolean isReallyWorking() {
            return needsBackgroundWork() && !RelativeTime.
                    wasButMoreSecondsAgoThan(previousBeat, HEARTBEAT_PERIOD_SECONDS * 2);
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
