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

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
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
import org.andstatus.app.util.ForegroundCheckTask;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

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
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * This is an application service that serves as a connection between this Android Device
 * and Microblogging system. Other applications can interact with it via IPC.
 */
public class MyService extends Service {
    private static final String TAG = MyService.class.getSimpleName();

    private static final String packageName = MyService.class.getPackage().getName();

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
     * These names of extras are used in the Intent-notification of new Msg
     * (e.g. to notify Widget).
     */

    /**
     * This extra is used as a command to perform by MyService and
     * MyAppWidgetProvider Value of this extra is string code of
     * CommandEnum (not serialized enum !)
     */
    public static final String EXTRA_MSGTYPE = packageName + ".MSGTYPE";

    /**
     * Command parameter: long - ID of the Tweet (or Msg)
     */
    public static final String EXTRA_ITEMID = packageName + ".ITEMID";

    public static final String EXTRA_COMMAND_RESULT = packageName + ".COMMAND_RESULT";
    
    /**
     * ({@link ServiceState}
     */
    public static final String EXTRA_SERVICE_STATE = packageName + ".SERVICE_STATE";

    /**
     * Text of the status message
     */
    public static final String EXTRA_STATUS = packageName + ".STATUS";

    /**
     * Account name, see {@link MyAccount#getAccountName()}
     */
    public static final String EXTRA_ACCOUNT_NAME = packageName + ".ACCOUNT_NAME";

    /**
     * Do we need to show the account?
     */
    public static final String EXTRA_SHOW_ACCOUNT = packageName + ".SHOW_ACCOUNT";
    
    /**
     * Name of the preference to set
     */
    public static final String EXTRA_PREFERENCE_KEY = packageName + ".PREFERENCE_KEY";

    public static final String EXTRA_PREFERENCE_VALUE = packageName + ".PREFERENCE_VALUE";

    /**
     * Reply to
     */
    public static final String EXTRA_INREPLYTOID = packageName + ".INREPLYTOID";

    /**
     * Recipient of a Direct message
     */
    public static final String EXTRA_RECIPIENTID = packageName + ".RECIPIENTID";

    /**
     * Selected User. E.g. the User whose messages we are seeing  
     */
    public static final String EXTRA_SELECTEDUSERID = packageName + ".SELECTEDUSERID";
    
    /**
     * Number of new tweets. Value is integer
     */
    public static final String EXTRA_NUMTWEETS = packageName + ".NUMTWEETS";

    /**
     * This extra is used to determine which timeline to show in
     * TimelineActivity Value is {@link MyDatabase.TimelineTypeEnum} 
     */
    public static final String EXTRA_TIMELINE_TYPE = packageName + ".TIMELINE_TYPE";

    /**
     * Is the timeline combined in {@link TimelineActivity} 
     */
    public static final String EXTRA_TIMELINE_IS_COMBINED = packageName + ".TIMELINE_IS_COMBINED";

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
        if (mInitialized) {
            if (mIsStopping) {
                state = ServiceState.STOPPING;
            } else {
                state = ServiceState.RUNNING;
            }
        }
        return state;
    }
    
    /**
     * The command to the MyService or to MyAppWidgetProvider as a
     * enum We use 'code' for persistence
     * 
     * @author yvolk
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
         * Boot completed, do what is needed...
         */
        BOOT_COMPLETED("boot-completed"),

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

    private boolean mNotificationsEnabled;

    private boolean mNotificationsVibrate;

    /**
     * We are going to finish this service. The flag is being checked by background threads
     */
    private volatile boolean mIsStopping = false;
    /**
     * Flag to control the Service state persistence
     */
    private volatile boolean mInitialized = false;

    /**
     * Commands queue to be processed by the Service
     */
    private Queue<CommandData> mCommands = new ArrayBlockingQueue<CommandData>(100, true);

    /**
     * Retry Commands queue
     */
    private Queue<CommandData> mRetryQueue = new ArrayBlockingQueue<CommandData>(100, true);

    /**
     * The set of threads that are currently executing commands For now let's
     * have only ONE working thread (it seems there is some problem in parallel
     * execution...)
     */
    private Set<CommandExecutor> mExecutors = new HashSet<CommandExecutor>();

    /**
     * The reference to the wake lock used to keep the CPU from stopping during
     * background operations.
     */
    private volatile PowerManager.WakeLock mWakeLock = null;

    /**
     * Time when shared preferences where changed as this knows it.
     */
    protected long preferencesChangeTime = 0;
    /**
     * Time when shared preferences where analyzed
     */
    protected long preferencesExamineTime = 0;
    
    /**
     * @return Single instance of Default SharedPreferences is returned, this is why we
     *         may synchronize on the object
     */
    private SharedPreferences getSp() {
        return MyPreferences.getDefaultSharedPreferences();
    }

    /**
     * The idea is to have SharePreferences, that are being edited by
     * the service process only (to avoid problems of concurrent access.
     * @return Single instance of SharedPreferences, specific to the class
     */
    private SharedPreferences getMyServicePreferences() {
        return MyPreferences.getSharedPreferences(TAG, MODE_PRIVATE);
    }

    /**
     * After this the service state remains "STOPPED": we didn't initialize the instance yet!
     */
    @Override
    public void onCreate() {
        preferencesChangeTime = MyPreferences.initialize(this, this);
        preferencesExamineTime = getMyServicePreferences().getLong(MyPreferences.KEY_PREFERENCES_EXAMINE_TIME, 0);
        MyLog.d(TAG, "Service created, preferencesChangeTime=" + preferencesChangeTime + ", examined=" + preferencesExamineTime);

    }

    private BroadcastReceiver intentReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context arg0, Intent intent) {
            MyLog.v(TAG, "onReceive " + intent.toString());
            receiveCommand(intent);
        }

    };
    
    /**
     * Notify background processes that the service is stopping.
     * Stop if background processes has finished.
     * Persist everything that we'll need on next Service creation and free resources
     */
    private synchronized void stopDelayed() {
        if (!mInitialized) return;

        mIsStopping = true;
        boolean doStop = (mExecutors.size() == 0);
        if (!doStop) {
            broadcastState(null);
            return;
        }

        unregisterReceiver(intentReceiver);

        // Clear notifications if any
        notifyOfQueue(true);
        
        int count = 0;
        // Save Queues
        count += persistQueue(mCommands, TAG + "_" + "mCommands");
        count += persistQueue(mRetryQueue, TAG + "_" + "mRetryQueue");
        MyLog.d(TAG, "State saved, " + (count>0 ? Integer.toString(count) : "no ") + " msg in the Queues");
        
        stopSelf();
        relealeWakeLock();

        mInitialized = false;
        mIsStopping = false;
        
        broadcastState(null);
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
        intent.putExtra(EXTRA_SERVICE_STATE, state.save());
        context.sendBroadcast(intent);
        MyLog.v(TAG, "state: " + state);
    }
    
    @Override
    public void onDestroy() {
        stopDelayed();
        MyLog.d(TAG, "Service destroyed");
    }

    private int persistQueue(Queue<CommandData> q, String prefsFileName) {
        Context context = MyPreferences.getContext();
        int count = 0;
        // Delete any existing saved queue
        SharedPreferencesUtil.delete(context, prefsFileName);
        if (q.size() > 0) {
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName, MODE_PRIVATE);
            while (q.size() > 0) {
                CommandData cd = q.poll();
                cd.save(sp, count);
                MyLog.v(TAG, "Command saved: " + cd.toString());
                count += 1;
            }
            MyLog.d(TAG, "Queue saved to " + prefsFileName  + ", " + count + " msgs");
        }
        return count;
    }
    
    /**
     * Initialize and restore the state if it was not restored yet
     */
    private synchronized void initialize() {

        // Check when preferences were changed
        long preferencesChangeTimeNew = MyPreferences.initialize(this, this);
        long preferencesExamineTimeNew = java.lang.System.currentTimeMillis();


        if (!mInitialized) {
            int count = 0;
            // Restore Queues
            count += restoreQueue(mCommands, TAG + "_" + "mCommands");
            count += restoreQueue(mRetryQueue, TAG + "_" + "mRetryQueue");
            MyLog.d(TAG, "State restored, " + (count>0 ? Integer.toString(count) : "no") + " msg in the Queues");

            registerReceiver(intentReceiver, new IntentFilter(ACTION_GO));
            
            mInitialized = true;
            broadcastState(null);
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
                Log.e(TAG, "Preferences change time error, time=" + preferencesChangeTimeNew);
            }
            preferencesChangeTime = preferencesChangeTimeNew;
            preferencesExamineTime = preferencesExamineTimeNew;
            getMyServicePreferences().edit().putLong(MyPreferences.KEY_PREFERENCES_EXAMINE_TIME, preferencesExamineTime).commit();
        }
    }

    private int restoreQueue(Queue<CommandData> q, String prefsFileName) {
        Context context = MyPreferences.getContext();
        int count = 0;
        if (SharedPreferencesUtil.exists(context, prefsFileName)) {
            boolean done = false;
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName, MODE_PRIVATE);
            do {
                CommandData cd = new CommandData(sp, count);
                if (cd.command == CommandEnum.UNKNOWN) {
                    done = true;
                } else {
                    if ( q.offer(cd) ) {
                        MyLog.v(TAG, "Command restored: " + cd.toString());
                        count += 1;
                    } else {
                        Log.e(TAG, "Error restoring queue, command: " + cd.toString());
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
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.d(TAG, "onStartCommand: startid=" + startId);
        receiveCommand(intent);
        return START_NOT_STICKY;
    }

    /**
     * Put Intent to the Command's queue and Start Execution thread if none is
     * already running
     * 
     * @param Intent containing command and it's parameters. It may be null to
     *            initialize execution only.
     */
    private synchronized void receiveCommand(Intent intent) {
        
        CommandData commandData = null;
        if (intent != null) {
            commandData = new CommandData(intent);
            switch (commandData.command) {
                case STOP_SERVICE:
                    // Try to stop immediately
                    stopDelayed();
                    return;
                case BROADCAST_SERVICE_STATE:
                    broadcastState(commandData);
                    return;
                case BOOT_COMPLETED:
                    // Force reexamining preferences
                    preferencesExamineTime = 0;
                default:
                    break;
            }
        }
        
        initialize();
        
        if (mCommands.isEmpty()) {
            // This is a good place to send commands from retry Queue
            while (!mRetryQueue.isEmpty()) {
                CommandData cd = mRetryQueue.poll();
                if (!mCommands.contains(cd)) {
                    if (!mCommands.offer(cd)) {
                        Log.e(TAG, "mCommands is full?");
                    }
                }
            }
        }

        if (processCommandImmediately(commandData)) {
            // Don't add to the queue
        } else if (mCommands.contains(commandData)) {
            MyLog.d(TAG, "Duplicated " + commandData);
            // Reset retries counter on receiving duplicated command
            for (CommandData cd:mCommands) {
                if (cd.equals(commandData)) {
                    cd.retriesLeft = 0;
                    break;
                }
            }
        } else {
            MyLog.d(TAG, "Adding to the queue " + commandData);
            if (!mCommands.offer(commandData)) {
                Log.e(TAG, "mCommands is full?");
            }
        }

        // Start Executor if necessary
        startOrStopExecutor(true, null);
    }

    /**
     * @param commandData may be null
     * @return true if the command was processed (either successfully or not...)
     */
    private boolean processCommandImmediately(CommandData commandData) {
        boolean processed = false;
        // Processed successfully?
        boolean ok = true;
        boolean skipped = false;

        /**
         * Flag for debugging. It looks like for now we don't need to edit
         * SharedPreferences from this part of code
         */
        boolean putPreferences = false;

        processed = (commandData == null);
        if (!processed) {
            processed = true;
            switch (commandData.command) {
                case UNKNOWN:
                case EMPTY:
                case BOOT_COMPLETED:
                    // Nothing to do
                    break;

                // TODO: Do we really need these three commands?
                case PUT_BOOLEAN_PREFERENCE:
                    if (!putPreferences) {
                        skipped = true;
                        break;
                    }
                    String key = commandData.bundle.getString(EXTRA_PREFERENCE_KEY);
                    boolean boolValue = commandData.bundle.getBoolean(EXTRA_PREFERENCE_VALUE);
                    MyLog.v(TAG, "Put boolean Preference '"
                            + key
                            + "'="
                            + boolValue
                            + ((commandData.getAccount() != null) ? " account='"
                                    + commandData.getAccount().getAccountName() + "'" : " global"));
                    SharedPreferences sp = null;
                    if (commandData.getAccount() != null) {
                        sp = commandData.getAccount().getAccountPreferences();
                    } else {
                        sp = getSp();
                    }
                    synchronized (sp) {
                        sp.edit().putBoolean(key, boolValue).commit();
                    }
                    break;
                case PUT_LONG_PREFERENCE:
                    if (!putPreferences) {
                        skipped = true;
                        break;
                    }
                    key = commandData.bundle.getString(EXTRA_PREFERENCE_KEY);
                    long longValue = commandData.bundle.getLong(EXTRA_PREFERENCE_VALUE);
                    MyLog.v(TAG, "Put long Preference '"
                            + key
                            + "'="
                            + longValue
                            + ((commandData.getAccount() != null) ? " account='"
                                    + commandData.getAccount().getAccountName() + "'" : " global"));
                    if (commandData.getAccount() != null) {
                        sp = commandData.getAccount().getAccountPreferences();
                    } else {
                        sp = getSp();
                    }
                    synchronized (sp) {
                        sp.edit().putLong(key, longValue).commit();
                    }
                    break;
                case PUT_STRING_PREFERENCE:
                    if (!putPreferences) {
                        skipped = true;
                        break;
                    }
                    key = commandData.bundle.getString(EXTRA_PREFERENCE_KEY);
                    String stringValue = commandData.bundle.getString(EXTRA_PREFERENCE_VALUE);
                    MyLog.v(TAG, "Put String Preference '"
                            + key
                            + "'="
                            + stringValue
                            + ((commandData.getAccount() != null) ? " account='"
                                    + commandData.getAccount().getAccountName() + "'" : " global"));
                    if (commandData.getAccount() != null) {
                        sp = commandData.getAccount().getAccountPreferences();
                    } else {
                        sp = getSp();
                    }
                    synchronized (sp) {
                        sp.edit().putString(key, stringValue).commit();
                    }
                    break;

                default:
                    processed = false;
                    break;
            }
            if (processed) {
                MyLog.d(TAG, (skipped ? "Skipped" : (ok ? "Succeeded" : "Failed")) + " "
                        + commandData);
            }
        }
        return processed;
    }

    /**
     * Start Execution thread if none is already running or stop execution
     * 
     * @param start true: start, false: stop
     * @param executor - existing executor or null (if starting new executor)
     * @param logMsg a log message to include for debugging
     */
    private synchronized void startOrStopExecutor(boolean start, CommandExecutor executorIn) {
        if (start) {
            start = !mIsStopping;
        }
        if (start) {
            SharedPreferences sp = getSp();
            mNotificationsEnabled = sp.getBoolean("notifications_enabled", false);
            mNotificationsVibrate = sp.getBoolean("vibration", false);
            sp = null;

            if (!mCommands.isEmpty()) {
                // Don't even launch executor if we're not online
                if (isOnline() && MyPreferences.isDataAvailable()) {
                    acquireWakeLock();
                    // only one Executing thread for now...
                    if (mExecutors.isEmpty()) {
                        CommandExecutor executor;
                        if (executorIn != null) {
                            executor = executorIn;
                        } else {
                            executor = new CommandExecutor();
                        }
                        mExecutors.add(executor);
                        executor.execute();
                    }
                } else {
                    notifyOfQueue(false);
                }
            }
        } else {
            // Stop
            mExecutors.remove(executorIn);
            if (mExecutors.size() == 0) {
                relealeWakeLock();
                if (mIsStopping) {
                    stopDelayed();
                } else if ( notifyOfQueue(false) == 0) {
                    if (! ForegroundCheckTask.isAppOnForeground(MyPreferences.getContext())) {
                        MyLog.d(TAG, "App is on Background so stop this Service");
                        stopDelayed();
                    }
                }
            }
        }
    }

    /**
     * Notify user of the commands Queue size
     * 
     * @return total size of Queues
     */
    private int notifyOfQueue(boolean clearNotification) {
        int count = mRetryQueue.size() + mCommands.size();
        NotificationManager nM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (count == 0 || clearNotification) {
            // Clear notification
            nM.cancel(CommandEnum.NOTIFY_QUEUE.ordinal());
        } else if (mNotificationsEnabled && getSp().getBoolean(MyPreferences.KEY_NOTIFICATIONS_QUEUE, false)) {
            if (mRetryQueue.size() > 0) {
                MyLog.d(TAG, mRetryQueue.size() + " commands in Retry Queue.");
            }
            if (mCommands.size() > 0) {
                MyLog.d(TAG, mCommands.size() + " commands in Main Queue.");
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
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, CommandData.EMPTY_COMMAND.toIntent(), 0);
            notification.setLatestEventInfo(this, getText(messageTitle), aMessage, pi);
            nM.notify(CommandEnum.NOTIFY_QUEUE.ordinal(), notification);
        }
        return count;
    }

    private class CommandExecutor extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... arg0) {
            MyLog.d(TAG, "CommandExecutor, " + mCommands.size() + " commands to process");

            do {
                if (mIsStopping) break;
                
                // Get commands from the Queue one by one and execute them
                // The queue is Blocking, so we can do this
                CommandData commandData = mCommands.poll();
                if (commandData == null) {
                    break;
                }
                executeOneCommand(commandData);
                if (shouldWeRetry(commandData)) {
                    synchronized(MyService.this) {
                        // Put the command to the retry queue
                        if (!mRetryQueue.contains(commandData)) {
                            if (!mRetryQueue.offer(commandData)) {
                                Log.e(TAG, "mRetryQueue is full?");
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
                    String status = commandData.bundle.getString(EXTRA_STATUS).trim();
                    long replyToId = commandData.bundle.getLong(EXTRA_INREPLYTOID);
                    long recipientId = commandData.bundle.getLong(EXTRA_RECIPIENTID);
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
                    Log.e(TAG, "Unexpected command here " + commandData);
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
            startOrStopExecutor(false, this);
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
                    Log.e(TAG,
                            (create ? "create" : "destroy") + "Favorite Connection Exception: "
                                    + e.toString());
                }
            } else {
                Log.e(TAG,
                        (create ? "create" : "destroy") + "Favorite; msgId not found: " + msgId);
            }
            if (ok) {
                if (SharedPreferencesUtil.isTrue(message.favoritedByReader) != create) {
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
                        message.favoritedByReader = create;

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

                        Log.e(TAG,
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
                    Log.e(TAG,
                            (follow ? "Follow" : "Stop following") + " User Connection Exception: "
                                    + e.toString());
                }
            } else {
                Log.e(TAG,
                        (follow ? "Follow" : "Stop following") + " User; userId not found: " + userId);
            }
            if (ok) {
                if (SharedPreferencesUtil.isTrue(user.followedByReader) != follow) {
                    if (follow) {
                        // Act just like for creating favorite...
                        user.followedByReader = true;

                        MyLog.d(TAG,
                                (follow ? "Follow" : "Stop following") + " User. 'following' flag didn't change yet.");

                        // Let's try to assume that everything was
                        // Ok:
                        ok = true;
                    } else {
                        ok = false;

                        Log.e(TAG,
                                (follow ? "Follow" : "Stop following") + " User. 'following' flag didn't change yet.");
                    }
                }
                if (ok) {
                    new DataInserter(ma,
                            MyService.this.getApplicationContext(),
                            TimelineTypeEnum.HOME).insertOrUpdateUser(user);
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
                    Log.e(TAG, "destroyStatus Connection Exception: " + e.toString());
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
                    Log.e(TAG, "Error destroying status locally: " + e.toString());
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
                    Log.e(TAG, "destroyStatus Connection Exception: " + e.toString());
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
                    Log.e(TAG, "Error destroying reblog locally: " + e.toString());
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
                MbMessage message = commandData.getAccount().getConnection().getStatus(oid);
                if (!message.isEmpty()) {
                    // And add the message to the local storage
                    try {
                        new DataInserter(commandData.getAccount(),
                                MyService.this.getApplicationContext(),
                                TimelineTypeEnum.ALL).insertOrUpdateMsg(message);
                        ok = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error inserting status: " + e.toString());
                    }
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                    commandData.commandResult.numParseExceptions++;
                    // This means that there is no such "Status"
                    // TODO: so we don't need to retry this command
                }
                Log.e(TAG, "getStatus Connection Exception: " + e.toString());
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
                Log.e(TAG, "updateStatus Exception: " + e.toString());
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
            MyAccount ma = commandData.getAccount();
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, rebloggedId, 0);
            boolean ok = false;
            MbMessage result = null;
            try {
                result = ma.getConnection()
                        .postReblog(oid);
                ok = !result.isEmpty();
            } catch (ConnectionException e) {
                Log.e(TAG, "reblog Exception: " + e.toString());
            }
            if (ok) {
                // The tweet was sent successfully
                // Reblog should be put into the user's Home timeline!
                new DataInserter(ma, 
                        MyService.this.getApplicationContext(),
                        TimelineTypeEnum.HOME).insertOrUpdateMsg(result);
            }
            setSoftErrorIfNotOk(commandData, ok);
        }
        
        /**
         * Load one or all timeline(s) for one or all accounts
         * @return True if everything Succeeded
         */
        private void loadTimeline(CommandData commandData) {
            if (commandData.getAccount() == null) {
                for (MyAccount acc : MyAccount.list()) {
                    loadTimelineAccount(commandData, acc);
                    if (mIsStopping) {
                        setSoftErrorIfNotOk(commandData, false);
                        break;
                    }
                }
            } else {
                loadTimelineAccount(commandData, commandData.getAccount());
            }
            if (!commandData.commandResult.hasError() && commandData.timelineType == TimelineTypeEnum.ALL && !mIsStopping) {
                new DataPruner(MyService.this.getApplicationContext()).prune();
            }
            if (!commandData.commandResult.hasError()) {
                // Notify all timelines, 
                // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
                MyPreferences.getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
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
            int downloadedCount = 0;
            int msgAdded = 0;
            int mentionsAdded = 0;
            int directedAdded = 0;
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
                    if (mIsStopping) {
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
                    ok = false;

                    TimelineTypeEnum timelineType = atl[ind];
                    MyLog.d(TAG, "Getting " + timelineType.save() + " for "
                            + acc.getAccountName());

                    TimelineDownloader fl = null;
                    descr = "loading " + timelineType.save();
                    fl = new TimelineDownloader(acc,
                            MyService.this.getApplicationContext(),
                            timelineType, userId);
                    ok = fl.loadTimeline();
                    downloadedCount += fl.downloadedCount();
                    switch (timelineType) {
                        case MENTIONS:
                            mentionsAdded += fl.mentionsCount();
                            break;
                        case HOME:
                            msgAdded += fl.messagesCount();
                            mentionsAdded += fl.mentionsCount();
                            break;
                        case DIRECT:
                            directedAdded += fl.messagesCount();
                            break;
                        case FOLLOWING_USER:
                        case USER:
                            // Don't count anything for now...
                            break;
                        default:
                            ok = false;
                            Log.e(TAG, descr + " - not implemented");
                    }

                    if (ok) {
                        okSomething = true;
                    } else {
                        notOkSomething = true;
                    }
                    oKs[ind] = ok;
                }
            } catch (ConnectionException e) {
                Log.e(TAG, descr + ", Connection Exception: " + e.toString());
                ok = false;
            } catch (SQLiteConstraintException e) {
                Log.e(TAG, descr + ", SQLite Exception: " + e.toString());
                ok = false;
            }

            if (ok) {
                descr = "notifying";
                notifyOfUpdatedTimeline(msgAdded, mentionsAdded, directedAdded);
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
                    + " for " + acc.getAccountName();
            if (downloadedCount > 0) {
                message += ", " + downloadedCount + " downloaded";
            }
            if (msgAdded > 0) {
                message += ", " + msgAdded + " messages";
            }
            if (mentionsAdded > 0) {
                message += ", " + mentionsAdded + " mentions";
            }
            if (directedAdded > 0) {
                message += ", " + directedAdded + " directs";
            }
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

            boolean notificationsMessages = false;
            boolean notificationsReplies = false;
            boolean notificationsTimeline = false;
            String ringtone = null;
            SharedPreferences sp = getSp();
            synchronized (sp) {
                notificationsMessages = sp.getBoolean("notifications_messages", false);
                notificationsReplies = sp.getBoolean("notifications_mentions", false);
                notificationsTimeline = sp.getBoolean("notifications_timeline", false);
                ringtone = sp.getString(MyPreferences.KEY_RINGTONE_PREFERENCE, null);
            }
            sp = null;

            // Make sure that notifications haven't been turned off for the
            // message
            // type
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
                    intent.putExtra(MyService.EXTRA_TIMELINE_TYPE,
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
                    intent.putExtra(MyService.EXTRA_TIMELINE_TYPE,
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
                    intent.putExtra(MyService.EXTRA_TIMELINE_TYPE,
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
            intent.putExtra(EXTRA_MSGTYPE, msgType.save());
            intent.putExtra(EXTRA_NUMTWEETS, numTweets);
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
                Log.e(TAG, "rateLimitStatus Exception: " + e.toString());
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

    private synchronized void acquireWakeLock() {
        if (mWakeLock == null) {
            MyLog.d(TAG, "Acquiring wakelock");
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }
    }
    
    private synchronized void relealeWakeLock() {
        if (mWakeLock != null) {
            MyLog.d(TAG, "Releasing wakelock");
            mWakeLock.release();
            mWakeLock = null;
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
                MyLog.v(TAG, "Internet Connection Not Present");
            }
        } catch (Exception e) {}
        return is;
    }
}
