/* 
 * Copyright (c) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.appwidget.MyAppWidgetProvider;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbRateLimitStatus;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.BaseColumns;

import net.jcip.annotations.GuardedBy;

/**
 * This is an application service that serves as a connection between this Android Device
 * and Microblogging system. Other applications can interact with it via IPC.
 */
public class MyService extends Service {
    private static final String TAG = MyService.class.getSimpleName();

    static final String packageName = MyService.class.getPackage().getName();

    /**
     * Prefix of all actions of this Service
     */
    private static final String ACTIONPREFIX = packageName + ".action.";

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
    public static final String ACTION_APPWIDGET_UPDATE = ACTIONPREFIX + "APPWIDGET_UPDATE";

    /**
     * Broadcast with this action is being sent by {@link MyService} to notify of its state.
     *  Actually {@link MyServiceManager} receives it.
     */
    public static final String ACTION_SERVICE_STATE = ACTIONPREFIX + "SERVICE_STATE";

    /**
     * This action is used in any intent sent to this service. Actual command to
     * perform by this service is in the {@link #EXTRA_MSGTYPE} extra of the
     * intent
     * 
     * @see CommandEnum
     */
    public static final String ACTION_GO = ACTIONPREFIX + "GO";
    
    /**
     * The command to the MyService or to MyAppWidgetProvider as a
     * enum We use 'code' for persistence
     * 
     * @author yvolk@yurivolkov.com
     */
    public enum CommandEnum {

        /**
         * The action is unknown
         */
        UNKNOWN("unknown"),
        /**
         * There is no action
         */
        EMPTY("empty"),
        /**
         * The action to fetch all usual timelines in the background.
         */
        AUTOMATIC_UPDATE("automatic-update"),
        /**
         * Fetch timeline(s) of the specified type for the specified MyAccount. 
         */
        FETCH_TIMELINE("fetch-timeline"),

        CREATE_FAVORITE("create-favorite"), DESTROY_FAVORITE("destroy-favorite"),

        FOLLOW_USER("follow-user"), STOP_FOLLOWING_USER("stop-following-user"),
        UPDATE_FOLLOWING_USERS_IF_NECESSARY("update-following-users-ifnecessary"),

        /**
         * This command is for sending both public and direct messages
         */
        UPDATE_STATUS("update-status"), 
        DESTROY_STATUS("destroy-status"),
        GET_STATUS("get-status"),
        
        REBLOG("reblog"),
        DESTROY_REBLOG("destroy-reblog"),

        RATE_LIMIT_STATUS("rate-limit-status"),

        /**
         * Notify User about commands in the Queue
         */
        NOTIFY_QUEUE("notify-queue"),

        /**
         * Commands to the Widget New tweets|messages were successfully loaded
         * from the server
         */
        NOTIFY_DIRECT_MESSAGE("notify-direct-message"),
        /**
         * New messages in the Home timeline of Account
         */
        NOTIFY_HOME_TIMELINE("notify-home-timeline"),
        /**
         * Mentions and replies are currently shown in one timeline
         */
        NOTIFY_MENTIONS("notify-mentions"), 
                // TODO: Add NOTIFY_REPLIES("notify-replies"),
        /**
         * Clear previous notifications (because e.g. user open tweet list...)
         */
        NOTIFY_CLEAR("notify-clear"),

        /**
         * Stop the service after finishing all asynchronous treads (i.e. not immediately!)
         */
        STOP_SERVICE("stop-service"),

        /**
         * Broadcast back state of {@link MyService}
         */
        BROADCAST_SERVICE_STATE("broadcast-service-state"),
        
        /**
         * Save SharePreverence. We try to use it because sometimes Android
         * doesn't actually store these values to the disk... and the
         * preferences get lost. I think this is mainly because of several
         * processes using the same preferences
         */
        PUT_BOOLEAN_PREFERENCE("put-boolean-preference"), PUT_LONG_PREFERENCE("put-long-preference"), PUT_STRING_PREFERENCE(
                "put-string-preference");

        /**
         * code of the enum that is used in messages
         */
        private String code;

        private CommandEnum(String codeIn) {
            code = codeIn;
        }

        /**
         * String code for the Command to be used in messages
         */
        public String save() {
            return code;
        }

        /**
         * Returns the enum for a String action code or UNKNOWN
         */
        public static CommandEnum load(String strCode) {
            for (CommandEnum serviceCommand : CommandEnum.values()) {
                if (serviceCommand.code.equals(strCode)) {
                    return serviceCommand;
                }
            }
            return UNKNOWN;
        }

    }

    /**
     * Send broadcast to Widgets even if there are no new tweets
     */
    // TODO: Maybe this should be additional setting...
    public static boolean updateWidgetsOnEveryUpdate = true;

    private volatile boolean mNotificationsEnabled;
    private volatile boolean mNotificationsVibrate;

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
    private CommandExecutor executor = null;

    private final Object wakeLockLock = new Object();
    /**
     * The reference to the wake lock used to keep the CPU from stopping during
     * background operations.
     */
    @GuardedBy("wakeLockLock")
    private PowerManager.WakeLock wakeLock = null;

    private final Queue<CommandData> mainCommandQueue = new ArrayBlockingQueue<CommandData>(100, true);
    private final Queue<CommandData> retryCommandQueue = new ArrayBlockingQueue<CommandData>(100, true);

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
        MyLog.d(TAG, "Service created, preferencesChangeTime=" + preferencesChangeTime + ", examined=" + preferencesExamineTime);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.d(TAG, "onStartCommand: startid=" + startId);
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
        switch (commandData.command) {
            case STOP_SERVICE:
                MyLog.v(this, "Command " + commandData.command + " received");
                stopDelayed(false);
                break;
            case BROADCAST_SERVICE_STATE:
                broadcastState(commandData);
                break;
            case UNKNOWN:
                MyLog.v(this, "Command " + commandData.command + " ignored");
                break;
            default:
                receiveOtherCommand(commandData, startId);
                break;
        }
    }

    private void receiveOtherCommand(CommandData commandData, int startId) {
        boolean isStopping = false;
        synchronized(serviceStateLock) {
            isStopping = isStopping();
            if (!isStopping) {
                dontStop = true;
                if (startId != 0) {
                    lastProcessedStartId = startId;
                }
            }
        }
        if (isStopping) {
            MyLog.v(this, "The Service is stopping: ignoring the command: " +  commandData.command);
        } else {
            try {
                initialize();
                if (mainCommandQueue.isEmpty()) {
                    // This is a good place to send commands from retry Queue
                    while (!retryCommandQueue.isEmpty()) {
                        CommandData cd = retryCommandQueue.poll();
                        if (!mainCommandQueue.contains(cd)) {
                            if (!mainCommandQueue.offer(cd)) {
                                MyLog.e(this, "mCommands is full?");
                            }
                        }
                    }
                }
                if ( commandData.command == CommandEnum.EMPTY) {
                    // Nothing to do
                } else if (mainCommandQueue.contains(commandData)) {
                    MyLog.d(TAG, "Duplicated " + commandData);
                    // Reset retries counter on receiving duplicated command
                    for (CommandData cd:mainCommandQueue) {
                        if (cd.equals(commandData)) {
                            cd.retriesLeft = 0;
                            break;
                        }
                    }
                } else {
                    MyLog.d(TAG, "Adding to the queue " + commandData);
                    if (!mainCommandQueue.offer(commandData)) {
                        MyLog.e(this, "mCommands is full?");
                    }
                }
            } finally {
                synchronized(serviceStateLock) {
                    dontStop = false;
                }
            }
            desideIfStopTheService(false);
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
                // Restore Queues
                count += restoreQueue(mainCommandQueue, TAG + "_" + "mCommands");
                count += restoreQueue(retryCommandQueue, TAG + "_" + "mRetryQueue");
                MyLog.d(TAG, "State restored, " + (count>0 ? Integer.toString(count) : "no") + " msg in the Queues");

                registerReceiver(intentReceiver, new IntentFilter(ACTION_GO));

                mNotificationsEnabled = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_enabled", false);
                mNotificationsVibrate = MyPreferences.getDefaultSharedPreferences().getBoolean("vibration", false);
                
                mInitialized = true;
                broadcastState(null);
            }
        }
        
        if (preferencesChangeTime != preferencesChangeTimeNew
                || preferencesExamineTime < preferencesChangeTimeNew) {
            // Preferences changed...
            
            if (preferencesChangeTimeNew > preferencesExamineTime) {
                MyLog.d(TAG, "Examine at=" + preferencesExamineTimeNew + " Preferences changed at=" + preferencesChangeTimeNew);
            } else if (preferencesChangeTimeNew > preferencesChangeTime) {
                MyLog.d(TAG, "Preferences changed at=" + preferencesChangeTimeNew);
            } else if (preferencesChangeTimeNew == preferencesChangeTime) {
                MyLog.d(TAG, "Preferences didn't change, still at=" + preferencesChangeTimeNew);
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
                if (cd.command == CommandEnum.UNKNOWN) {
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
            MyLog.d(TAG, "Queue restored from " + prefsFileName  + ", " + count + " msgs");
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

    private void desideIfStopTheService(boolean calledFromExecutor) {
        synchronized(serviceStateLock) {
            boolean isStopping = false;
            if (!mInitialized) {
                isStopping = false;
                return;
            }
            if (dontStop) {
                MyLog.v(this, "desideIfStopTheService: dontStop flag");
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
                    MyLog.v(this, "Desided to continue; startId=" + lastProcessedStartId);
                } else {
                    MyLog.v(this, "Desided to stop; startId=" + lastProcessedStartId 
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
                    executor = new CommandExecutor();
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
        } catch (Exception e) {}
        return is;
    }
    
    private void acquireWakeLock() {
        synchronized(wakeLockLock) {
            if (wakeLock == null) {
                MyLog.d(TAG, "Acquiring wakelock");
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
        MyLog.v(TAG, "onDestroy");
        stopDelayed(true);
        MyLog.d(TAG, "Service destroyed");
    }
    
    /**
     * Notify background processes that the service is stopping.
     * Stop if background processes has finished.
     * Persist everything that we'll need on next Service creation and free resources
     * @param boolean forceNow 
     */
    private void stopDelayed(boolean forceNow) {
        synchronized (serviceStateLock) {
            if (mInitialized) {
                if (dontStop) {
                    MyLog.d(this, "stopDelayed: dontStop flag");
                    return;
                }
                mIsStopping = true;
            } else {
                mIsStopping = false;
                return;
            }
            boolean mayStop = (executor == null || executor.getStatus() == Status.FINISHED);
            if (!mayStop) {
                if (forceNow) {
                    MyLog.d(this, "stopDelayed: Forced to stop now, cancelling Executor");
                    executor.cancel(true);
                } else {
                    MyLog.v(this, "stopDelayed: Cannot stop now, executor is working");
                    broadcastState(null);
                    return;
                }
            }
            if( mInitialized) {
                try {
                    unregisterReceiver(intentReceiver);
    
                    notifyOfQueue();
                    int count = 0;
                    // Save Queues
                    count += persistQueue(mainCommandQueue, TAG + "_" + "mCommands");
                    count += persistQueue(retryCommandQueue, TAG + "_" + "mRetryQueue");
                    MyLog.d(TAG, "State saved, " + (count>0 ? Integer.toString(count) : "no ") + " msg in the Queues");
    
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
        if (q.size() > 0) {
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName);
            while (q.size() > 0) {
                CommandData cd = q.poll();
                cd.save(sp, count);
                MyLog.v(this, "Command saved: " + cd.toString());
                count += 1;
            }
            MyLog.d(TAG, "Queue saved to " + prefsFileName  + ", " + count + " msgs");
        }
        return count;
    }

    private void relealeWakeLock() {
        synchronized(wakeLockLock) {
            if (wakeLock != null) {
                MyLog.d(TAG, "Releasing wakelock");
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
            if (retryCommandQueue.size() > 0) {
                MyLog.d(TAG, retryCommandQueue.size() + " commands in Retry Queue.");
            }
            if (mainCommandQueue.size() > 0) {
                MyLog.d(TAG, mainCommandQueue.size() + " commands in Main Queue.");
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
             * Set the latest event information and send the notification
             * Actually don't start any intent
             * @see <a href="http://stackoverflow.com/questions/4232006/android-notification-pendingintent-problem">android-notification-pendingintent-problem</a>
             */
            // PendingIntent pi = PendingIntent.getActivity(this, 0, null, 0);

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
    
    private class CommandExecutor extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... arg0) {
            MyLog.d(TAG, "CommandExecutor, " + mainCommandQueue.size() + " commands to process");

            do {
                if (isStopping()) break;
                
                // Get commands from the Queue one by one and execute them
                // The queue is Blocking, so we can do this
                CommandData commandData = mainCommandQueue.poll();
                if (commandData == null) {
                    break;
                }
                commandData.resetCommandResult();
                executeOneCommand(commandData);
                if (shouldWeRetry(commandData)) {
                    synchronized(MyService.this) {
                        // Put the command to the retry queue
                        if (!retryCommandQueue.contains(commandData)) {
                            if (!retryCommandQueue.offer(commandData)) {
                                MyLog.e(this, "mRetryQueue is full?");
                            }
                        }
                    }        
                }
                MyLog.d(TAG, (commandData.commandResult.hasError() ?
                        (commandData.commandResult.willRetry ? "Will retry" : "Failed") : "Succeeded") 
                        + " " + commandData);
                broadcastState(commandData);
                if (commandData.commandResult.hasError() && !isOnline()) {
                    // Don't bother with other commands if we're not Online :-)
                    break;
                }
            } while (true);
            return true;
        }

        private void executeOneCommand(CommandData commandData) {
            MyLog.d(TAG, "Executing " + commandData);
            switch (commandData.command) {
                case AUTOMATIC_UPDATE:
                case FETCH_TIMELINE:
                    loadTimeline(commandData);
                    break;
                case CREATE_FAVORITE:
                case DESTROY_FAVORITE:
                    createOrDestroyFavorite(commandData,
                            commandData.itemId, 
                            commandData.command == CommandEnum.CREATE_FAVORITE);
                    break;
                case FOLLOW_USER:
                case STOP_FOLLOWING_USER:
                    followOrStopFollowingUser(commandData,
                            commandData.itemId, 
                            commandData.command == CommandEnum.FOLLOW_USER);
                    break;
                case UPDATE_STATUS:
                    String status = commandData.bundle.getString(IntentExtra.EXTRA_STATUS.key).trim();
                    long replyToId = commandData.bundle.getLong(IntentExtra.EXTRA_INREPLYTOID.key);
                    long recipientId = commandData.bundle.getLong(IntentExtra.EXTRA_RECIPIENTID.key);
                    updateStatus(commandData, status, replyToId, recipientId);
                    break;
                case DESTROY_STATUS:
                    destroyStatus(commandData, commandData.itemId);
                    break;
                case DESTROY_REBLOG:
                    destroyReblog(commandData, commandData.itemId);
                    break;
                case GET_STATUS:
                    getStatus(commandData);
                    break;
                case REBLOG:
                    reblog(commandData, commandData.itemId);
                    break;
                case RATE_LIMIT_STATUS:
                    rateLimitStatus(commandData);
                    break;
                default:
                    MyLog.e(this, "Unexpected command here " + commandData);
            }
        }

        private boolean shouldWeRetry(CommandData commandData) {
            commandData.commandResult.willRetry = false;
            if (commandData.commandResult.hasError()) {
                switch (commandData.command) {
                    case AUTOMATIC_UPDATE:
                    case FETCH_TIMELINE:
                    case RATE_LIMIT_STATUS:
                        break;
                    default:
                        if (!commandData.commandResult.hasHardError()) {
                            commandData.commandResult.willRetry = true;
                        }
                }
            }
            if (commandData.commandResult.willRetry) {
                if (commandData.retriesLeft == 0) {
                    // This means that retriesLeft was not set yet,
                    // so let's set it to some default value, the same for
                    // any command that needs to be retried...
                    commandData.retriesLeft = 10;
                }
                commandData.retriesLeft -= 1;
                if (commandData.retriesLeft == 0) {
                    commandData.commandResult.willRetry = false;
                }
                
            }
            return commandData.commandResult.willRetry;
        }
        
        /**
         * This is in the UI thread, so we can mess with the UI
         */
        @Override
        protected void onPostExecute(Boolean notUsed) {
            desideIfStopTheService(true);
        }

        @Override
        protected void onCancelled(Boolean result) {
            MyLog.v(this, "Executor was cancelled, result=" + result);
            desideIfStopTheService(true);
        }
        
        /**
         * @param create true - create, false - destroy
         */
        private void createOrDestroyFavorite(CommandData commandData, long msgId, boolean create) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            MyAccount ma = commandData.getAccount();
            boolean ok = false;
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
            MbMessage message = null;
            if (oid.length() > 0) {
                try {
                    if (create) {
                        message = ma.getConnection().createFavorite(oid);
                    } else {
                        message = ma.getConnection().destroyFavorite(oid);
                    }
                    ok = !message.isEmpty();
                } catch (ConnectionException e) {
                    logConnectionException(e, commandData, (create ? "create" : "destroy") + "Favorite Connection Exception");
                }
            } else {
                MyLog.e(this,
                        (create ? "create" : "destroy") + "Favorite; msgId not found: " + msgId);
            }
            if (ok) {
                if (SharedPreferencesUtil.isTrue(message.favoritedByActor) != create) {
                    /**
                     * yvolk: 2011-09-27 Twitter docs state that
                     * this may happen due to asynchronous nature of
                     * the process, see
                     * https://dev.twitter.com/docs/
                     * api/1/post/favorites/create/%3Aid
                     */
                    if (create) {
                        // For the case we created favorite, let's
                        // change
                        // the flag manually.
                        message.favoritedByActor = TriState.fromBoolean(create);

                        MyLog.d(TAG,
                                (create ? "create" : "destroy")
                                        + ". Favorited flag didn't change yet.");

                        // Let's try to assume that everything was
                        // Ok:
                        ok = true;
                    } else {
                        // yvolk: 2011-09-27 Sometimes this
                        // twitter.com 'async' process doesn't work
                        // so let's try another time...
                        // This is safe, because "delete favorite"
                        // works even for the "Unfavorited" tweet :-)
                        ok = false;

                        MyLog.e(this,
                                (create ? "create" : "destroy")
                                        + ". Favorited flag didn't change yet.");
                    }
                }

                if (ok) {
                    // Please note that the Favorited message may be NOT in the User's Home timeline!
                    new DataInserter(ma,
                            MyService.this.getApplicationContext(),
                            TimelineTypeEnum.ALL).insertOrUpdateMsg(message);
                }
            }
            setSoftErrorIfNotOk(commandData, ok);
            MyLog.d(TAG, (create ? "Creating" : "Destroying") + " favorite "
                    + (ok ? "succeded" : "failed") + ", id=" + msgId);
        }

        /**
         * @param userId
         * @param follow true - Follow, false - Stop following
         */
        private void followOrStopFollowingUser(CommandData commandData, long userId, boolean follow) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            MyAccount ma = commandData.getAccount();
            boolean ok = false;
            String oid = MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
            MbUser user = null;
            if (oid.length() > 0) {
                try {
                    user = ma.getConnection().followUser(oid, follow);
                    ok = !user.isEmpty();
                } catch (ConnectionException e) {
                    logConnectionException(e, commandData, (follow ? "Follow" : "Stop following") + " Exception");
                }
            } else {
                MyLog.e(this,
                        (follow ? "Follow" : "Stop following") + " User; userId not found: " + userId);
            }
            if (ok) {
                if (user.followedByActor != TriState.UNKNOWN &&  user.followedByActor.toBoolean(follow) != follow) {
                    if (follow) {
                        // Act just like for creating favorite...
                        user.followedByActor = TriState.fromBoolean(follow);

                        MyLog.d(TAG,
                                (follow ? "Follow" : "Stop following") + " User. 'following' flag didn't change yet.");

                        // Let's try to assume that everything was
                        // Ok:
                        ok = true;
                    } else {
                        ok = false;

                        MyLog.e(this,
                                (follow ? "Follow" : "Stop following") + " User. 'following' flag didn't change yet.");
                    }
                }
                if (ok) {
                    new DataInserter(ma,
                            MyService.this.getApplicationContext(),
                            TimelineTypeEnum.HOME).insertOrUpdateUser(user);
                    getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
                }
            }
            setSoftErrorIfNotOk(commandData, ok);
            MyLog.d(TAG, (follow ? "Follow" : "Stop following") + " User "
                    + (ok ? "succeded" : "failed") + ", id=" + userId);
        }
        
        /**
         * @param msgId ID of the message to destroy
         */
        private void destroyStatus(CommandData commandData, long msgId) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            MyAccount ma = commandData.getAccount();
            boolean ok = false;
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
            try {
                ok = ma.getConnection().destroyStatus(oid);
            } catch (ConnectionException e) {
                if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                    // This means that there is no such "Status", so we may
                    // assume that it's Ok!
                    ok = true;
                } else {
                    logConnectionException(e, commandData, "destroyStatus Exception");
                }
            }

            if (ok) {
                // And delete the status from the local storage
                try {
                    // TODO: Maybe we should use Timeline Uri...
                    MyService.this.getApplicationContext().getContentResolver()
                            .delete(MyDatabase.Msg.CONTENT_URI, BaseColumns._ID + " = " + msgId, 
                                    null);
                } catch (Exception e) {
                    MyLog.e(this, "Error destroying status locally: " + e.toString());
                }
            }
            setSoftErrorIfNotOk(commandData, ok);
            MyLog.d(TAG, "Destroying status " + (ok ? "succeded" : "failed") + ", id=" + msgId);
        }


        /**
         * @param msgId ID of the message to destroy
         */
        private void destroyReblog(CommandData commandData, long msgId) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            MyAccount ma = commandData.getAccount();
            boolean ok = false;
            String oid = MyProvider.idToOid(OidEnum.REBLOG_OID, msgId, ma.getUserId());
            try {
                ok = ma.getConnection().destroyStatus(oid);
            } catch (ConnectionException e) {
                if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                    // This means that there is no such "Status", so we may
                    // assume that it's Ok!
                    ok = true;
                } else {
                    logConnectionException(e, commandData, "destroyReblog Exception");
                }
            }

            if (ok) {
                // And delete the status from the local storage
                try {
                    ContentValues values = new ContentValues();
                    values.put(MyDatabase.MsgOfUser.REBLOGGED, 0);
                    values.putNull(MyDatabase.MsgOfUser.REBLOG_OID);
                    Uri msgUri = MyProvider.getTimelineMsgUri(ma.getUserId(), TimelineTypeEnum.HOME, false, msgId);
                    MyService.this.getApplicationContext().getContentResolver().update(msgUri, values, null, null);
                } catch (Exception e) {
                    MyLog.e(this, "Error destroying reblog locally: " + e.toString());
                }
            }
            setSoftErrorIfNotOk(commandData, ok);
            MyLog.d(TAG, "Destroying reblog " + (ok ? "succeded" : "failed") + ", id=" + msgId);
        }

        private void getStatus(CommandData commandData) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            boolean ok = false;
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, commandData.itemId, 0);
            try {
                MbMessage message = commandData.getAccount().getConnection().getMessage(oid);
                if (!message.isEmpty()) {
                    // And add the message to the local storage
                    try {
                        new DataInserter(commandData.getAccount(),
                                MyService.this.getApplicationContext(),
                                TimelineTypeEnum.ALL).insertOrUpdateMsg(message);
                        ok = true;
                    } catch (Exception e) {
                        MyLog.e(this, "Error inserting status: " + e.toString());
                    }
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                    commandData.commandResult.numParseExceptions++;
                    // This means that there is no such "Status"
                    // TODO: so we don't need to retry this command
                }
                logConnectionException(e, commandData, "getStatus Exception");
            }
            setSoftErrorIfNotOk(commandData, ok);
            MyLog.d(TAG, "getStatus " + (ok ? "succeded" : "failed") + ", id=" + commandData.itemId);
        }
        
        /**
         * @param status
         * @param replyToMsgId - Message Id
         * @param recipientUserId !=0 for Direct messages - User Id
         */
        private void updateStatus(CommandData commandData, String status, long replyToMsgId, long recipientUserId) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            MyAccount ma = commandData.getAccount();
            boolean ok = false;
            MbMessage message = null;
            try {
                if (recipientUserId == 0) {
                    String replyToMsgOid = MyProvider.idToOid(OidEnum.MSG_OID, replyToMsgId, 0);
                    message = ma.getConnection()
                            .updateStatus(status.trim(), replyToMsgOid);
                } else {
                    String recipientOid = MyProvider.idToOid(OidEnum.USER_OID, recipientUserId, 0);
                    // Currently we don't use Screen Name, I guess id is enough.
                    message = ma.getConnection()
                            .postDirectMessage(status.trim(), recipientOid);
                }
                ok = (!message.isEmpty());
            } catch (ConnectionException e) {
                logConnectionException(e, commandData, "updateStatus Exception");
            }
            if (ok) {
                // The message was sent successfully
                // New User's message should be put into the user's Home timeline.
                new DataInserter(ma, 
                        MyService.this.getApplicationContext(),
                        (recipientUserId == 0) ? TimelineTypeEnum.HOME : TimelineTypeEnum.DIRECT)
                .insertOrUpdateMsg(message);
            }
            setSoftErrorIfNotOk(commandData, ok);
        }
        
        private void reblog(CommandData commandData, long rebloggedId) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, rebloggedId, 0);
            boolean ok = false;
            MbMessage result = null;
            try {
                result = commandData.getAccount().getConnection()
                        .postReblog(oid);
                ok = !result.isEmpty();
            } catch (ConnectionException e) {
                logConnectionException(e, commandData, "Reblog Exception");
            }
            if (ok) {
                // The tweet was sent successfully
                // Reblog should be put into the user's Home timeline!
                new DataInserter(commandData.getAccount(), 
                        MyService.this.getApplicationContext(),
                        TimelineTypeEnum.HOME).insertOrUpdateMsg(result);
            }
            setSoftErrorIfNotOk(commandData, ok);
        }
        
        private void logConnectionException(ConnectionException e, CommandData commandData, String detailedMessage) {
            if (e.isHardError()) {
                commandData.commandResult.numParseExceptions += 1;
            } else {
                commandData.commandResult.numIoExceptions += 1;
            }
            MyLog.e(this, detailedMessage + ": " + e.toString());
        }
        
        /**
         * Load one or all timeline(s) for one or all accounts
         * @return True if everything Succeeded
         */
        private void loadTimeline(CommandData commandData) {
            if (commandData.getAccount() == null) {
                for (MyAccount acc : MyContextHolder.get().persistentAccounts().list()) {
                    loadTimelineAccount(commandData, acc);
                    if (isStopping()) {
                        setSoftErrorIfNotOk(commandData, false);
                        break;
                    }
                }
            } else {
                loadTimelineAccount(commandData, commandData.getAccount());
            }
            if (!commandData.commandResult.hasError() && commandData.timelineType == TimelineTypeEnum.ALL && !isStopping()) {
                new DataPruner(MyService.this.getApplicationContext()).prune();
            }
            if (!commandData.commandResult.hasError()) {
                // Notify all timelines, 
                // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
                MyContextHolder.get().context().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
            }
        }

        /**
         * Load Timeline(s) for one MyAccount
         * @return True if everything Succeeded
         */
        private void loadTimelineAccount(CommandData commandData, MyAccount acc) {
            if (setErrorIfCredentialsNotVerified(commandData, acc)) {
                return;
            }
            boolean okAllTimelines = true;
            boolean ok = false;
            MessageCounters counters = new MessageCounters(acc, MyService.this.getApplicationContext(), TimelineTypeEnum.ALL);
            String descr = "(starting)";

            long userId = commandData.itemId;
            if (userId == 0) {
                userId = acc.getUserId();
            }

            TimelineTypeEnum[] atl;
            if (commandData.timelineType == TimelineTypeEnum.ALL) {
                atl = new TimelineTypeEnum[] {
                        TimelineTypeEnum.HOME, TimelineTypeEnum.MENTIONS,
                        TimelineTypeEnum.DIRECT,
                        TimelineTypeEnum.FOLLOWING_USER
                };
            } else {
                atl = new TimelineTypeEnum[] {
                        commandData.timelineType
                };
            }

            int pass = 1;
            boolean okSomething = false;
            boolean notOkSomething = false;
            boolean oKs[] = new boolean[atl.length];
            try {
                for (int ind = 0; ind <= atl.length; ind++) {
                    if (isStopping()) {
                        okAllTimelines = false;
                        break;
                    }

                    if (ind == atl.length) {
                        // This is some trick for the cases
                        // when we load more than one timeline at once
                        // and there was an error on some timeline only
                        if (pass > 1 || !okSomething || !notOkSomething) {
                            break;
                        }
                        pass++;
                        ind = 0; // Start from beginning
                        MyLog.d(TAG, "Second pass of loading timeline");
                    }
                    if (pass > 1) {
                        // Find next error index
                        for (int ind2 = ind; ind2 < atl.length; ind2++) {
                            if (!oKs[ind2]) {
                                ind = ind2;
                                break;
                            }
                        }
                        if (oKs[ind]) {
                            // No more errors on the second pass
                            break;
                        }
                    }
                    ok = true;
                    TimelineTypeEnum timelineType = atl[ind];
                    if (acc.getConnection().isApiSupported(timelineType.getConnectionApiRoutine())) {
                        MyLog.d(TAG, "Getting " + timelineType.save() + " for "
                                + acc.getAccountName());
                        TimelineDownloader fl = null;
                        descr = "loading " + timelineType.save();
                        counters.timelineType = timelineType;
                        fl = TimelineDownloader.newInstance(counters, userId);
                        fl.download();
                        counters.accumulate();
                    } else {
                        MyLog.v(this, "Not supported " + timelineType.save() + " for "
                                + acc.getAccountName());
                    }

                    if (ok) {
                        okSomething = true;
                    } else {
                        notOkSomething = true;
                    }
                    oKs[ind] = ok;
                }
            } catch (ConnectionException e) {
                logConnectionException(e, commandData, descr +" Exception");
                ok = false;
            } catch (SQLiteConstraintException e) {
                MyLog.e(this, descr + ", SQLite Exception: " + e.toString());
                ok = false;
            }

            if (ok) {
                descr = "notifying";
                notifyOfUpdatedTimeline(counters.msgAdded, counters.mentionsAdded, counters.directedAdded);
            }

            String message = "";
            if (oKs.length <= 1) {
                message += (ok ? "Succeeded" : "Failed");
                okAllTimelines = ok;
            } else {
                int nOks = 0;
                for (int ind = 0; ind < oKs.length; ind++) {
                    if (oKs[ind]) {
                        nOks += 1;
                    }
                }
                if (nOks > 0) {
                    message += "Succeded " + nOks;
                    if (nOks < oKs.length) {
                        message += " of " + oKs.length;
                        okAllTimelines = false;
                    }
                } else {
                    message += "Failed " + oKs.length;
                    okAllTimelines = false;
                }
                message += " times";
            }
            setSoftErrorIfNotOk(commandData, okAllTimelines);
            
            message += " getting " + commandData.timelineType.save()
                    + " for " + acc.getAccountName() + counters.accumulatedToString();
            MyLog.d(TAG, message);
        }
        
        /**
         * TODO: Different notifications for different Accounts
         * @param msgAdded Number of "Tweets" added
         * @param mentionsAdded
         * @param directedAdded
         */
        private void notifyOfUpdatedTimeline(int msgAdded, int mentionsAdded, int directedAdded) {
            boolean notified = false;
            if (mentionsAdded > 0) {
                notifyOfNewTweets(mentionsAdded, CommandEnum.NOTIFY_MENTIONS);
                notified = true;
            }
            if (directedAdded > 0) {
                notifyOfNewTweets(directedAdded, CommandEnum.NOTIFY_DIRECT_MESSAGE);
                notified = true;
            }
            if (msgAdded > 0 || !notified) {
                notifyOfNewTweets(msgAdded, CommandEnum.NOTIFY_HOME_TIMELINE);
                notified = true;
            }
        }
        
        /**
         * Notify the user of new tweets.
         * 
         * @param numHomeTimeline
         */
        private void notifyOfNewTweets(int numTweets, CommandEnum msgType) {
            MyLog.d(TAG, "notifyOfNewTweets n=" + numTweets + "; msgType=" + msgType);

            if (updateWidgetsOnEveryUpdate) {
                // Notify widgets even about the fact, that update occurred
                // even if there was nothing new
                updateWidgets(numTweets, msgType);
            }

            // If no notifications are enabled, return
            if (!mNotificationsEnabled || numTweets == 0) {
                return;
            }

            boolean notificationsMessages = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_messages", false);
            boolean notificationsReplies = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_mentions", false);
            boolean notificationsTimeline = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_timeline", false);
            String ringtone = MyPreferences.getDefaultSharedPreferences().getString(MyPreferences.KEY_RINGTONE_PREFERENCE, null);

            // Make sure that notifications haven't been turned off for the
            // message type
            switch (msgType) {
                case NOTIFY_MENTIONS:
                    if (!notificationsReplies)
                        return;
                    break;
                case NOTIFY_DIRECT_MESSAGE:
                    if (!notificationsMessages)
                        return;
                    break;
                case NOTIFY_HOME_TIMELINE:
                    if (!notificationsTimeline)
                        return;
                    break;
                default:
                    break;
            }

            // Set up the notification to display to the user
            Notification notification = new Notification(R.drawable.notification_icon,
                    getText(R.string.notification_title), System.currentTimeMillis());

            notification.vibrate = null;
            if (mNotificationsVibrate) {
                notification.vibrate = new long[] {
                        200, 300, 200, 300
                };
            }

            notification.flags = Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
            notification.ledOffMS = 1000;
            notification.ledOnMS = 500;
            notification.ledARGB = Color.GREEN;

            if ("".equals(ringtone) || ringtone == null) {
                notification.sound = null;
            } else {
                Uri ringtoneUri = Uri.parse(ringtone);
                notification.sound = ringtoneUri;
            }

            // Set up the pending intent
            PendingIntent contentIntent;

            int messageTitle;
            Intent intent;
            String aMessage = "";

            // Prepare "intent" to launch timeline activities exactly like in
            // org.andstatus.app.TimelineActivity.onOptionsItemSelected
            switch (msgType) {
                case NOTIFY_MENTIONS:
                    aMessage = I18n.formatQuantityMessage(getApplicationContext(),
                            R.string.notification_new_mention_format, numTweets,
                            R.array.notification_mention_patterns,
                            R.array.notification_mention_formats);
                    messageTitle = R.string.notification_title_mentions;
                    intent = new Intent(getApplicationContext(), TimelineActivity.class);
                    intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                            MyDatabase.TimelineTypeEnum.MENTIONS.save());
                    contentIntent = PendingIntent.getActivity(getApplicationContext(), numTweets,
                            intent, 0);
                    break;

                case NOTIFY_DIRECT_MESSAGE:
                    aMessage = I18n.formatQuantityMessage(getApplicationContext(),
                            R.string.notification_new_message_format, numTweets,
                            R.array.notification_message_patterns,
                            R.array.notification_message_formats);
                    messageTitle = R.string.notification_title_messages;
                    intent = new Intent(getApplicationContext(), TimelineActivity.class);
                    intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                            MyDatabase.TimelineTypeEnum.DIRECT.save());
                    contentIntent = PendingIntent.getActivity(getApplicationContext(), numTweets,
                            intent, 0);
                    break;

                case NOTIFY_HOME_TIMELINE:
                default:
                    aMessage = I18n
                            .formatQuantityMessage(getApplicationContext(),
                                    R.string.notification_new_tweet_format, numTweets,
                                    R.array.notification_tweet_patterns,
                                    R.array.notification_tweet_formats);
                    messageTitle = R.string.notification_title;
                    intent = new Intent(getApplicationContext(), TimelineActivity.class);
                    intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                            MyDatabase.TimelineTypeEnum.HOME.save());
                    contentIntent = PendingIntent.getActivity(getApplicationContext(), numTweets,
                            intent, 0);
                    break;
            }

            // Set up the scrolling message of the notification
            notification.tickerText = aMessage;

            // Set the latest event information and send the notification
            notification.setLatestEventInfo(MyService.this, getText(messageTitle), aMessage,
                    contentIntent);
            NotificationManager nM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nM.notify(msgType.ordinal(), notification);
        }

        /**
         * Send Update intent to AndStatus Widget(s), if there are some
         * installed... (e.g. on the Home screen...)
         * 
         * @see MyAppWidgetProvider
         */
        private void updateWidgets(int numTweets, CommandEnum msgType) {
            Intent intent = new Intent(ACTION_APPWIDGET_UPDATE);
            intent.putExtra(IntentExtra.EXTRA_MSGTYPE.key, msgType.save());
            intent.putExtra(IntentExtra.EXTRA_NUMTWEETS.key, numTweets);
            sendBroadcast(intent);
        }

        private void rateLimitStatus(CommandData commandData) {
            if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
                return;
            }
            boolean ok = false;
            try {
                Connection conn = commandData.getAccount().getConnection();
                MbRateLimitStatus rateLimitStatus = conn.rateLimitStatus();
                ok = !rateLimitStatus.isEmpty();
                if (ok) {
                    commandData.commandResult.remaining_hits = rateLimitStatus.remaining; 
                    commandData.commandResult.hourly_limit = rateLimitStatus.limit;
                 }
            } catch (ConnectionException e) {
                logConnectionException(e, commandData, "rateLimitStatus Exception");
            }
            setSoftErrorIfNotOk(commandData, ok);
        }
        
        private void setSoftErrorIfNotOk(CommandData commandData, boolean ok) {
            if (!ok) {
                commandData.commandResult.numIoExceptions++;
            }
        }

        private boolean setErrorIfCredentialsNotVerified(CommandData commandData, MyAccount myAccount) {
            boolean errorOccured = false;
            if (myAccount == null || myAccount.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                errorOccured = true;
                commandData.commandResult.numAuthExceptions++;
            }
            return errorOccured;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
