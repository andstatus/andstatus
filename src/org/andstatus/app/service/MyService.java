/* 
 * Copyright (c) 2011-2014 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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
import java.util.concurrent.PriorityBlockingQueue;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
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
    
    /**
     * Intent with this action sent when it is time to update AndStatus
     * AppWidget.
     * <p>
     * This may be sent in response to some new information is ready for
     * notification (some changes...), or the system booting.
     * <p>
     * The intent will contain the following extras:
     * <ul>
     * <li>{@link #EXTRA_MSGTYPE}</li>
     * <li>{@link #EXTRA_NUMTWEETSMSGTYPE}</li>
     * <li>{@link android.appwidget.AppWidgetManager#EXTRA_APPWIDGET_IDS}<br/>
     * The appWidgetIds to update. This may be all of the AppWidgets created for
     * this provider, or just a subset. The system tries to send updates for as
     * few AppWidget instances as possible.</li>
     * 
     * @see AppWidgetProvider#onUpdate AppWidgetProvider.onUpdate(Context
     *      context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
     */
    public static final String ACTION_APPWIDGET_UPDATE = IntentExtra.MY_ACTION_PREFIX + "APPWIDGET_UPDATE";

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
    
    /**
     * Send broadcast to Widgets even if there are no new tweets
     */
    // TODO: Maybe this should be additional setting...
    public static final boolean UPDATE_WIDGETS_ON_EVERY_UPDATE = true;

    private volatile boolean mNotificationsEnabled;

    /**
     * Communicate state of this service 
     */
    public enum ServiceState {
        RUNNING,
        STOPPING,
        STOPPED,
        UNKNOWN;
        
        /**
         * Like valueOf but doesn't throw exceptions: it returns UNKNOWN instead 
         */
        public static ServiceState load(String str) {
            ServiceState state;
            try {
                state = valueOf(str);
            } catch (IllegalArgumentException e) {
                MyLog.v(TAG, e);
                state = UNKNOWN;
            }
            return state;
        }
        public String save() {
            return this.toString();
        }
    }
    
    private ServiceState getServiceState() {
        ServiceState state = ServiceState.STOPPED; 
        synchronized (serviceStateLock) {
            if (mInitialized) {
                if (mIsStopping) {
                    state = ServiceState.STOPPING;
                } else {
                    state = ServiceState.RUNNING;
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
    /**
     * Flag to control the Service state persistence
     */
    @GuardedBy("serviceStateLock")
    private boolean mInitialized = false;
    @GuardedBy("serviceStateLock")
    private int lastProcessedStartId = 0;
    /**
     * For now let's have only ONE working thread 
     * (it seems there is some problem in parallel execution...)
     */
    @GuardedBy("serviceStateLock")
    private QueueExecutor executor = null;

    private final Object wakeLockLock = new Object();
    /**
     * The reference to the wake lock used to keep the CPU from stopping during
     * background operations.
     */
    @GuardedBy("wakeLockLock")
    private PowerManager.WakeLock wakeLock = null;

    private final Queue<CommandData> mainCommandQueue = new PriorityBlockingQueue<CommandData>(100);
    private final Queue<CommandData> retryCommandQueue = new PriorityBlockingQueue<CommandData>(100);

    /**
     * Time when shared preferences where changed as this knows it.
     */
    protected volatile long preferencesChangeTime = 0;
    /**
     * Time when shared preferences where analyzed
     */
    protected volatile long preferencesExamineTime = 0;

    /**
     * After this the service state remains "STOPPED": we didn't initialize the instance yet!
     */
    @Override
    public void onCreate() {
        preferencesChangeTime = MyContextHolder.initialize(this, this);
        preferencesExamineTime = getMyServicePreferences().getLong(MyPreferences.KEY_PREFERENCES_EXAMINE_TIME, 0);
        MyLog.d(this, "Service created, preferencesChangeTime=" + preferencesChangeTime + ", examined=" + preferencesExamineTime);

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
                broadcastState(commandData);
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
                addToTheQueue(commandData);
            } finally {
                synchronized(serviceStateLock) {
                    dontStop = false;
                }
            }
            decideIfStopTheService(false);
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
        boolean ok = false;
        synchronized (serviceStateLock) {
            if (mInitialized) {
                ok = addToTheQueue(commandData);
            }
        }
        if (!ok) {
            MyLog.e(this, "The Service is stopping, the command was lost: "
                    + commandData.getCommand());
        }
    }

    private boolean addToTheQueue(CommandData commandData) {
        boolean ok = true;
        if ( commandData.getCommand() == CommandEnum.EMPTY) {
            // Nothing to do
        } else if (mainCommandQueue.contains(commandData)) {
            MyLog.d(this, "Duplicated " + commandData);
            // Reset retries counter on receiving duplicated command
            for (CommandData cd:mainCommandQueue) {
                if (cd.equals(commandData)) {
                    cd.retriesLeft = 0;
                    break;
                }
            }
        } else {
            MyLog.d(this, "Adding to the queue " + commandData);
            if (!mainCommandQueue.offer(commandData)) {
                MyLog.e(this, "Couldn't add to the main queue, size=" + mainCommandQueue.size());
                ok = false;
            }
        }
        return ok;
    }

    private void moveCommandsFromRetryToMainQueue() {
        while (!retryCommandQueue.isEmpty()) {
            CommandData cd = retryCommandQueue.poll();
            if (!addToTheQueue(cd)) {
                if (!retryCommandQueue.offer(cd)) {
                    MyLog.e(this, "Couldn't return to the retry Queue, size=" + retryCommandQueue.size()
                            + " command=" + cd);
                }
                break;
            }
        }
    }
    
    /**
     * Initialize and restore the state if it was not restored yet
     */
    private void initialize() {

        // Check when preferences were changed
        long preferencesChangeTimeNew = MyContextHolder.initialize(this, this);
        long preferencesExamineTimeNew = java.lang.System.currentTimeMillis();

        synchronized (serviceStateLock) {
            if (!mInitialized) {
                int count = 0;
                count += restoreQueue(mainCommandQueue, COMMANDS_QUEUE_FILENAME);
                count += restoreQueue(retryCommandQueue, RETRY_QUEUE_FILENAME);
                MyLog.d(this, "State restored, " + (count>0 ? Integer.toString(count) : "no") + " msg in the Queues");

                registerReceiver(intentReceiver, new IntentFilter(ACTION_GO));

                mNotificationsEnabled = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_enabled", false);
                
                mInitialized = true;
                broadcastState(null);
            }
        }
        
        if (preferencesChangeTime != preferencesChangeTimeNew
                || preferencesExamineTime < preferencesChangeTimeNew) {
            // Preferences changed...
            
            if (preferencesChangeTimeNew > preferencesExamineTime) {
                MyLog.d(this, "Examine at=" + preferencesExamineTimeNew + " Preferences changed at=" + preferencesChangeTimeNew);
            } else if (preferencesChangeTimeNew > preferencesChangeTime) {
                MyLog.d(this, "Preferences changed at=" + preferencesChangeTimeNew);
            } else if (preferencesChangeTimeNew == preferencesChangeTime) {
                MyLog.d(this, "Preferences didn't change, still at=" + preferencesChangeTimeNew);
            } else {
                MyLog.e(this, "Preferences change time error, time=" + preferencesChangeTimeNew);
            }
            preferencesChangeTime = preferencesChangeTimeNew;
            preferencesExamineTime = preferencesExamineTimeNew;
            getMyServicePreferences().edit().putLong(MyPreferences.KEY_PREFERENCES_EXAMINE_TIME, preferencesExamineTime).commit();
        }
    }

    private int restoreQueue(Queue<CommandData> q, String prefsFileName) {
        Context context = MyContextHolder.get().context();
        int count = 0;
        if (SharedPreferencesUtil.exists(context, prefsFileName)) {
            boolean done = false;
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName);
            do {
                CommandData cd = new CommandData(sp, count);
                if (cd.getCommand() == CommandEnum.UNKNOWN) {
                    done = true;
                } else {
                    if ( q.offer(cd) ) {
                        MyLog.v(this, "Command restored: " + cd.toString());
                        count += 1;
                    } else {
                        MyLog.e(this, "Error restoring queue, command: " + cd.toString());
                    }
                }
            } while (!done);
            sp = null;
            // Delete this saved queue
            SharedPreferencesUtil.delete(context, prefsFileName);
            MyLog.d(this, "Queue restored from " + prefsFileName  + ", " + count + " msgs");
        }
        return count;
    }

    /**
     * The idea is to have SharePreferences, that are being edited by
     * the service process only (to avoid problems of concurrent access.
     * @return Single instance of SharedPreferences, specific to the class
     */
    private SharedPreferences getMyServicePreferences() {
        return MyPreferences.getSharedPreferences(TAG);
    }

    private void decideIfStopTheService(boolean calledFromExecutor) {
        synchronized(serviceStateLock) {
            boolean isStopping = false;
            if (!mInitialized) {
                isStopping = false;
                return;
            }
            if (dontStop) {
                MyLog.v(this, "decideIfStopTheService: dontStop flag");
                return;
            }
            isStopping = isStopping();
            if (!isStopping) {
                isStopping = mainCommandQueue.isEmpty()
                        || !isOnline() 
                        || !MyContextHolder.get().isReady();
                if (isStopping && !calledFromExecutor && executor!= null) {
                    isStopping = (executor.getStatus() != Status.RUNNING);
                }
            }
            if (this.mIsStopping != isStopping) {
                if (isStopping) {
                    MyLog.v(this, "Decided to continue; startId=" + lastProcessedStartId);
                } else {
                    MyLog.v(this, "Decided to stop; startId=" + lastProcessedStartId 
                            + "; " + (totalQueuesSize() == 0 ? "queue is empty"  : "queueSize=" + totalQueuesSize())
                            );
                }
                this.mIsStopping = isStopping;
            }
            if (isStopping || calledFromExecutor ) {
                executor = null;
            }
            if (isStopping) {
                stopDelayed(true);
            } else {
                acquireWakeLock();
                if (executor == null) {
                    executor = new QueueExecutor();
                    MyLog.v(this, "Adding new executor " + executor);
                    executor.execute();
                } else {
                    MyLog.v(this, "There is an Executor already");
                }
            }
        }
    }

    /**
     * We use this function before actual requests of Internet services Based on
     * http
     * ://stackoverflow.com/questions/1560788/how-to-check-internet-access-on
     * -android-inetaddress-never-timeouts
     */
    public boolean isOnline() {
        boolean is = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            // test for connection
            if (cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isAvailable()
                    && cm.getActiveNetworkInfo().isConnected()) {
                is = true;
            } else {
                MyLog.v(this, "Internet Connection Not Present");
            }
        } catch (Exception e) {
            MyLog.v(this, "isOnline", e);
        }
        return is;
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
        String method = "stopDelayed";
        synchronized (serviceStateLock) {
            if (mInitialized) {
                if (dontStop) {
                    MyLog.d(this, method + ": dontStop flag");
                    return;
                }
                mIsStopping = true;
            } else {
                mIsStopping = false;
                return;
            }
            boolean mayStop = executor == null || executor.getStatus() != Status.RUNNING;
            if (!mayStop) {
                if (forceNow) {
                    MyLog.d(this, method + ": Forced to stop now, cancelling Executor");
                    executor.cancel(true);
                } else {
                    MyLog.v(this, method + ": Cannot stop now, executor is working");
                    broadcastState(null);
                    return;
                }
            }
            if( mInitialized) {
                try {
                    unregisterReceiver(intentReceiver);
    
                    notifyOfQueue();
                    int count = 0;
                    count += persistQueue(mainCommandQueue, COMMANDS_QUEUE_FILENAME);
                    count += persistQueue(retryCommandQueue, RETRY_QUEUE_FILENAME);
                    MyLog.d(this, "State saved, " + (count>0 ? Integer.toString(count) : "no ") + " msg in the Queues");
    
                    relealeWakeLock();
                    stopSelfResult(lastProcessedStartId);
                } finally {
                    mInitialized = false;
                    mIsStopping = false;
                    lastProcessedStartId = 0;
                    dontStop = false;
                }
            }
        }
        broadcastState(null);
    }

    private int persistQueue(Queue<CommandData> q, String prefsFileName) {
        Context context = MyContextHolder.get().context();
        int count = 0;
        // Delete any existing saved queue
        SharedPreferencesUtil.delete(context, prefsFileName);
        if (!q.isEmpty()) {
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName);
            while (!q.isEmpty()) {
                CommandData cd = q.poll();
                cd.save(sp, count);
                MyLog.v(this, "Command saved: " + cd.toString());
                count += 1;
            }
            MyLog.d(this, "Queue saved to " + prefsFileName  + ", " + count + " msgs");
        }
        return count;
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
     * Notify user of the commands Queue size
     * 
     * @return total size of Queues
     */
    private int notifyOfQueue() {
        int count = retryCommandQueue.size() + mainCommandQueue.size();
        if (count == 0 ) {
            clearNotifications();
        } else if (mNotificationsEnabled && MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_NOTIFICATIONS_QUEUE, false)) {
            if (!retryCommandQueue.isEmpty()) {
                MyLog.d(this, retryCommandQueue.size() + " commands in Retry Queue.");
            }
            if (!mainCommandQueue.isEmpty()) {
                MyLog.d(this, mainCommandQueue.size() + " commands in Main Queue.");
            }

            // Set up the notification to display to the user
            Notification notification = new Notification(R.drawable.notification_icon,
                    getText(R.string.notification_title), System.currentTimeMillis());

            int messageTitle;
            String aMessage = "";

            aMessage = I18n.formatQuantityMessage(getApplicationContext(),
                    R.string.notification_queue_format, count, R.array.notification_queue_patterns,
                    R.array.notification_queue_formats);
            messageTitle = R.string.notification_title_queue;

            // Set up the scrolling message of the notification
            notification.tickerText = aMessage;

            /**
             * Kick the commands queue by sending empty command
             * This Intent will be sent upon a User tapping the notification 
             */
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, CommandData.EMPTY_COMMAND.toIntent(intentForThisInitialized()), 0);
            notification.setLatestEventInfo(this, getText(messageTitle), aMessage, pi);

            NotificationManager nM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nM.notify(CommandEnum.NOTIFY_QUEUE.ordinal(), notification);
        }
        return count;
    }
    
    private void clearNotifications() {
        NotificationManager nM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nM != null) {
            nM.cancel(CommandEnum.NOTIFY_QUEUE.ordinal());
        }
    }
    
    /**
     * @return This intent will be received by MyService only if it's initialized
     * (and corresponding broadcast receiver registered)
     */
    public static Intent intentForThisInitialized() {
        return new Intent(MyService.ACTION_GO);
    }
    
    /**
     * Send broadcast informing of the current state of this service
     */
    private void broadcastState(CommandData commandData) {
        broadcastState(this, getServiceState(), commandData);
    }

    /**
     * Send broadcast informing of the current state of this service
     */
    public static void broadcastState(Context context, ServiceState state, CommandData commandData) {
        Intent intent = new Intent(ACTION_SERVICE_STATE);
        if (commandData != null) {
            intent = commandData.toIntent(intent);
        }
        intent.putExtra(IntentExtra.EXTRA_SERVICE_STATE.key, state.save());
        context.sendBroadcast(intent);
        MyLog.v(TAG, "state: " + state);
    }
    
    private class QueueExecutor extends AsyncTask<Void, Void, Boolean> implements CommandExecutorParent {
        
        @Override
        protected Boolean doInBackground(Void... arg0) {
            MyLog.d(this, "CommandExecutor, " + mainCommandQueue.size() + " commands to process");

            do {
                if (isStopping()) {
                    break;
                }
                
                // Get commands from the Queue one by one and execute them
                // The queue is Blocking, so we can do this
                CommandData commandData = mainCommandQueue.poll();
                if (commandData == null) {
                    break;
                }
                commandData.resetCommandResult();
                new CommandExecutorAllAccounts().setCommandData(commandData).setParent(this).execute();
                if (shouldWeRetry(commandData)) {
                    synchronized(MyService.this) {
                        // Put the command to the retry queue
                        if (!retryCommandQueue.contains(commandData) 
                                && !retryCommandQueue.offer(commandData)) {
                            MyLog.e(this, "mRetryQueue is full?");
                        }
                    }        
                }
                MyLog.d(this, (commandData.getResult().hasError() 
                        ? (commandData.getResult().willRetry ? "Will retry" : "Failed") 
                                : "Succeeded") 
                        + " " + commandData);
                broadcastState(commandData);
                if (commandData.getResult().hasError() && !isOnline()) {
                    // Don't bother with other commands if we're not Online :-)
                    break;
                }
            } while (true);
            return true;
        }

        private boolean shouldWeRetry(CommandData commandData) {
            commandData.getResult().willRetry = false;
            if (commandData.getResult().hasError()) {
                switch (commandData.getCommand()) {
                    case AUTOMATIC_UPDATE:
                    case FETCH_TIMELINE:
                    case RATE_LIMIT_STATUS:
                        break;
                    default:
                        if (!commandData.getResult().hasHardError()) {
                            commandData.getResult().willRetry = true;
                        }
                        break;
                }
            }
            if (commandData.getResult().willRetry) {
                if (commandData.retriesLeft == 0) {
                    // This means that retriesLeft was not set yet,
                    // so let's set it to some default value, the same for
                    // any command that needs to be retried...
                    commandData.retriesLeft = 10;
                }
                commandData.retriesLeft -= 1;
                if (commandData.retriesLeft == 0) {
                    commandData.getResult().willRetry = false;
                }
                
            }
            return commandData.getResult().willRetry;
        }
        
        /**
         * This is in the UI thread, so we can mess with the UI
         */
        @Override
        protected void onPostExecute(Boolean notUsed) {
            MyLog.v(this, "onPostExecute");
            decideIfStopTheService(true);
        }

        @Override
        protected void onCancelled(Boolean result) {
            MyLog.v(this, "onCancelled, result=" + result);
            decideIfStopTheService(true);
        }

        @Override
        public boolean isStopping() {
            return MyService.this.isStopping();
        }
        
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
