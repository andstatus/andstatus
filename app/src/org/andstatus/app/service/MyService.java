/* 
 * Copyright (c) 2011-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.notification.CommandsQueueNotifier;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TriState;
import org.andstatus.app.appwidget.AppWidgets;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.IBinder;
import android.os.PowerManager;

import net.jcip.annotations.GuardedBy;
import java.util.concurrent.RejectedExecutionException;

/**
 * This is an application service that serves as a connection between this Android Device
 * and Microblogging system. Other applications can interact with it via IPC.
 */
public class MyService extends Service {
    private static final String TAG = MyService.class.getSimpleName();
    
    public static final long MAX_COMMAND_EXECUTION_SECONDS = 10 * 60;
    
    /**
     * Broadcast with this action is being sent by {@link MyService} to notify of its state.
     *  Actually {@link MyServiceManager} receives it.
     */
    public static final String ACTION_SERVICE_STATE = IntentExtra.MY_ACTION_PREFIX + "SERVICE_STATE";

    /**
     * This action is used in any intent sent to this service. Actual command to
     * perform by this service is in the {@link #EXTRA_MSGTYPE} extra of the
     * intent
     * 
     * @see CommandEnum
     */
    public static final String ACTION_GO = IntentExtra.MY_ACTION_PREFIX + "GO";

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
    /**
     * Flag to control the Service state persistence
     */
    @GuardedBy("serviceStateLock")
    private boolean mInitialized = false;

    private volatile int mLatestProcessedStartId = 0;
    
    private final Object executorLock = new Object();
    @GuardedBy("executorLock")
    private QueueExecutor mExecutor = null;
    @GuardedBy("executorLock")
    private long mExecutorStartedAt = 0;
    @GuardedBy("executorLock")
    private long mExecutorEndedAt = 0;
    
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

    private final Queue<CommandData> mMainCommandQueue = new PriorityBlockingQueue<CommandData>(100);
    private final Queue<CommandData> mRetryCommandQueue = new PriorityBlockingQueue<CommandData>(100);
    private final Queue<CommandData> mErrorCommandQueue = new LinkedBlockingQueue<CommandData>(200);

    private static final long RETRY_QUEUE_PROCESSING_PERIOD_SECONDS = 900; 
    private final AtomicLong mRetryQueueProcessedAt = new AtomicLong();
    
    private static volatile boolean widgetsInitialized = false;

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
        CommandData commandData = CommandData.fromIntent(intent);
        switch (commandData.getCommand()) {
            case STOP_SERVICE:
                MyLog.v(this, "Command " + commandData.getCommand() + " received");
                stopDelayed(false);
                break;
            case BROADCAST_SERVICE_STATE:
                MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState())
                        .broadcast();
                break;
            case UNKNOWN:
                MyLog.v(this, "Command " + commandData.getCommand() + " ignored");
                break;
            default:
                receiveOtherCommand(commandData);
                break;
        }
        mLatestProcessedStartId = startId;
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
            MyLog.i(this,"Couldn't add command while stopping "
            + commandData2);
        }
    }

    /** returns command back in a case of an error */
    private CommandData addToMainQueue(CommandData commandData) {
        switch (commandData.getCommand()) {
            case EMPTY:
                return null;
            case DROP_QUEUES:
                clearQueues();
                broadcastAfterExecutingCommand(commandData);
                return null;
            case DELETE_COMMAND:
                deleteCommand(commandData);
                broadcastAfterExecutingCommand(commandData);
                return null;
            default:
                break;

        }
        commandData.getResult().prepareForLaunch();
        MyLog.v(this, "Adding to Main queue " + commandData);
        if (!mMainCommandQueue.offer(commandData)) {
            MyLog.e(this, "Couldn't add to the main queue, size=" + mMainCommandQueue.size());
            return commandData;
        }
        return null;
    }

    private void clearQueues() {
        mMainCommandQueue.clear();
        mRetryCommandQueue.clear();
        mErrorCommandQueue.clear();
        MyLog.v(this,"Queues cleared");
    }
    
    public void deleteCommand(CommandData commandData) {
        commandData.deleteCommandInTheQueue(mMainCommandQueue);
        commandData.deleteCommandInTheQueue(mRetryCommandQueue);
        commandData.deleteCommandInTheQueue(mErrorCommandQueue);
    }

    private void broadcastAfterExecutingCommand(CommandData commandData) {
        MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState())
        .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }
    
    void initialize() {
        boolean changed = false;
        boolean wasNotInitialized = false;
        synchronized (serviceStateLock) {
            if (!mInitialized) {
                wasNotInitialized = true;
                restoreState();
                registerReceiver(intentReceiver, new IntentFilter(ACTION_GO));
                mInitialized = true;
                changed = true;
            }
        }
        if (wasNotInitialized) {
            if (!widgetsInitialized) {
                AppWidgets.updateWidgets(MyContextHolder.get());
                widgetsInitialized = true;
            }
            reviveHeartBeat();
        }
        if (changed) {
            MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState()).broadcast();
        }
    }
    private void reviveHeartBeat() {
        synchronized(heartBeatLock) {
            if (mHeartBeat != null && !mHeartBeat.isReallyWorking()) {
                mHeartBeat.cancel(true);
                mHeartBeat = null;
            }
            if (mHeartBeat == null) {
                mHeartBeat = new HeartBeat();
                try {
                    mHeartBeat.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } catch (RejectedExecutionException e) {
                    MyLog.w(this, "Launching new HeartBeat", e);
                    mHeartBeat = null;
                }
            }
        }
    }

    private void restoreState() {
        int count = 0;
        count += CommandData.loadQueue(this, mMainCommandQueue, QueueType.CURRENT);
        count += CommandData.loadQueue(this, mRetryCommandQueue, QueueType.RETRY);
        int countError = CommandData.loadQueue(this, mErrorCommandQueue, QueueType.ERROR);
        MyLog.d(this, "State restored, " + (count > 0 ? Integer.toString(count) : "no ")
                + " msg in the Queues, "
                + (countError > 0 ? Integer.toString(countError) + " in Error queue" : "")
                );
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
        boolean doStop = !MyContextHolder.get().isReady() || isForcedToStop()
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
            logMsg.append("; startId=" + mLatestProcessedStartId);
        }
        if (success && doStop) {
            logMsg.append("; "
                    + (totalQueuesSize() == 0 ? "queue is empty" : "queueSize=" + totalQueuesSize()));
        }
        MyLog.v(this, logMsg.toString());
        return success;
    }

    private void startExecution() {
        acquireWakeLock();
        startExecutor();
    }
    
    private void startExecutor() {
        final String method = "startExecutor";
        StringBuilder logMessageBuilder = new StringBuilder();
        synchronized(executorLock) {
            if ( mExecutor != null && (mExecutor.getStatus() != Status.RUNNING)) {
                removeExecutor(logMessageBuilder);
            }
            if ( mExecutor != null && !isExecutorReallyWorkingNow()) {
                logMessageBuilder.append(" Killing stalled Executor " + mExecutor);
                removeExecutor(logMessageBuilder);
            }
            if (mExecutor != null) {
                logMessageBuilder.append(" There is an Executor already " + mExecutor);
            } else {
                // For now let's have only ONE working thread 
                // (it seems there is some problem in parallel execution...)
                mExecutor = new QueueExecutor();
                logMessageBuilder.append(" Adding and starting new Executor " + mExecutor);
                mExecutorStartedAt = System.currentTimeMillis();
                mExecutorEndedAt = 0;
                mExecutor.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
            logMessageBuilder.append(" Removing Executor " + mExecutor);
            mExecutor = null;
            mExecutorStartedAt = 0;
            mExecutorEndedAt = 0;
        }
    }

    private void acquireWakeLock() {
        synchronized(wakeLockLock) {
            if (mWakeLock == null) {
                MyLog.d(this, "Acquiring wakelock");
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                mWakeLock.acquire();
            }
        }
    }

    private boolean isAnythingToExecuteNow() {
        return isAnythingToExecuteInMainQueueNow() || isAnythingToRetryNow()
                || isExecutorReallyWorkingNow();
    }
    
    private boolean isAnythingToExecuteInMainQueueNow() {
        if (mMainCommandQueue.isEmpty()) {
            return false;
        }
        if (!MyPreferences.isSyncWhileUsingApplicationEnabled()
                && MyContextHolder.get().isInForeground()) {
            return hasQueueForegroundTasks(mMainCommandQueue);
        }
        return true;
    }
    
    private boolean isAnythingToRetryNow() {
        if (mRetryCommandQueue.isEmpty()
                || !RelativeTime.moreSecondsAgoThan(mRetryQueueProcessedAt.get(),
                        RETRY_QUEUE_PROCESSING_PERIOD_SECONDS)) {
            return false;
        }
        if (!MyPreferences.isSyncWhileUsingApplicationEnabled()
                && MyContextHolder.get().isInForeground()) {
            return hasQueueForegroundTasks(mRetryCommandQueue);
        }
        return true;
    }
    
    private boolean isExecutorReallyWorkingNow() {
        synchronized(executorLock) {
          return mExecutor != null && mExecutorStartedAt != 0 && mExecutor.isReallyWorking();
        }        
    }
    
    private boolean hasQueueForegroundTasks(Queue<CommandData> queue) {
        boolean has = false;
        for (CommandData commandData : queue) {
            if (commandData.isInForeground()) {
                has = true;
                break;
            }
        }
        return has;
    }
    
    private int totalQueuesSize() {
        return mRetryCommandQueue.size() + mMainCommandQueue.size();
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
     * @param boolean forceNow 
     */
    private void stopDelayed(boolean forceNow) {
        if (!setIsStopping(true, forceNow) && !forceNow) {
            return;
        }
        if (!couldStopExecutor(forceNow) && !forceNow) {
            return;
        }
        unInitialize();
        MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState())
                .setEvent(MyServiceEvent.ON_STOP).broadcast();
    }

    private void unInitialize() {
        int mainQueueSize = mMainCommandQueue.size();
        int retryQueueSize = mRetryCommandQueue.size();
        synchronized (serviceStateLock) {
            if( mInitialized) {
                try {
                    unregisterReceiver(intentReceiver);
                } catch (Exception e) {
                    MyLog.d(this, "On unregisterReceiver", e);
                }
                saveState();
                mInitialized = false;
                mIsStopping = false;
                mForcedToStop = false;
                decidedToChangeIsStoppingAt = 0;
            }
        }
        synchronized(heartBeatLock) {
            if (mHeartBeat != null) {
                mHeartBeat.cancel(true);
            }
            mHeartBeat = null;
        }
        relealeWakeLock();
        stopSelfResult(mLatestProcessedStartId);
        mLatestProcessedStartId = 0;
        CommandsQueueNotifier.newInstance(MyContextHolder.get()).update(
                mainQueueSize, retryQueueSize);
    }

    private boolean couldStopExecutor(boolean forceNow) {
        final String method = "couldStopExecutor";
        StringBuilder logMessageBuilder = new StringBuilder();
        boolean could = true;
        synchronized(executorLock) {
            if (mExecutor == null || mExecutorStartedAt == 0) {
                // Ok
            } else if ( mExecutor.isReallyWorking() ) {
                if (forceNow) {
                    logMessageBuilder.append(" Cancelling Executor " + mExecutor);
                    mExecutor.cancel(true);
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

    private void saveState() {
        int count = 0;
        count += CommandData.saveQueue(this, mMainCommandQueue, QueueType.CURRENT);
        count += CommandData.saveQueue(this, mRetryCommandQueue, QueueType.RETRY);
        int countError = CommandData.saveQueue(this, mErrorCommandQueue, QueueType.ERROR);
        MyLog.d(this, "State saved, " + (count > 0 ? Integer.toString(count) : "no ")
                + " msg in the Queues, "
                + (countError > 0 ? Integer.toString(countError) + " in Error queue" : "")
                );
    }
    
    private void relealeWakeLock() {
        synchronized(wakeLockLock) {
            if (mWakeLock != null) {
                MyLog.d(this, "Releasing wakelock");
                mWakeLock.release();
                mWakeLock = null;
            }
        }
    }
    
    /**
     * @return This intent will be received by MyService only if it's initialized
     * (and corresponding broadcast receiver registered)
     */
    public static Intent intentForThisInitialized() {
        return new Intent(MyService.ACTION_GO);
    }
    
    private class QueueExecutor extends AsyncTask<Void, Void, Boolean> implements CommandExecutorParent {
        private volatile CommandData currentlyExecuting = null;
        private volatile long currentlyExecutingSince = 0;
        private static final long DELAY_AFTER_EXECUTOR_ENDED_SECONDS = 1;
        
        @Override
        protected Boolean doInBackground(Void... arg0) {
            MyLog.d(this, "Started, " + mMainCommandQueue.size() + " commands to process");
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
                if (MyContextHolder.get().isOnline(commandData.getCommand().getConnetionRequired())) {
                    MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState())
                        .setCommandData(commandData).setEvent(MyServiceEvent.BEFORE_EXECUTING_COMMAND).broadcast();
                    CommandExecutorStrategy.executeCommand(commandData, this);
                } else {
                    commandData.getResult().incrementNumIoExceptions();
                    commandData.getResult().setMessage("No '" + commandData.getCommand().getConnetionRequired() + "' connection");
                }
                if (commandData.getResult().shouldWeRetry()) {
                    addToRetryQueue(commandData);        
                } else if (commandData.getResult().hasError()) {
                    addToErrorQueue(commandData);
                }
                broadcastAfterExecutingCommand(commandData);
                addSyncOfThisToQueue(commandData);
            } while (true);
            MyLog.d(this, "Ended, " + breakReason + ", " + totalQueuesSize() + " commands left");
            return true;
        }

        private CommandData pollQueue() {
            Queue<CommandData> tempQueue = new PriorityBlockingQueue<CommandData>(mMainCommandQueue.size()+1);
            CommandData commandData = null;
            do {
                commandData = mMainCommandQueue.poll();
                if (commandData == null && isAnythingToRetryNow()) {
                    moveCommandsFromRetryToMainQueue();
                    commandData = mMainCommandQueue.poll();
                }
                if (commandData == null) {
                    break;
                }
                commandData = findInRetryQueue(commandData);
                if (commandData != null) {
                    commandData = findInErrorQueue(commandData);
                }
                if (commandData != null && !commandData.isInForeground()
                        && MyContextHolder.get().isInForeground()
                        && !MyPreferences.isSyncWhileUsingApplicationEnabled()) {
                    tempQueue.add(commandData);
                    commandData = null;
                }
            } while (commandData == null);
            while (!tempQueue.isEmpty()) {
                CommandData cd = tempQueue.poll();
                if (!mMainCommandQueue.add(cd)) {
                    MyLog.e(this, "Couldn't return to main Queue, size=" + mMainCommandQueue.size()
                            + " command=" + cd);
                    break;
                }
            }
            MyLog.v(this, "Polled in "
                    + (MyContextHolder.get().isInForeground() ? "foreground"
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
            for (CommandData cd : mRetryCommandQueue) {
                if (cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                    addToMainQueue(cd);
                    mRetryCommandQueue.remove(cd);
                    MyLog.v(this, "Moved from Retry to Main queue: " + cd);
                }
            }
            mRetryQueueProcessedAt.set(System.currentTimeMillis());
        }
        
        private CommandData findInRetryQueue(CommandData cdIn) {
            CommandData cdOut = cdIn;
            if (mRetryCommandQueue.contains(cdIn)) {
                for (CommandData cd : mRetryCommandQueue) {
                    if (cd.equals(cdIn)) {
                        cd.resetRetries();
                        if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                            cdOut = cd;
                            mRetryCommandQueue.remove(cd);
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
            if (mErrorCommandQueue.contains(cdIn)) {
                for (CommandData cd : mErrorCommandQueue) {
                    if (cd.equals(cdIn)) {
                        cd.resetRetries();
                        if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                            cdOut = cd;
                            mErrorCommandQueue.remove(cd);
                            MyLog.v(this, "Returned from Error queue: " + cd);
                        } else {
                            cdOut = null;
                            MyLog.v(this, "Found in Error queue: " + cd);
                        }
                    } else {
                        if (cd.executedMoreSecondsAgoThan(MAX_DAYS_IN_ERROR_QUEUE * RelativeTime.SECONDS_IN_A_DAY)) {
                            if (mErrorCommandQueue.remove(cd)) {
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
        
        private void addToRetryQueue(CommandData commandData) {
            if (!mRetryCommandQueue.contains(commandData) 
                    && !mRetryCommandQueue.offer(commandData)) {
                MyLog.e(this, "mRetryQueue is full?");
            }
        }

        private void addToErrorQueue(CommandData commandData) {
            if (!mErrorCommandQueue.contains(commandData)
                    && !mErrorCommandQueue.offer(commandData)) {
                CommandData commandData2 = mErrorCommandQueue.poll();
                MyLog.d(this, "Removed from overloaded Error queue: " + commandData2);
                if (!mErrorCommandQueue.offer(commandData)) {
                    MyLog.e(this, "Error Queue is full?");
                }
            }
        }

        private void addSyncOfThisToQueue(CommandData commandDataExecuted) {
            if (commandDataExecuted.getResult().hasError()
                    || commandDataExecuted.getCommand() != CommandEnum.UPDATE_STATUS
                    || !MyPreferences.getBoolean(
                            MyPreferences.KEY_SYNC_AFTER_MESSAGE_WAS_SENT, false)) {
                return;
            }
            addToMainQueue(new CommandData(CommandEnum.FETCH_TIMELINE,
                    commandDataExecuted.getAccountName(), TimelineTypeEnum.HOME)
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
            synchronized(executorLock) {
                mExecutorEndedAt = System.currentTimeMillis();
            }
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
            long executorStartedAt2 = 0;
            long executorEndedAt2 = 0;
            synchronized(executorLock) {
                executorStartedAt2 = mExecutorStartedAt;
                executorEndedAt2 = mExecutorEndedAt;
            }
            if (executorStartedAt2 > 0) {
                sb.append("started:" + RelativeTime.getDifference(getBaseContext(), executorStartedAt2) + ",");
            } else {
                sb.append("not started,");
            }
            if (currentlyExecuting != null && currentlyExecutingSince > 0) {
                sb.append("currentlyExecuting:" + currentlyExecuting + ",");
                sb.append("since:" + RelativeTime.getDifference(getBaseContext(), currentlyExecutingSince) + ",");
            }
            if (executorEndedAt2 != 0) {
                sb.append("ended:" + RelativeTime.getDifference(getBaseContext(), executorEndedAt2) + ",");
            }
            sb.append("status:" + getStatus() + ",");
            if (isCancelled()) {
                sb.append("cancelled,");
            }
            if (isStopping()) {
                sb.append("stopping,");
            }
            return MyLog.formatKeyValue(this, sb.toString());
        }
        
        boolean isReallyWorking() {
            synchronized (executorLock) {
                if (mExecutorEndedAt > 0) {
                    return !RelativeTime.moreSecondsAgoThan(mExecutorEndedAt,
                            DELAY_AFTER_EXECUTOR_ENDED_SECONDS);
                }
            }
            if (getStatus() != Status.RUNNING
                    || currentlyExecuting == null
                    || RelativeTime.moreSecondsAgoThan(currentlyExecutingSince,
                            MAX_COMMAND_EXECUTION_SECONDS)) {
                return false;
            }
            return true;
        }
    }
    
    private class HeartBeat extends AsyncTask<Void, Long, Void> {
        private static final long HEARTBEAT_PERIOD_SECONDS = 11;
        private final long mInstanceId = InstanceId.next();
        private final long workingSince = MyLog.uniqueCurrentTimeMS();
        private volatile long previousBeat = workingSince;
        private volatile long mIteration = 0;

        @Override
        protected Void doInBackground(Void... arg0) {
            MyLog.v(this, "Started instance " + mInstanceId);
            String breakReason = "";
            for (long iteration = 1; iteration < 10000; iteration++) {
                try {
                    synchronized(heartBeatLock) {
                        if (mHeartBeat != this && mHeartBeat.isReallyWorking() ) {
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
            MyLog.v(this, "onProgressUpdate; " + this);
            startStopExecution();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("HeartBeat [id=");
            builder.append(mInstanceId);
            builder.append(", since=");
            builder.append(RelativeTime.secondsAgo(workingSince));
            builder.append("sec, iteration=");
            builder.append(mIteration);
            builder.append("]");
            return builder.toString();
        }
        
        public boolean isReallyWorking() {
            if (getStatus() != Status.RUNNING
                    || RelativeTime.moreSecondsAgoThan(previousBeat,
                            HEARTBEAT_PERIOD_SECONDS + 3)) {
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
