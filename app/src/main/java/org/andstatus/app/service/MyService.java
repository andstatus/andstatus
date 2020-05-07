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
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.notification.NotificationEventType.SERVICE_RUNNING;
import static org.andstatus.app.service.CommandEnum.CLEAR_COMMAND_QUEUE;
import static org.andstatus.app.service.CommandEnum.DELETE_COMMAND;

/**
 * This service asynchronously executes commands, mostly related to communication
 * between this Android Device and Social networks.
 */
public class MyService extends Service {
    private volatile MyContext myContext = MyContext.EMPTY;
    private final Object serviceStateLock = new Object();
    private volatile long startedForegrounLastTime = 0;
    /** We are going to finish this service. But may rethink...  */
    @GuardedBy("serviceStateLock")
    private boolean mIsStopping = false;
    /** No way back */
    @GuardedBy("serviceStateLock")
    private boolean mForcedToStop = false;
    private final static long START_TO_STOP_CHANGE_MIN_PERIOD_SECONDS = 10;
    @GuardedBy("serviceStateLock")
    private long decidedToChangeIsStoppingAt = 0;
    /** Flag to control the Service state persistence */
    @GuardedBy("serviceStateLock")
    private boolean mInitialized = false;

    @GuardedBy("serviceStateLock")
    private int mLatestProcessedStartId = 0;
    
    private final Object executorLock = new Object();
    @GuardedBy("executorLock")
    private QueueExecutor mExecutor = null;

    private final Object heartBeatLock = new Object();
    @GuardedBy("heartBeatLock")
    private HeartBeat mHeartBeat = null;

    private final Object wakeLockLock = new Object();
    /**
     * The reference to the wake lock used to keep the CPU from stopping during
     * background operations.
     */
    @GuardedBy("wakeLockLock")
    private PowerManager.WakeLock mWakeLock = null;

    private static final AtomicBoolean widgetsInitialized = new AtomicBoolean(false);

    private MyServiceState getServiceState() {
        MyServiceState state = MyServiceState.STOPPED; 
        synchronized (serviceStateLock) {
            if (mInitialized) {
                if (mIsStopping) {
                    state = MyServiceState.STOPPING;
                } else {
                    state = MyServiceState.RUNNING;
                }
            }
        }
        return state;
    }
    private boolean isStopping() {
        synchronized (serviceStateLock) {
            return mIsStopping;
        }
    }
    
    @Override
    public void onCreate() {
        MyLog.d(this, "Service created");
        initializeMyContext();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.d(this, "onStartCommand: startid=" + startId);
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
        getMyContext().getNotifier().createNotificationChannel(data);
        startForeground(SERVICE_RUNNING.notificationId(), getMyContext().getNotifier().getAndroidNotification(data));
    }

    @GuardedBy("serviceStateLock")
    private BroadcastReceiver intentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            MyLog.v(this, () -> "onReceive " + intent.toString());
            receiveCommand(intent, 0);
        }
    };
    
    private void receiveCommand(Intent intent, int startId) {
        CommandData commandData = CommandData.fromIntent(getMyContext(), intent);
        switch (commandData.getCommand()) {
            case STOP_SERVICE:
                MyLog.v(this, () -> "Command " + commandData.getCommand() + " received");
                stopDelayed(false);
                break;
            case BROADCAST_SERVICE_STATE:
                broadcastAfterExecutingCommand(commandData);
                break;
            default:
                if (isForcedToStop()) {
                    stopDelayed(false);
                } else {
                    initialize();
                    startStopExecution();
                }
                break;
        }
        synchronized (serviceStateLock) {
            if (startId > mLatestProcessedStartId) {
                mLatestProcessedStartId = startId;
            }
        }
    }

    private boolean isForcedToStop() {
        synchronized (serviceStateLock) {
            return mForcedToStop || myContextHolder.isShuttingDown();
        }
    }

    private void broadcastAfterExecutingCommand(CommandData commandData) {
        MyServiceEventsBroadcaster.newInstance(getMyContext(), getServiceState())
        .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }
    
    void initialize() {
        boolean changed = false;
        boolean wasNotInitialized = false;
        MyContext myContext = getMyContext();
        if (!mInitialized) {
            initializeMyContext();
            synchronized (serviceStateLock) {
                if (!mInitialized) {
                    wasNotInitialized = true;
                    myContext = getMyContext();
                    if (myContext.isReady()) {
                        registerReceiver(intentReceiver, new IntentFilter(MyAction.EXECUTE_COMMAND.getAction()));
                        mInitialized = true;
                        changed = true;
                    }
                }
            }
        }
        if (mInitialized && myContext.isReady()) {
            if (wasNotInitialized) {
                if (widgetsInitialized.compareAndSet(false, true)) {
                    AppWidgets.of(myContext).updateViews();
                }
                reviveHeartBeat();
            }
            if (changed) {
                MyServiceEventsBroadcaster.newInstance(myContext, getServiceState()).broadcast();
            }
        }
    }

    private void reviveHeartBeat() {
        synchronized(heartBeatLock) {
            if (mHeartBeat != null && !mHeartBeat.isReallyWorking()) {
                mHeartBeat.cancelLogged(true);
                mHeartBeat = null;
            }
            if (mHeartBeat == null) {
                mHeartBeat = new HeartBeat();
                if (AsyncTaskLauncher.execute(this, mHeartBeat).isFailure()) {
                    mHeartBeat = null;
                }
            }
        }
    }

    private void startStopExecution() {
        switch (shouldStop()) {
            case TRUE:
                stopDelayed(false);
                break;
            case FALSE:
                startExecution();
                break;
            default:
                MyLog.v(this, () -> "Didn't change execution " + mExecutor);
                break;
        }
    }

    private TriState shouldStop() {
        boolean doStop = !getMyContext().isReady() || isForcedToStop() || !isAnythingToExecuteNow();
        if (!setIsStopping(doStop, false)) {
            return TriState.UNKNOWN;
        }
        if (doStop) {
            return TriState.TRUE;
        } else {
            return TriState.FALSE;
        }
    }

    /** @return true if succeeded */
    private boolean setIsStopping(boolean doStop, boolean forceStopNow) {
        boolean decided = false;
        boolean success = false;
        StringBuilder logMsg = new StringBuilder("setIsStopping ");
        synchronized (serviceStateLock) {
            if (doStop == mIsStopping) {
                logMsg.append("Continuing " + (doStop ? "stopping" : "starting"));
                decided = true;
                success = true;
            }
            if (!decided && !mInitialized && !doStop) {
                logMsg.append("Cannot start when not initialized");
                decided = true;
            }
            if (!decided && !doStop && mForcedToStop) {
                logMsg.append("Cannot start due to forcedToStop flag");
                decided = true;
            }
            if (!decided
                    && doStop
                    && !RelativeTime.moreSecondsAgoThan(decidedToChangeIsStoppingAt,
                            START_TO_STOP_CHANGE_MIN_PERIOD_SECONDS)) {
                if (forceStopNow) {
                    logMsg.append("Forced to stop");
                    success = true;
                } else {
                    logMsg.append("Cannot stop now, decided to start only "
                            + RelativeTime.secondsAgo(decidedToChangeIsStoppingAt) + " second ago");
                }
                decided = true;
            }
            if (!decided) {
                success = true;
                if (doStop) {
                    logMsg.append("Stopping");
                } else {
                    logMsg.append("Starting");
                }
            }
            if (success && doStop != mIsStopping) {
                decidedToChangeIsStoppingAt = System.currentTimeMillis();
                mIsStopping = doStop;
            }
        }
        if (success) {
            logMsg.append("; startId=" + getLatestProcessedStartId());
        }
        if (success && doStop) {
            logMsg.append("; " + (myContext.queues().totalSizeToExecute() == 0 ? "queue is empty" : "queueSize="
                    + myContext.queues().totalSizeToExecute()));
        }
        MyLog.v(this, logMsg::toString);
        return success;
    }

    private int getLatestProcessedStartId() {
        synchronized (serviceStateLock) {
            return mLatestProcessedStartId;
        }
    }
    
    private void startExecution() {
        acquireWakeLock();
        try {
            ensureExecutorStarted();
        } catch (Exception e) {
            MyLog.i(this, "Couldn't start executor", e);
            couldStopExecutor(true);
            releaseWakeLock();
        }
    }
    
    private void ensureExecutorStarted() {
        final String method = "ensureExecutorStarted";
        StringBuilder logMessageBuilder = new StringBuilder();
        synchronized(executorLock) {
            if ( mExecutor != null && mExecutor.completedBackgroundWork()) {
                logMessageBuilder.append(" Removing completed Executor " + mExecutor);
                removeExecutor(logMessageBuilder);
            }
            if ( mExecutor != null && !isExecutorReallyWorkingNow()) {
                logMessageBuilder.append(" Cancelling stalled Executor " + mExecutor);
                removeExecutor(logMessageBuilder);
            }
            if (mExecutor != null) {
                logMessageBuilder.append(" There is an Executor already " + mExecutor);
            } else {
                // For now let's have only ONE working thread 
                // (it seems there is some problem in parallel execution...)
                QueueExecutor newExecutor = new QueueExecutor();
                logMessageBuilder.append(" Adding and starting new Executor " + newExecutor);
                if (AsyncTaskLauncher.execute(this, newExecutor).isSuccess()) {
                    mExecutor = newExecutor;
                } else {
                    logMessageBuilder.append(" New executor was not added");
                }
            }
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(this, () -> method + "; " + logMessageBuilder);
        }
    }
    
    private void removeExecutor(StringBuilder logMessageBuilder) {
        synchronized(executorLock) {
            if (mExecutor == null) {
                return;
            }
            if (mExecutor.needsBackgroundWork()) {
                logMessageBuilder.append(" Cancelling and");
                mExecutor.cancelLogged(true);
            }
            logMessageBuilder.append(" Removing Executor " + mExecutor);
            mExecutor = null;
        }
    }

    private void acquireWakeLock() {
        synchronized(wakeLockLock) {
            if (mWakeLock == null) {
                MyLog.d(this, "Acquiring wakelock");
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, MyService.class.getName());
                mWakeLock.acquire();
            }
        }
    }

    private boolean isAnythingToExecuteNow() {
        return myContext.queues().isAnythingToExecuteNow() || isExecutorReallyWorkingNow();
    }
    
    private boolean isExecutorReallyWorkingNow() {
        synchronized(executorLock) {
          return mExecutor != null && mExecutor.isReallyWorking();
        }        
    }
    
    @Override
    public void onDestroy() {
        boolean initialized;
        synchronized (serviceStateLock) {
            mForcedToStop = true;
            initialized = mInitialized;
        }
        if (initialized) {
            MyLog.v(this, "onDestroy");
            stopDelayed(true);
        }
        MyLog.d(this, "Service destroyed");
        MyLog.setNextLogFileName();
    }
    
    /**
     * Notify background processes that the service is stopping.
     * Stop if background processes has finished.
     * Persist everything that we'll need on next Service creation and free resources
     */
    private void stopDelayed(boolean forceNow) {
        startedForegrounLastTime = 0;

        if (!setIsStopping(true, forceNow) && !forceNow) {
            return;
        }
        if (!couldStopExecutor(forceNow) && !forceNow) {
            return;
        }
        unInitialize();
        MyServiceEventsBroadcaster.newInstance(getMyContext(), getServiceState())
                .setEvent(MyServiceEvent.ON_STOP).broadcast();
    }

    private void unInitialize() {
        int latestProcessedStartId = 0;
        synchronized (serviceStateLock) {
            if( mInitialized) {
                try {
                    unregisterReceiver(intentReceiver);
                } catch (Exception e) {
                    MyLog.d(this, "On unregisterReceiver", e);
                }
                latestProcessedStartId = mLatestProcessedStartId;
                mInitialized = false;
                mIsStopping = false;
                mForcedToStop = false;
                mLatestProcessedStartId = 0;
                decidedToChangeIsStoppingAt = 0;
            }
        }
        synchronized(heartBeatLock) {
            if (mHeartBeat != null) {
                mHeartBeat.cancelLogged(true);
                mHeartBeat = null;
            }
        }
        AsyncTaskLauncher.cancelPoolTasks(MyAsyncTask.PoolEnum.SYNC);
        releaseWakeLock();
        stopSelfResult(latestProcessedStartId);
        getMyContext().getNotifier().clearAndroidNotification(SERVICE_RUNNING);
    }

    private boolean couldStopExecutor(boolean forceNow) {
        final String method = "couldStopExecutor";
        StringBuilder logMessageBuilder = new StringBuilder();
        boolean could = true;
        synchronized(executorLock) {
            if (mExecutor != null && mExecutor.needsBackgroundWork() && mExecutor.isReallyWorking() ) {
                if (forceNow) {
                    logMessageBuilder.append(" Cancelling working Executor;");
                } else {
                    logMessageBuilder.append(" Cannot stop now Executor " + mExecutor);
                    could = false;
                }
            }
            if (could) {
                removeExecutor(logMessageBuilder);
            }
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(this, () -> method + "; " + logMessageBuilder);
        }
        return could;
    }

    private void releaseWakeLock() {
        synchronized(wakeLockLock) {
            if (mWakeLock != null) {
                MyLog.d(this, "Releasing wakelock");
                mWakeLock.release();
                mWakeLock = null;
            }
        }
    }

    private MyContext getMyContext() {
        return myContext;
    }

    private void initializeMyContext() {
        if (!myContext.isReady()) {
            myContext = myContextHolder.initialize(this).getBlocking();
        }
    }

    private class QueueExecutor extends MyAsyncTask<Void, Void, Boolean> implements CommandExecutorParent {
        private volatile CommandData currentlyExecuting = null;
        private static final long MAX_EXECUTION_TIME_SECONDS = 60;

        QueueExecutor() {
            super(PoolEnum.SYNC);
        }

        @Override
        protected Boolean doInBackground2(Void aVoid) {
            MyLog.d(this, "Started, " + myContext.queues().totalSizeToExecute() + " commands to process");
            String breakReason = "";
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
                synchronized (executorLock) {
                    if (mExecutor != this) {
                        breakReason = "Other executor";
                        break;
                    }
                }
                CommandData commandData = myContext.queues().pollQueue();
                currentlyExecuting = commandData;
                currentlyExecutingSince = System.currentTimeMillis();
                if (commandData == null) {
                    breakReason = "No more commands";
                    break;
                }
                ConnectionState connectionState = getMyContext().getConnectionState();
                if (commandData.getCommand().getConnectionRequired().isConnectionStateOk(connectionState)) {
                    MyServiceEventsBroadcaster.newInstance(getMyContext(), getServiceState())
                            .setCommandData(commandData)
                            .setEvent(MyServiceEvent.BEFORE_EXECUTING_COMMAND).broadcast();
                    if (commandData.getCommand() == DELETE_COMMAND) {
                        myContext.queues().deleteCommand(commandData);
                    } else if (commandData.getCommand() == CLEAR_COMMAND_QUEUE) {
                        myContext.queues().clear();
                        return true;
                    } else {
                        CommandExecutorStrategy.executeCommand(commandData, this);
                    }
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
            MyLog.d(this, "Ended, " + breakReason + ", " + myContext.queues().totalSizeToExecute() + " commands left");
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
            CommandQueue.addToPreQueue(CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                    commandDataExecuted.getTimeline().myAccountToSync, TimelineType.HOME)
                    .setInForeground(commandDataExecuted.isInForeground()));
        }
        
       @Override
        protected void onPostExecute2(Boolean notUsed) {
            onEndedExecution("onPostExecute");
        }

        @Override
        protected void onCancelled2(Boolean result) {
            onEndedExecution("onCancelled");
        }

        private void onEndedExecution(String method) {
            MyLog.v(this, method);
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
            return MyStringBuilder.formatKeyValue(this, sb);
        }

    }
    
    private class HeartBeat extends MyAsyncTask<Void, Long, Void> {
        private static final long HEARTBEAT_PERIOD_SECONDS = 11;
        private volatile long previousBeat = createdAt;
        private volatile long mIteration = 0;

        HeartBeat() {
            super(PoolEnum.SYNC);
        }

        @Override
        protected Void doInBackground2(Void aVoid) {
            MyLog.v(this, () -> "Started instance " + instanceId);
            String breakReason = "";
            for (long iteration = 1; iteration < 10000; iteration++) {
                synchronized(heartBeatLock) {
                    if (mHeartBeat != null && mHeartBeat != this && mHeartBeat.isReallyWorking() ) {
                        breakReason = "Other instance found: " + mHeartBeat;
                        break;
                    }
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
                synchronized(serviceStateLock) {
                    if (!mInitialized) {
                        breakReason = "Not initialized";
                        break;
                    }
                }
                publishProgress(iteration);
            }
            String breakReasonVal = breakReason;
            MyLog.v(this, () -> "Ended; " + this + " - " + breakReasonVal);
            synchronized(heartBeatLock) {
                if (mHeartBeat == this) {
                    mHeartBeat = null;
                }
            }
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
            return "HeartBeat " + mIteration + "; " + super.toString();
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
