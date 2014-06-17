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

import org.andstatus.app.IntentExtra;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.support.android.v11.os.AsyncTask;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TriState;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask.Status;
import android.os.IBinder;
import android.os.PowerManager;

import net.jcip.annotations.GuardedBy;

/**
 * This is an application service that serves as a connection between this Android Device
 * and Microblogging system. Other applications can interact with it via IPC.
 */
public class MyService extends Service {
    private static final String TAG = MyService.class.getSimpleName();
    private static final String COMMANDS_QUEUE_FILENAME = TAG + "-commands-queue";
    private static final String RETRY_QUEUE_FILENAME = TAG + "-retry-queue";
    private static final String ERROR_QUEUE_FILENAME = TAG + "-error-queue";
    
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

    private final Object serviceStateLock = new Object();
    private boolean isStopping() {
        synchronized (serviceStateLock) {
            return mIsStopping;
        }
    }
    @GuardedBy("serviceStateLock")
    private boolean dontStop = false;
    /**
     * We are going to finish this service. The flag is being checked by background threads
     */
    @GuardedBy("serviceStateLock")
    private boolean mIsStopping = false;
    private static long ISSTOPPING_CHANGE_MIN_PERIOD_SECONDS = 20;
    @GuardedBy("serviceStateLock")
    private long decidedToChangeIsStoppingAt = 0;
    /**
     * Flag to control the Service state persistence
     */
    @GuardedBy("serviceStateLock")
    private boolean mInitialized = false;

    private int lastProcessedStartId = 0;
    
    private final Object executorLock = new Object();
    @GuardedBy("executorLock")
    private QueueExecutor executor = null;
    @GuardedBy("executorLock")
    private long executorStartedAt = 0;
    @GuardedBy("executorLock")
    private long executorEndedAt = 0;
	
    private final Object heartBeatLock = new Object();
	@GuardedBy("heartBeatLock")
	private HeartBeat heartBeat = null;

    private final Object wakeLockLock = new Object();
    /**
     * The reference to the wake lock used to keep the CPU from stopping during
     * background operations.
     */
    @GuardedBy("wakeLockLock")
    private PowerManager.WakeLock wakeLock = null;

    final Queue<CommandData> mainCommandQueue = new PriorityBlockingQueue<CommandData>(100);
    final Queue<CommandData> retryCommandQueue = new PriorityBlockingQueue<CommandData>(100);
    final Queue<CommandData> errorCommandQueue = new LinkedBlockingQueue<CommandData>(200);
    
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
                receiveOtherCommand(commandData, startId);
                break;
        }
    }

    private void receiveOtherCommand(CommandData commandData, int startId) {
        if (setDontStop(startId)) {
            try {
                initialize();
                if (mainCommandQueue.isEmpty()) {
                    moveCommandsFromRetryToMainQueue();
                }
                addToMainQueue(commandData);
            } finally {
                synchronized(serviceStateLock) {
                    dontStop = false;
                }
            }
            startStopExecution();
        } else {
            addToTheQueueWhileStopping(commandData);
        }
    }

    private boolean setDontStop(int startId) {
        boolean ok = false;
        synchronized(serviceStateLock) {
            if (!isStopping()) {
                ok = true;
                dontStop = true;
                if (startId != 0) {
                    lastProcessedStartId = startId;
                }
            }
        }
        return ok;
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
                return null;
            default:
                break;

        }
        MyLog.v(this, "Adding to Main queue " + commandData);
        if (!mainCommandQueue.offer(commandData)) {
            MyLog.e(this, "Couldn't add to the main queue, size=" + mainCommandQueue.size());
            return commandData;
        }
        return null;
    }

    private void clearQueues() {
        mainCommandQueue.clear();
        retryCommandQueue.clear();
        errorCommandQueue.clear();
    }

    private final static long MIN_RETRY_PERIOD_MS = 180000; 
    private void moveCommandsFromRetryToMainQueue() {
        Queue<CommandData> tempQueue = new PriorityBlockingQueue<CommandData>(retryCommandQueue.size()+1);
        while (!retryCommandQueue.isEmpty()) {
            CommandData commandData = retryCommandQueue.poll();
            if (System.currentTimeMillis() - commandData.getResult().getLastExecutedDate() > MIN_RETRY_PERIOD_MS) {
                commandData = addToMainQueue(commandData);
            }
            if (commandData != null) {
                if (!tempQueue.add(commandData)) {
                    MyLog.e(this, "Couldn't add to temp Queue, size=" + tempQueue.size()
                            + " command=" + commandData);
                    break;
                }
            }
        }
        while (!tempQueue.isEmpty()) {
            CommandData cd = tempQueue.poll();
            if (!retryCommandQueue.add(cd)) {
                MyLog.e(this, "Couldn't return to retry Queue, size=" + retryCommandQueue.size()
                        + " command=" + cd);
                break;
            }
        }
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
            synchronized(heartBeatLock) {
                if (heartBeat != null && heartBeat.getStatus() == Status.RUNNING) {
                    heartBeat.cancel(true);
                }
                heartBeat = new HeartBeat();
                heartBeat.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
		if (changed) {
            MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState()).broadcast();
		}
    }

    private void restoreState() {
        int count = 0;
        count += CommandData.loadQueue(this, mainCommandQueue, COMMANDS_QUEUE_FILENAME);
        count += CommandData.loadQueue(this, retryCommandQueue, RETRY_QUEUE_FILENAME);
        int countError = CommandData.loadQueue(this, errorCommandQueue, ERROR_QUEUE_FILENAME);
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
			    MyLog.v(this, "Didn't change execution " + executor);
                break;
        }
    }

    private TriState shouldStop() {
        boolean doStop = !MyContextHolder.get().isReady() || mainCommandQueue.isEmpty();
        if (!couldSetIsStopping(doStop, false)) {
            return TriState.UNKNOWN;
        }
        if (doStop) {
            return TriState.TRUE;
        } else {
            return TriState.FALSE;
        }
    }

    private boolean couldSetIsStopping(boolean doStop, boolean forceStopNow) {
        boolean forced = false;
        synchronized (serviceStateLock) {
            if (doStop == mIsStopping) {
                MyLog.v(this, "Not changed: doStop=" + doStop);
                return true;
            }
            if (!mInitialized && !doStop) {
                MyLog.v(this, "!mInitialized && !doStop");
                return false;
            }
            if (doStop && dontStop) {
                MyLog.v(this, "dontStop flag");
                return false;
            }
            long decidedToChangeSeconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - decidedToChangeIsStoppingAt);
            if (decidedToChangeSeconds < ISSTOPPING_CHANGE_MIN_PERIOD_SECONDS) {
                if (doStop && forceStopNow) {
                    forced = true;
                } else {
                    MyLog.v(this, "Decided to change " + decidedToChangeSeconds + " second ago, doStop=" + doStop);
                    return false;
                }
            }
            decidedToChangeIsStoppingAt = System.currentTimeMillis();
            mIsStopping = doStop;
        }
        if (doStop) {
            if (forced) {
                MyLog.v(this, "Forced to stop; startId=" + lastProcessedStartId 
                        + "; " + (totalQueuesSize() == 0 ? "queue is empty"  : "queueSize=" + totalQueuesSize())
                        );
            } else {
                MyLog.v(this, "Stopping; startId=" + lastProcessedStartId 
                        + "; " + (totalQueuesSize() == 0 ? "queue is empty"  : "queueSize=" + totalQueuesSize())
                        );
            }
        } else {
            MyLog.v(this, "Reverted to Starting; startId=" + lastProcessedStartId);
        }
        return true;
    }

    private void startExecution() {
        acquireWakeLock();
        startExecutor();
    }
    
    private void startExecutor() {
        final String method = "startExecutor";
        StringBuilder logMessageBuilder = new StringBuilder();
        synchronized(executorLock) {
            if ( executor != null && (executor.getStatus() != Status.RUNNING)) {
                logMessageBuilder.append(" Deleting Executor " + executor);
                executor = null;
            }
            if (executor != null) {
                logMessageBuilder.append(" There is an Executor already " + executor);
            } else {
                // For now let's have only ONE working thread 
                // (it seems there is some problem in parallel execution...)
                executor = new QueueExecutor();
                logMessageBuilder.append(" Adding and starting new Executor " + executor);
                executorStartedAt = System.currentTimeMillis();
                executorEndedAt = 0;
                executor.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(this, method + "; " + logMessageBuilder);
        }
    }

    private void acquireWakeLock() {
        synchronized(wakeLockLock) {
            if (wakeLock == null) {
                MyLog.d(this, "Acquiring wakelock");
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                wakeLock.acquire();
            }
        }
    }
    
    private int totalQueuesSize() {
        return retryCommandQueue.size() + mainCommandQueue.size();
    }
    
    @Override
    public void onDestroy() {
        MyLog.v(this, "onDestroy");
        stopDelayed(true);
        MyLog.d(this, "Service destroyed");
    }
    
    /**
     * Notify background processes that the service is stopping.
     * Stop if background processes has finished.
     * Persist everything that we'll need on next Service creation and free resources
     * @param boolean forceNow 
     */
    private void stopDelayed(boolean forceNow) {
        if (!couldSetIsStopping(true, forceNow) && !forceNow) {
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
        int mainQueueSize = mainCommandQueue.size();
        int retryQueueSize = retryCommandQueue.size();
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
                dontStop = false;
            }
        }
		synchronized(heartBeatLock) {
		    if (heartBeat != null) {
		        heartBeat.cancel(true);
		    }
			heartBeat = null;
		}
        relealeWakeLock();
        stopSelfResult(lastProcessedStartId);
        lastProcessedStartId = 0;
        CommandsQueueNotifier.newInstance(MyContextHolder.get()).update(
                mainQueueSize, retryQueueSize);
    }

    private boolean couldStopExecutor(boolean forceNow) {
        final String method = "couldStopExecutor";
        StringBuilder logMessageBuilder = new StringBuilder();
        boolean could = true;
        synchronized(executorLock) {
            if (executor == null || executorStartedAt == 0) {
                // Ok
            } else if ( executor.isReallyWorking() ) {
                if (forceNow) {
                    logMessageBuilder.append(" Cancelling Executor " + executor);
                    executor.cancel(true);
                } else {
                    logMessageBuilder.append(" Cannot stop now Executor " + executor);
                    could = false;
                }
            }
            if (could) {
                if (executor != null) {
                    logMessageBuilder.append(" Removing Executor " + executor);
                }
                executor = null;
                executorStartedAt = 0;
                executorEndedAt = 0;
            }
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(this, method + "; " + logMessageBuilder);
        }
        return could;
    }

    private void saveState() {
        int count = 0;
        count += CommandData.saveQueue(this, mainCommandQueue, COMMANDS_QUEUE_FILENAME);
        count += CommandData.saveQueue(this, retryCommandQueue, RETRY_QUEUE_FILENAME);
        int countError = CommandData.saveQueue(this, errorCommandQueue, ERROR_QUEUE_FILENAME);
        MyLog.d(this, "State saved, " + (count > 0 ? Integer.toString(count) : "no ")
                + " msg in the Queues, "
                + (countError > 0 ? Integer.toString(countError) + " in Error queue" : "")
                );
    }
    
    private void relealeWakeLock() {
        synchronized(wakeLockLock) {
            if (wakeLock != null) {
                MyLog.d(this, "Releasing wakelock");
                wakeLock.release();
                wakeLock = null;
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
        private static final long DELAY_AFTER_EXECUTOR_ENDED_SECONDS = 10;
		private static final long MAX_COMMAND_EXECUTION_MS = 10 * 60 * 1000;
        
        @Override
        protected Boolean doInBackground(Void... arg0) {
            MyLog.d(this, "Started, " + mainCommandQueue.size() + " commands to process");
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
					if (executor != this) {
	                    breakReason = "Other executor";
						break;
					}
				}
                CommandData commandData = null;
                do {
                    commandData = mainCommandQueue.poll();
                    if (commandData == null) {
                        break;
                    }
                    commandData = checkInRetryQueue(commandData);
                    if (commandData != null) {
                        commandData = checkInErrorQueue(commandData);
                    }
                } while (commandData == null);
                currentlyExecuting = commandData;
                currentlyExecutingSince = System.currentTimeMillis();
                if (commandData == null) {
                    breakReason = "No more commands";
                    break;
                }
                
                MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState())
                .setCommandData(commandData).setEvent(MyServiceEvent.BEFORE_EXECUTING_COMMAND).broadcast();
                if ( !commandData.getCommand().isOnlineOnly() || isOnline()) {
                    CommandExecutorStrategy.executeCommand(commandData, this);
                }
                if (commandData.getResult().shouldWeRetry()) {
                    addToRetryQueue(commandData);        
                } else if (commandData.getResult().hasError()) {
                    addToErrorQueue(commandData);
                }
                MyServiceBroadcaster.newInstance(MyContextHolder.get(), getServiceState())
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
            } while (true);
            MyLog.d(this, "Ended, " + breakReason + ", " + totalQueuesSize() + " commands left");
            return true;
        }

        private CommandData checkInRetryQueue(CommandData commandData) {
            CommandData commandData2 = commandData;
            if (retryCommandQueue.contains(commandData)) {
                for (CommandData cd : retryCommandQueue) {
                    if (cd.equals(commandData)) {
                        cd.getResult().resetRetries(commandData.getCommand());
                        if (System.currentTimeMillis() - cd.getResult().getLastExecutedDate() > MIN_RETRY_PERIOD_MS) {
                            commandData2 = cd;
                            retryCommandQueue.remove(cd);
                        } else {
                            commandData2 = null;
                            MyLog.v(this, "Found in Retry queue: " + cd);
                        }
                    } else {
                        if (System.currentTimeMillis() - cd.getResult().getLastExecutedDate() > MAX_MS_IN_ERROR_QUEUE) {
                            if (retryCommandQueue.remove(cd)) {
                                MyLog.i(this, "Removed old from Retry queue: " + cd);
                            } else {
                                MyLog.i(this, "Failed to Remove old from Retry queue: " + cd);
                            }
                        }
                    }
                }
            }
            return commandData2;
        }
        
        private final static long MAX_MS_IN_ERROR_QUEUE = 3 * 24 * 60 * 60 * 1000; 
        private CommandData checkInErrorQueue(CommandData commandData) {
            CommandData commandData2 = commandData;
            if (errorCommandQueue.contains(commandData)) {
                for (CommandData cd : errorCommandQueue) {
                    if (cd.equals(commandData)) {
                        cd.getResult().resetRetries(commandData.getCommand());
                        if (System.currentTimeMillis() - cd.getResult().getLastExecutedDate() > MIN_RETRY_PERIOD_MS) {
                            commandData2 = cd;
                            errorCommandQueue.remove(cd);
                        } else {
                            commandData2 = null;
                            MyLog.v(this, "Found in Error queue: " + cd);
                        }
                    } else {
                        if (System.currentTimeMillis() - cd.getResult().getLastExecutedDate() > MAX_MS_IN_ERROR_QUEUE) {
                            if (errorCommandQueue.remove(cd)) {
                                MyLog.i(this, "Removed old from Error queue: " + cd);
                            } else {
                                MyLog.i(this, "Failed to Remove old from Error queue: " + cd);
                            }
                        }
                    }
                }
            }
            return commandData2;
        }
        
        private void addToRetryQueue(CommandData commandData) {
            if (!retryCommandQueue.contains(commandData) 
                    && !retryCommandQueue.offer(commandData)) {
                MyLog.e(this, "mRetryQueue is full?");
            }
        }

        private void addToErrorQueue(CommandData commandData) {
            if (!errorCommandQueue.contains(commandData)) {
                if (!errorCommandQueue.offer(commandData)) {
                    CommandData commandData2 = errorCommandQueue.poll();
                    MyLog.d(this, "Removed from overloaded Error queue: " + commandData2);
					if (!errorCommandQueue.offer(commandData)) {
						MyLog.e(this, "Error Queue is full?");
					}
                }
            }
        }
        
        boolean isOnline() {
            if (isOnlineNotLogged()) {
                return true;
            } else {
                MyLog.v(this, "Internet Connection Not Present");
                return false;
            }
        }

        /**
         * Based on http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts
         */
        private boolean isOnlineNotLogged() {
            boolean is = false;
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo == null) {
                return false;
            }
            is = networkInfo.isAvailable() && networkInfo.isConnected();
            return is;
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
                executorEndedAt = System.currentTimeMillis();
            }
            MyLog.v(this, method);
            currentlyExecuting = null;
            currentlyExecutingSince = 0;
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
                executorStartedAt2 = executorStartedAt;
                executorEndedAt2 = executorEndedAt;
            }
            sb.append("started:" + RelativeTime.getDifference(getBaseContext(), executorStartedAt2) + ",");
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
		        if (executorEndedAt > 0) {
	                long endedSeconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - executorEndedAt);
	                if (endedSeconds < DELAY_AFTER_EXECUTOR_ENDED_SECONDS ) {
	                    return true;
	                }
		        }
		    }
			if (getStatus() != Status.RUNNING
			    || currentlyExecuting == null
				|| System.currentTimeMillis() - currentlyExecutingSince > MAX_COMMAND_EXECUTION_MS) {
				return false;
			}
			return true;
		}
    }
    
	private class HeartBeat extends AsyncTask<Void, Long, Void> {
		private static final long HEARTBEAT_PERIOD_SECONDS = 20;

        @Override
        protected Void doInBackground(Void... arg0) {
            MyLog.v(this, "Started");
			String breakReason = "";
            for (long iteration = 1; iteration < 10000; iteration++) {
				try {
					synchronized(heartBeatLock) {
						if (heartBeat != this) {
							breakReason = "Other instance found";
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
            MyLog.v(this, "Ended; " + breakReason);
			synchronized(heartBeatLock) {
				heartBeat = null;
			}
			return null;
		}

        @Override
        protected void onProgressUpdate(Long... values) {
            MyLog.v(this, "onProgressUpdate; " + values[0]);
            startStopExecution();
        }
	}
	
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
