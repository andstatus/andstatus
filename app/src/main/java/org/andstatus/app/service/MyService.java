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
import android.os.IBinder;
import android.os.PowerManager;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.MyAction;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.notification.CommandsQueueNotifier;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This service asynchronously executes commands, mostly related to communication
 * between this Android Device and Social networks.
 */
public class MyService extends Service {

    private MyContext myContext = MyContextHolder.get();
    private final Object serviceStateLock = new Object();
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
    private final CommandQueue queues = new CommandQueue(this);

    private static final long RETRY_QUEUE_PROCESSING_PERIOD_SECONDS = 900; 
    private final AtomicLong mRetryQueueProcessedAt = new AtomicLong();
    
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

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.d(this, "onStartCommand: startid=" + startId);
        receiveCommand(intent, startId);
        return START_NOT_STICKY;
    }

    @GuardedBy("serviceStateLock")
    private BroadcastReceiver intentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            MyLog.v(this, "onReceive " + intent.toString());
            receiveCommand(intent, 0);
        }
    };
    
    private void receiveCommand(Intent intent, int startId) {
        CommandData commandData = CommandData.fromIntent(myContext, intent);
        switch (commandData.getCommand()) {
            case STOP_SERVICE:
                MyLog.v(this, "Command " + commandData.getCommand() + " received");
                stopDelayed(false);
                break;
            case BROADCAST_SERVICE_STATE:
                MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
                        .broadcast();
                break;
            case UNKNOWN:
                MyLog.v(this, "Command " + commandData.getCommand() + " ignored");
                break;
            default:
                receiveOtherCommand(commandData);
                break;
        }
        synchronized (serviceStateLock) {
            if (startId > mLatestProcessedStartId) {
                mLatestProcessedStartId = startId;
            }
        }
    }

    private void receiveOtherCommand(CommandData commandData) {
        if (!isForcedToStop()) {
            initialize();
            addToMainQueue(commandData);
            startStopExecution();
        } else {
            addToTheQueueWhileStopping(commandData);
            stopDelayed(false);
        }
    }
    
    private boolean isForcedToStop() {
        synchronized (serviceStateLock) {
            return mForcedToStop;
        }
    }

    private void addToTheQueueWhileStopping(CommandData commandData) {
        CommandData commandData2 = null;
        synchronized (serviceStateLock) {
            if (mInitialized) {
                commandData2 = addToMainQueue(commandData);
            }
        }
        if (commandData2 != null) {
            MyLog.i(this, "Couldn't add command while stopping "
                    + commandData2);
        }
    }

    /** returns command back in a case of an error */
    private CommandData addToMainQueue(CommandData commandData) {
        switch (commandData.getCommand()) {
            case EMPTY:
            case UNKNOWN:
                return null;
            case DELETE_COMMAND:
                queues.deleteCommand(commandData);
                broadcastAfterExecutingCommand(commandData);
                return null;
            default:
                break;

        }
        commandData.getResult().prepareForLaunch();
        MyLog.v(this, "Adding to Main queue " + commandData);
        if (!queues.get(QueueType.CURRENT).offer(commandData)) {
            MyLog.e(this, "Couldn't add to the main queue, size=" + queues.get(QueueType.CURRENT).size());
            return commandData;
        }
        return null;
    }

    private void broadcastAfterExecutingCommand(CommandData commandData) {
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
        .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }
    
    void initialize() {
        boolean changed = false;
        boolean wasNotInitialized = false;
        synchronized (serviceStateLock) {
            if (!mInitialized) {
                wasNotInitialized = true;
                queues.load();
                registerReceiver(intentReceiver, new IntentFilter(MyAction.EXECUTE_COMMAND.getAction()));
                mInitialized = true;
                changed = true;
            }
        }
        if (wasNotInitialized) {
            if (widgetsInitialized.compareAndSet(false, true)) {
                AppWidgets.updateWidgets(myContext);
            }
            reviveHeartBeat();
        }
        if (changed) {
            MyServiceEventsBroadcaster.newInstance(myContext, getServiceState()).broadcast();
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
                if (!AsyncTaskLauncher.execute(this, false, mHeartBeat)) {
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
                MyLog.v(this, "Didn't change execution " + mExecutor);
                break;
        }
    }

    private TriState shouldStop() {
        boolean doStop = !myContext.isReady() || isForcedToStop()
                || !isAnythingToExecuteNow();
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
            logMsg.append("; "
                    + (queues.totalSizeToExecute() == 0 ? "queue is empty" : "queueSize=" + queues.totalSizeToExecute()));
        }
        MyLog.v(this, logMsg.toString());
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
            MyLog.i(this, "Couldn't startExecutor", e);
            couldStopExecutor(true);
            releaseWakeLock();
        }
    }
    
    private void ensureExecutorStarted() {
        final String method = "ensureExecutorStarted";
        StringBuilder logMessageBuilder = new StringBuilder();
        synchronized(executorLock) {
            if ( mExecutor != null && !mExecutor.needsBackgroundWork()) {
                logMessageBuilder.append(" Removing used Executor " + mExecutor);
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
                if (AsyncTaskLauncher.execute(this, false, newExecutor)) {
                    mExecutor = newExecutor;
                } else {
                    logMessageBuilder.append(" New executor was not added");
                }
            }
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(this, method + "; " + logMessageBuilder);
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
        return queues.isAnythingToExecuteNowIn(QueueType.CURRENT) || isAnythingToRetryNow()
                || isExecutorReallyWorkingNow();
    }
    
    private boolean isAnythingToRetryNow() {
        if (!RelativeTime.moreSecondsAgoThan(mRetryQueueProcessedAt.get(),
                        RETRY_QUEUE_PROCESSING_PERIOD_SECONDS)) {
            return false;
        }
        return queues.isAnythingToExecuteNowIn(QueueType.RETRY);
    }
    
    private boolean isExecutorReallyWorkingNow() {
        synchronized(executorLock) {
          return mExecutor != null && mExecutor.isReallyWorking();
        }        
    }
    
    @Override
    public void onDestroy() {
        boolean initialized = false;
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
        if (!setIsStopping(true, forceNow) && !forceNow) {
            return;
        }
        if (!couldStopExecutor(forceNow) && !forceNow) {
            return;
        }
        unInitialize();
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
                .setEvent(MyServiceEvent.ON_STOP).broadcast();
    }

    private void unInitialize() {
        int mainQueueSize = queues.get(QueueType.CURRENT).size();
        int retryQueueSize = queues.get(QueueType.RETRY).size();
        int latestProcessedStartId = 0;
        synchronized (serviceStateLock) {
            if( mInitialized) {
                try {
                    unregisterReceiver(intentReceiver);
                } catch (Exception e) {
                    MyLog.d(this, "On unregisterReceiver", e);
                }
                latestProcessedStartId = mLatestProcessedStartId;
                queues.save();
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
        AsyncTaskLauncher.shutdownExecutors(Collections.singleton(MyAsyncTask.PoolEnum.SYNC));
        releaseWakeLock();
        stopSelfResult(latestProcessedStartId);
        CommandsQueueNotifier.newInstance(myContext).update(
                mainQueueSize, retryQueueSize);
    }

    private boolean couldStopExecutor(boolean forceNow) {
        final String method = "couldStopExecutor";
        StringBuilder logMessageBuilder = new StringBuilder();
        boolean could = true;
        synchronized(executorLock) {
            if (mExecutor == null || !mExecutor.needsBackgroundWork()) {
                // Ok
            } else if ( mExecutor.isReallyWorking() ) {
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
            MyLog.v(this, method + "; " + logMessageBuilder);
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
    
    private class QueueExecutor extends MyAsyncTask<Void, Void, Boolean> implements CommandExecutorParent {
        private volatile CommandData currentlyExecuting = null;
        private static final long MAX_EXECUTION_TIME_SECONDS = 60;

        public QueueExecutor() {
            super(PoolEnum.SYNC);
        }

        @Override
        protected Boolean doInBackground2(Void... arg0) {
            MyLog.d(this, "Started, " + queues.get(QueueType.CURRENT).size() + " commands to process");
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
                CommandData commandData = pollQueue();
                currentlyExecuting = commandData;
                currentlyExecutingSince = System.currentTimeMillis();
                if (commandData == null) {
                    breakReason = "No more commands";
                    break;
                }
                ConnectionState connectionState = myContext.getConnectionState();
                if (commandData.getCommand().getConnectionRequired()
                        .isConnectionStateOk(connectionState)) {
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
                    queues.addToQueue(QueueType.RETRY, commandData);
                } else if (commandData.getResult().hasError()) {
                    queues.addToQueue(QueueType.ERROR, commandData);
                }
                broadcastAfterExecutingCommand(commandData);
                addSyncOfThisToQueue(commandData);
            } while (true);
            MyLog.d(this, "Ended, " + breakReason + ", " + queues.totalSizeToExecute() + " commands left");
            return true;
        }

        private CommandData pollQueue() {
            Queue<CommandData> tempQueue = new PriorityBlockingQueue<>(queues.get(QueueType.CURRENT).size()+1);
            CommandData commandData = null;
            do {
                commandData = queues.get(QueueType.CURRENT).poll();
                if (commandData == null && isAnythingToRetryNow()) {
                    moveCommandsFromRetryToMainQueue();
                    commandData = queues.get(QueueType.CURRENT).poll();
                }
                if (commandData == null) {
                    break;
                }
                commandData = findInRetryQueue(commandData);
                if (commandData != null) {
                    commandData = findInErrorQueue(commandData);
                }
                if (commandData != null && !commandData.isInForeground()
                        && myContext.isInForeground()
                        && !MyPreferences.isSyncWhileUsingApplicationEnabled()) {
                    tempQueue.add(commandData);
                    commandData = null;
                }
            } while (commandData == null);
            while (!tempQueue.isEmpty()) {
                CommandData cd = tempQueue.poll();
                if (!queues.get(QueueType.CURRENT).add(cd)) {
                    MyLog.e(this, "Couldn't return to main Queue, size=" + queues.get(QueueType.CURRENT).size()
                            + " command=" + cd);
                    break;
                }
            }
            MyLog.v(this, "Polled in "
                    + (myContext.isInForeground() ? "foreground"
                            + " "
                            + (MyPreferences.isSyncWhileUsingApplicationEnabled() ? "enabled"
                                    : "disabled")
                            : "background")
                    + " " + commandData);
            if (commandData != null) {
                commandData.setManuallyLaunched(false);
            }
            return commandData;
        }

        private static final long MIN_RETRY_PERIOD_SECONDS = 900; 
        private void moveCommandsFromRetryToMainQueue() {
            for (CommandData cd : queues.get(QueueType.RETRY)) {
                if (cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                    addToMainQueue(cd);
                    queues.get(QueueType.RETRY).remove(cd);
                    MyLog.v(this, "Moved from Retry to Main queue: " + cd);
                }
            }
            mRetryQueueProcessedAt.set(System.currentTimeMillis());
        }
        
        private CommandData findInRetryQueue(CommandData cdIn) {
            CommandData cdOut = cdIn;
            if (queues.get(QueueType.RETRY).contains(cdIn)) {
                for (CommandData cd : queues.get(QueueType.RETRY)) {
                    if (cd.equals(cdIn)) {
                        cd.resetRetries();
                        if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                            cdOut = cd;
                            queues.get(QueueType.RETRY).remove(cd);
                            MyLog.v(this, "Returned from Retry queue: " + cd);
                        } else {
                            cdOut = null;
                            MyLog.v(this, "Found in Retry queue: " + cd);
                        }
                        break;
                    }
                }
            }
            return cdOut;
        }
        
        private static final long MAX_DAYS_IN_ERROR_QUEUE = 10; 
        private CommandData findInErrorQueue(CommandData cdIn) {
            CommandData cdOut = cdIn;
            if (queues.get(QueueType.ERROR).contains(cdIn)) {
                for (CommandData cd : queues.get(QueueType.ERROR)) {
                    if (cd.equals(cdIn)) {
                        cd.resetRetries();
                        if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                            cdOut = cd;
                            queues.get(QueueType.ERROR).remove(cd);
                            MyLog.v(this, "Returned from Error queue: " + cd);
                        } else {
                            cdOut = null;
                            MyLog.v(this, "Found in Error queue: " + cd);
                        }
                    } else {
                        if (cd.executedMoreSecondsAgoThan(MAX_DAYS_IN_ERROR_QUEUE * RelativeTime.SECONDS_IN_A_DAY)) {
                            if (queues.get(QueueType.ERROR).remove(cd)) {
                                MyLog.i(this, "Removed old from Error queue: " + cd);
                            } else {
                                MyLog.i(this, "Failed to Remove old from Error queue: " + cd);
                            }
                        }
                    }
                }
            }
            return cdOut;
        }

        private void addSyncOfThisToQueue(CommandData commandDataExecuted) {
            if (commandDataExecuted.getResult().hasError()
                    || commandDataExecuted.getCommand() != CommandEnum.UPDATE_STATUS
                    || !SharedPreferencesUtil.getBoolean(
                            MyPreferences.KEY_SYNC_AFTER_MESSAGE_WAS_SENT, false)) {
                return;
            }
            addToMainQueue(CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE,
                    commandDataExecuted.getTimeline().getMyAccount(), TimelineType.HOME)
                    .setInForeground(commandDataExecuted.isInForeground()));
        }
        
       @Override
        protected void onPostExecute(Boolean notUsed) {
            onEndedExecution("onPostExecute");
        }

        @Override
        protected void onCancelled(Boolean result) {
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
            StringBuilder sb = new StringBuilder(64);
            if (currentlyExecuting != null && currentlyExecutingSince > 0) {
                sb.append("currentlyExecuting: " + currentlyExecuting + ", ");
                sb.append("since: " + RelativeTime.getDifference(getBaseContext(), currentlyExecutingSince) + ", ");
            }
            if (isStopping()) {
                sb.append("stopping, ");
            }
            sb.append(super.toString());
            return MyLog.formatKeyValue(this, sb.toString());
        }

    }
    
    private class HeartBeat extends MyAsyncTask<Void, Long, Void> {
        private static final long HEARTBEAT_PERIOD_SECONDS = 11;
        private volatile long previousBeat = createdAt;
        private volatile long mIteration = 0;

        public HeartBeat() {
            super(PoolEnum.SYNC);
        }

        @Override
        protected Void doInBackground2(Void... arg0) {
            MyLog.v(this, "Started instance " + instanceId);
            String breakReason = "";
            for (long iteration = 1; iteration < 10000; iteration++) {
                try {
                    synchronized(heartBeatLock) {
                        if (mHeartBeat != null && mHeartBeat != this && mHeartBeat.isReallyWorking() ) {
                            breakReason = "Other instance found: " + mHeartBeat;
                            break;
                        }
                        if (isCancelled()) {
                            breakReason = "Cancelled";
                            break;
                        }
                        heartBeatLock.wait(
                            java.util.concurrent.TimeUnit.SECONDS.toMillis(HEARTBEAT_PERIOD_SECONDS));
                    }
                } catch (InterruptedException e) {
                    breakReason = "InterruptedException";
                    Thread.currentThread().interrupt();
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
            MyLog.v(this, "Ended; " + this + " - " + breakReason);
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
                MyLog.v(this, "onProgressUpdate; " + this);
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
            if ( !needsBackgroundWork()
                    || RelativeTime.wasButMoreSecondsAgoThan(previousBeat, HEARTBEAT_PERIOD_SECONDS + 3)) {
                return false;
            }
            return true;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
