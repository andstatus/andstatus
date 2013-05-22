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
import org.andstatus.app.account.MyAccount.CredentialsVerified;
import org.andstatus.app.appwidget.MyAppWidgetProvider;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.ForegroundCheckTask;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
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
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
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
     * Repeating alarm was triggered.
     * @see MyService#scheduleRepeatingAlarm()
     */
    public static final String ACTION_ALARM = ACTIONPREFIX + "ALARM";

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
    public static final String EXTRA_TWEETID = packageName + ".TWEETID";

    /**
     * ({@link ServiceState}
     */
    public static final String EXTRA_SERVICE_STATE = packageName + ".SERVICE_STATE";

    /**
     * Text of the status message
     */
    public static final String EXTRA_STATUS = packageName + ".STATUS";

    /**
     * Account name, see {@link MyAccount#getAccountGuid()}
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
         * The action is being sent by recurring alarm to fetch the tweets,
         * replies and other information in the background.
         * TODO: Plus for all Accounts 
         */
        AUTOMATIC_UPDATE("automatic-update"),
        /**
         * Fetch all timelines for current MyAccount 
         * (this is generally done after addition of the new MyAccount)
         */
        FETCH_ALL_TIMELINES("fetch-all-timelines"),
        /**
         * Fetch the Home timeline and other information (replies...).
         */
        FETCH_HOME("fetch-home"),
        /**
         * Fetch the Mentions timeline and other information (replies...).
         */
        FETCH_MENTIONS("fetch-mention"),
        /**
         * Fetch Direct messages
         */
        FETCH_DIRECT_MESSAGES("fetch-dm"),
        /**
         * Fetch a User timeline (messages by the selected user)
         */
        FETCH_USER_TIMELINE("fetch-user-timeline"),
        /**
         * The recurring alarm that is used to implement recurring tweet
         * downloads should be started.
         */
        START_ALARM("start-alarm"),
        /**
         * The recurring alarm that is used to implement recurring tweet
         * downloads should be stopped.
         */
        STOP_ALARM("stop-alarm"),
        /**
         * The recurring alarm that is used to implement recurring tweet
         * downloads should be restarted.
         */
        RESTART_ALARM("restart-alarm"),

        CREATE_FAVORITE("create-favorite"), DESTROY_FAVORITE("destroy-favorite"),

        FOLLOW_USER("follow-user"), STOP_FOLLOWING_USER("stop-following-user"),

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
     * Command data store (message...)
     * 
     * @author yvolk
     */
    public static class CommandData {
        public CommandEnum command;

        /**
         * Unique name of {@link MyAccount} for this command. Empty string if command is not Account specific 
         * (e.g. {@link CommandEnum#AUTOMATIC_UPDATE} which works for all accounts) 
         */
        public String accountName = "";
        
        /**
         * This is: 
         * 1. Generally: Message ID ({@link MyDatabase.Msg#MSG_ID} of the {@link MyDatabase.Msg}).
         * 2. User ID ( {@link MyDatabase.User#USER_ID} ) for the {@link CommandEnum#FETCH_USER_TIMELINE}, 
         *      {@link CommandEnum#FOLLOW_USER}, {@link CommandEnum#STOP_FOLLOWING_USER} 
         */
        public long itemId = 0;

        /**
         * Other command parameters
         */
        public Bundle bundle = new Bundle();

        private int hashcode = 0;

        /**
         * Number of retries left
         */
        public int retriesLeft = 0;

        public CommandData(CommandEnum commandIn, String accountNameIn) {
            command = commandIn;
            if (!TextUtils.isEmpty(accountNameIn)) {
                accountName = accountNameIn;
            }
        }

        public CommandData(CommandEnum commandIn, String accountNameIn, long itemIdIn) {
            this(commandIn, accountNameIn);
            itemId = itemIdIn;
        }

        /**
         * Initialize command to put boolean SharedPreference
         * 
         * @param preferenceKey
         * @param value
         * @param accountNameIn - preferences for this user, or null if Global
         *            preferences
         */
        public CommandData(String accountNameIn, String preferenceKey, boolean value) {
            this(CommandEnum.PUT_BOOLEAN_PREFERENCE, accountNameIn);
            bundle.putString(EXTRA_PREFERENCE_KEY, preferenceKey);
            bundle.putBoolean(EXTRA_PREFERENCE_VALUE, value);
        }

        /**
         * Initialize command to put long SharedPreference
         * 
         * @param accountNameIn - preferences for this user, or null if Global
         *            preferences
         * @param preferenceKey
         * @param value
         */
        public CommandData(String accountNameIn, String preferenceKey, long value) {
            this(CommandEnum.PUT_LONG_PREFERENCE, accountNameIn);
            bundle.putString(EXTRA_PREFERENCE_KEY, preferenceKey);
            bundle.putLong(EXTRA_PREFERENCE_VALUE, value);
        }

        /**
         * Initialize command to put string SharedPreference
         * 
         * @param accountNameIn - preferences for this user
         * @param preferenceKey
         * @param value
         */
        public CommandData(String accountNameIn, String preferenceKey, String value) {
            this(CommandEnum.PUT_STRING_PREFERENCE, accountNameIn);
            bundle.putString(EXTRA_PREFERENCE_KEY, preferenceKey);
            bundle.putString(EXTRA_PREFERENCE_VALUE, value);
        }

        /**
         * Used to decode command from the Intent upon receiving it
         * 
         * @param intent
         */
        public CommandData(Intent intent) {
            bundle = intent.getExtras();
            // Decode command
            String strCommand = "(no command)";
            if (bundle != null) {
                strCommand = bundle.getString(EXTRA_MSGTYPE);
                accountName = bundle.getString(EXTRA_ACCOUNT_NAME);
                itemId = bundle.getLong(EXTRA_TWEETID);
            }
            command = CommandEnum.load(strCommand);
        }

        /**
         * Restore this from the SharedPreferences 
         * @param sp
         * @param index Index of the preference's name to be used
         */
        public CommandData(SharedPreferences sp, int index) {
            bundle = new Bundle();
            String si = Integer.toString(index);
            // Decode command
            String strCommand = sp.getString(EXTRA_MSGTYPE + si, CommandEnum.UNKNOWN.save());
            accountName = sp.getString(EXTRA_ACCOUNT_NAME + si, "");
            itemId = sp.getLong(EXTRA_TWEETID + si, 0);
            command = CommandEnum.load(strCommand);

            switch (command) {
                case UPDATE_STATUS:
                    bundle.putString(EXTRA_STATUS, sp.getString(EXTRA_STATUS + si, ""));
                    bundle.putLong(EXTRA_INREPLYTOID, sp.getLong(EXTRA_INREPLYTOID + si, 0));
                    bundle.putLong(EXTRA_RECIPIENTID, sp.getLong(EXTRA_RECIPIENTID + si, 0));
                    break;
            }

            MyLog.v(TAG, "Restored command " + (EXTRA_MSGTYPE + si) + " = " + strCommand);
        }
        
        /**
         * It's used in equals() method We need to distinguish duplicated
         * commands
         */
        @Override
        public int hashCode() {
            if (hashcode == 0) {
                String text = Long.toString(command.ordinal());
                if (!TextUtils.isEmpty(accountName)) {
                    text += accountName;
                }
                if (itemId != 0) {
                    text += Long.toString(itemId);
                }
                switch (command) {
                    case UPDATE_STATUS:
                        text += bundle.getString(EXTRA_STATUS);
                        break;
                    case PUT_BOOLEAN_PREFERENCE:
                        text += bundle.getString(EXTRA_PREFERENCE_KEY)
                                + bundle.getBoolean(EXTRA_PREFERENCE_VALUE);
                        break;
                    case PUT_LONG_PREFERENCE:
                        text += bundle.getString(EXTRA_PREFERENCE_KEY)
                                + bundle.getLong(EXTRA_PREFERENCE_VALUE);
                        break;
                    case PUT_STRING_PREFERENCE:
                        text += bundle.getString(EXTRA_PREFERENCE_KEY)
                                + bundle.getString(EXTRA_PREFERENCE_VALUE);
                        break;
                }
                hashcode = text.hashCode();
            }
            return hashcode;
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "CommandData [" + "command=" + command.save()
                    + (TextUtils.isEmpty(accountName) ? "" : "; account=" + accountName)
                    + (itemId == 0 ? "" : "; id=" + itemId) + ", hashCode=" + hashCode() + "]";
        }

        /**
         * @return Intent to be sent to this.AndStatusService
         */
        public Intent toIntent() {
            return toIntent(null);
        }

        /**
         * @return Intent to be sent to this.AndStatusService
         */
        public Intent toIntent(Intent intent_in) {
            Intent intent = intent_in;
            if (intent == null) {
                intent = new Intent(MyService.ACTION_GO);
            }
            if (bundle == null) {
                bundle = new Bundle();
            }
            bundle.putString(MyService.EXTRA_MSGTYPE, command.save());
            if (!TextUtils.isEmpty(accountName)) {
                bundle.putString(MyService.EXTRA_ACCOUNT_NAME, accountName);
            }
            if (itemId != 0) {
                bundle.putLong(MyService.EXTRA_TWEETID, itemId);
            }
            intent.putExtras(bundle);
            return intent;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CommandData)) {
                return false;
            }
            CommandData cd = (CommandData) o;
            return (hashCode() == cd.hashCode());
        }

        /**
         * Persist the object to the SharedPreferences 
         * We're not storing all types of commands here because not all commands
         *   go to the queue
         * @param sp
         * @param index Index of the preference's name to be used
         */
        public void save(SharedPreferences sp, int index) {
            String si = Integer.toString(index);

            android.content.SharedPreferences.Editor ed = sp.edit();
            ed.putString(EXTRA_MSGTYPE + si, command.save());
            if (!TextUtils.isEmpty(accountName)) {
                ed.putString(EXTRA_ACCOUNT_NAME + si, accountName);
            }
            if (itemId != 0) {
                ed.putLong(EXTRA_TWEETID + si, itemId);
            }
            switch (command) {
                case UPDATE_STATUS:
                    ed.putString(EXTRA_STATUS + si, bundle.getString(EXTRA_STATUS));
                    ed.putLong(EXTRA_INREPLYTOID + si, bundle.getLong(EXTRA_INREPLYTOID));
                    ed.putLong(EXTRA_RECIPIENTID + si, bundle.getLong(EXTRA_RECIPIENTID));
                    break;
            }
            ed.commit();
        }
    }

    /**
     * This is a list of callbacks that have been registered with the service.
     */
    final RemoteCallbackList<IMyServiceCallback> mCallbacks = new RemoteCallbackList<IMyServiceCallback>();

    static final int MILLISECONDS = 1000;

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
            broadcastState();
            return;
        }

        // Unregister all callbacks.
        mCallbacks.kill();

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
        
        broadcastState();
    }

    /**
     * Send broadcast informing of the current state of this service
     */
    private void broadcastState() {
        Intent intent = new Intent(ACTION_SERVICE_STATE);
        ServiceState state = getServiceState();
        intent.putExtra(EXTRA_SERVICE_STATE, state.save());
        sendBroadcast(intent);
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
            broadcastState();
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

            // Stop existing alarm in any case
            cancelRepeatingAlarm();

            SharedPreferences sp = MyPreferences.getDefaultSharedPreferences();
            if (sp.contains("automatic_updates") && sp.getBoolean("automatic_updates", false)) {
                /**
                 * Schedule Automatic updates according to the preferences.
                 */
                scheduleRepeatingAlarm();
            }
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
        // Select the interface to return. If your service only implements
        // a single interface, you can just return it here without checking
        // the Intent.
        if (IMyService.class.getName().equals(intent.getAction())) {
            return mBinder;
        }
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
                    broadcastState();
                    return;
                case BOOT_COMPLETED:
                    // Force reexamining preferences
                    preferencesExamineTime = 0;
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

        if (commandData != null) {
            if (commandData.command == CommandEnum.UNKNOWN) {
                // Ignore unknown commands

                // Maybe this command may be processed synchronously without
                // Internet connection?
            } else if (processCommandImmediately(commandData)) {
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
        }

        // Start Executor if necessary
        startEndExecutor(true, null);
    }

    /**
     * @param commandData
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

                // TODO: Do we really need these three commands?
                case START_ALARM:
                    ok = scheduleRepeatingAlarm();
                    break;
                case STOP_ALARM:
                    ok = cancelRepeatingAlarm();
                    break;
                case RESTART_ALARM:
                    ok = cancelRepeatingAlarm();
                    ok = scheduleRepeatingAlarm();
                    break;

                case UNKNOWN:
                case EMPTY:
                case BOOT_COMPLETED:
                    // Nothing to do
                    break;

                case PUT_BOOLEAN_PREFERENCE:
                    if (!putPreferences) {
                        skipped = true;
                        break;
                    }
                    String key = commandData.bundle.getString(EXTRA_PREFERENCE_KEY);
                    boolean boolValue = commandData.bundle.getBoolean(EXTRA_PREFERENCE_VALUE);
                    MyLog.v(TAG, "Put boolean Preference '" + key + "'=" + boolValue
                            + ((!TextUtils.isEmpty(commandData.accountName)) ? " account='" + commandData.accountName + "'" : " global"));
                    SharedPreferences sp = null;
                    if (!TextUtils.isEmpty(commandData.accountName)) {
                        sp = MyAccount.getMyAccount(commandData.accountName).getMyAccountPreferences();
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
                    MyLog.v(TAG, "Put long Preference '" + key + "'=" + longValue
                            + ((!TextUtils.isEmpty(commandData.accountName)) ? " account='" + commandData.accountName + "'" : " global"));
                    if (!TextUtils.isEmpty(commandData.accountName)) {
                        sp = MyAccount.getMyAccount(commandData.accountName).getMyAccountPreferences();
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
                    MyLog.v(TAG, "Put String Preference '" + key + "'=" + stringValue
                            + ((!TextUtils.isEmpty(commandData.accountName)) ? " account='" + commandData.accountName + "'" : " global"));
                    if (!TextUtils.isEmpty(commandData.accountName)) {
                        sp = MyAccount.getMyAccount(commandData.accountName).getMyAccountPreferences();
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
                MyLog.d(TAG, (skipped ? "Skipped" : (ok ? "Succeeded" : "Failed")) + " " + commandData);
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
    private synchronized void startEndExecutor(boolean start, CommandExecutor executorIn) {
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
        } else if (mNotificationsEnabled) {
            if (mRetryQueue.size() > 0) {
                MyLog.d(TAG, mRetryQueue.size() + " commands in Retry Queue.");
            }
            if (mCommands.size() > 0) {
                MyLog.d(TAG, mCommands.size() + " commands in Main Queue.");
            }

            // Set up the notification to display to the user
            Notification notification = new Notification(R.drawable.notification_icon,
                    (String) getText(R.string.notification_title), System.currentTimeMillis());

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
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new CommandData(
                    CommandEnum.EMPTY, "").toIntent(), 0);

            notification.setLatestEventInfo(this, getText(messageTitle), aMessage, pi);
            nM.notify(CommandEnum.NOTIFY_QUEUE.ordinal(), notification);
        }
        return count;
    }

    /**
     * Command executor
     * 
     * @author yvolk
     */
    private class CommandExecutor extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... arg0) {
            MyLog.d(TAG, "CommandExecutor, " + mCommands.size() + " commands to process");

            do {
                boolean ok = false;
                if (mIsStopping) break;
                
                // Get commands from the Queue one by one and execute them
                // The queue is Blocking, so we can do this
                CommandData commandData = mCommands.poll();
                if (commandData == null) {
                    // All work is done
                    break;
                }

                commandData.retriesLeft -= 1;
                boolean retry = false;
                MyLog.d(TAG, "Executing " + commandData);

                switch (commandData.command) {
                    case AUTOMATIC_UPDATE:
                    case FETCH_ALL_TIMELINES:
                        ok = loadTimeline(commandData.accountName, MyDatabase.TimelineTypeEnum.ALL, 0);
                        break;
                    case FETCH_HOME:
                        ok = loadTimeline(commandData.accountName, MyDatabase.TimelineTypeEnum.HOME, 0);
                        break;
                    case FETCH_MENTIONS:
                        ok = loadTimeline(commandData.accountName, MyDatabase.TimelineTypeEnum.MENTIONS, 0);
                        break;
                    case FETCH_DIRECT_MESSAGES:
                        ok = loadTimeline(commandData.accountName, MyDatabase.TimelineTypeEnum.DIRECT, 0);
                        break;
                    case FETCH_USER_TIMELINE:
                        ok = loadTimeline(commandData.accountName, MyDatabase.TimelineTypeEnum.USER, commandData.itemId);
                        break;
                    case CREATE_FAVORITE:
                    case DESTROY_FAVORITE:
                        ok = createOrDestroyFavorite(commandData.accountName,
                                commandData.itemId, 
                                commandData.command == CommandEnum.CREATE_FAVORITE);
                        // Retry in a case of an error
                        retry = !ok;
                        break;
                    case FOLLOW_USER:
                    case STOP_FOLLOWING_USER:
                        ok = followOrStopFollowingUser(commandData.accountName,
                                commandData.itemId, 
                                commandData.command == CommandEnum.FOLLOW_USER);
                        // Retry in a case of an error
                        retry = !ok;
                        break;
                    case UPDATE_STATUS:
                        String status = commandData.bundle.getString(EXTRA_STATUS).trim();
                        long replyToId = commandData.bundle.getLong(EXTRA_INREPLYTOID);
                        long recipientId = commandData.bundle.getLong(EXTRA_RECIPIENTID);
                        ok = updateStatus(commandData.accountName, status, replyToId, recipientId);
                        retry = !ok;
                        break;
                    case DESTROY_STATUS:
                        ok = destroyStatus(commandData.accountName, commandData.itemId);
                        // Retry in a case of an error
                        retry = !ok;
                        break;
                    case DESTROY_REBLOG:
                        ok = destroyReblog(commandData.accountName, commandData.itemId);
                        // Retry in a case of an error
                        retry = !ok;
                        break;
                    case GET_STATUS:
                        ok = getStatus(commandData.accountName, commandData.itemId);
                        // Retry in a case of an error
                        retry = !ok;
                        break;
                    case REBLOG:
                        ok = reblog(commandData.accountName, commandData.itemId);
                        retry = !ok;
                        break;
                    case RATE_LIMIT_STATUS:
                        ok = rateLimitStatus(commandData.accountName);
                        break;
                    default:
                        Log.e(TAG, "Unexpected command here " + commandData);
                }
                MyLog.d(TAG, (ok ? "Succeeded" : "Failed") + " " + commandData);
                
                if (retry) {
                    boolean ok2 = true;
                    if (commandData.retriesLeft < 0) {
                        // This means that retriesLeft was not set yet,
                        // so let's set it to some default value, the same for
                        // any command
                        // that needs to be retried...
                        commandData.retriesLeft = 9;
                    }
                    // Check if any retries left (actually 0 means this was the
                    // last retry)
                    if (commandData.retriesLeft > 0) {
                        synchronized(MyService.this) {
                            // Put the command to the retry queue
                            if (!mRetryQueue.contains(commandData)) {
                                if (!mRetryQueue.offer(commandData)) {
                                    Log.e(TAG, "mRetryQueue is full?");
                                }
                            }
                        }        
                    } else {
                        ok2 = false;
                    }
                    if (!ok2) {
                        Log.e(TAG, "Couldn't execute " + commandData);
                    }
                }
                if (!ok && !isOnline()) {
                    // Don't bother with other commands if we're not Online :-)
                    break;
                }
            } while (true);
            return true;
        }

        /**
         * This is in the UI thread, so we can mess with the UI
         */
        protected void onPostExecute(Boolean notUsed) {
            startEndExecutor(false, this);
        }

        /**
         * @param create true - create, false - destroy
         * @param msgId
         * @return ok
         */
        private boolean createOrDestroyFavorite(String accountNameIn, long msgId, boolean create) {
            boolean ok = false;
            MyAccount ma = MyAccount.getMyAccount(accountNameIn);
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
            JSONObject result = new JSONObject();
            if (oid.length() > 0) {
                try {
                    if (create) {
                        result = ma.getConnection().createFavorite(oid);
                    } else {
                        result = ma.getConnection().destroyFavorite(oid);
                    }
                    ok = (result != null);
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
                try {
                    boolean favorited = result.getBoolean("favorited");
                    if (favorited != create) {
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
                            result.put("favorited", create);

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
                } catch (JSONException e) {
                    Log.e(TAG,
                            (create ? "create" : "destroy")
                                    + ". Checking resulted favorited flag: "
                                    + e.toString());
                }

                if (ok) {
                    try {
                        TimelineDownloader fl = new TimelineDownloader(ma,
                                MyService.this.getApplicationContext(),
                                TimelineTypeEnum.HOME);
                        fl.insertMsgFromJSONObject(result, true);
                    } catch (JSONException e) {
                        Log.e(TAG,
                                "Error marking as " + (create ? "" : "not ") + "favorite: "
                                        + e.toString());
                    }
                }
            }

            // TODO: Maybe we need to notify the caller about the result?!

            MyLog.d(TAG, (create ? "Creating" : "Destroying") + " favorite "
                    + (ok ? "succeded" : "failed") + ", id=" + msgId);
            return ok;
        }


        /**
         * @param userId
         * @param follow true - Follow, false - Stop following
         * @return ok
         */
        private boolean followOrStopFollowingUser(String accountNameIn, long userId, boolean follow) {
            boolean ok = false;
            MyAccount ma = MyAccount.getMyAccount(accountNameIn);
            String oid = MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
            JSONObject result = new JSONObject();
            if (oid.length() > 0) {
                try {
                    result = ma.getConnection().followUser(oid, follow);
                    ok = (result != null);
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
                try {
                    boolean following = result.getBoolean("following");
                    if (following != follow) {
                        if (follow) {
                            // Act just like for creating favorite...
                            result.put("following", follow);

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
                } catch (JSONException e) {
                    Log.e(TAG,
                            (follow ? "Follow" : "Stop following") + " User. Checking resulted 'following' flag: "
                                    + e.toString());
                }

                if (ok) {
                    try {
                        TimelineDownloader fl = new TimelineDownloader(ma,
                                MyService.this.getApplicationContext(),
                                TimelineTypeEnum.HOME);
                        fl.insertUserFromJSONObject(result);
                    } catch (JSONException e) {
                        Log.e(TAG,
                                "Error on " + (follow ? "Follow" : "Stop following") + " User: "
                                        + e.toString());
                    }
                }
            }

            // TODO: Maybe we need to notify the caller about the result?!

            MyLog.d(TAG, (follow ? "Follow" : "Stop following") + " User "
                    + (ok ? "succeded" : "failed") + ", id=" + userId);
            return ok;
        }
        
        /**
         * @param accountNameIn Account whose message (status) to destroy
         * @param msgId ID of the message to destroy
         * @return boolean ok
         */
        private boolean destroyStatus(String accountNameIn, long msgId) {
            boolean ok = false;
            MyAccount ma = MyAccount.getMyAccount(accountNameIn);
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
            JSONObject result = new JSONObject();
            try {
                result = ma.getConnection().destroyStatus(oid);
                ok = (result != null);
                if (ok && MyLog.isLoggable(null, Log.VERBOSE)) {
                    Log.v(TAG, "destroyStatus response: " + result.toString(2));
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() == 404) {
                    // This means that there is no such "Status", so we may
                    // assume that it's Ok!
                    ok = true;
                } else {
                    Log.e(TAG, "destroyStatus Connection Exception: " + e.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (ok) {
                // And delete the status from the local storage
                try {
                    TimelineDownloader fl = new TimelineDownloader(ma,
                            MyService.this.getApplicationContext(),
                            TimelineTypeEnum.HOME);
                    fl.destroyStatus(msgId);
                } catch (Exception e) {
                    Log.e(TAG, "Error destroying status locally: " + e.toString());
                }
            }

            // TODO: Maybe we need to notify the caller about the result?!

            MyLog.d(TAG, "Destroying status " + (ok ? "succeded" : "failed") + ", id=" + msgId);
            return ok;
        }


        /**
         * @param accountNameIn Account whose reblog to destroy ("undo reblog")
         * @param msgId ID of the message to destroy
         * @return boolean ok
         */
        private boolean destroyReblog(String accountNameIn, long msgId) {
            boolean ok = false;
            MyAccount ma = MyAccount.getMyAccount(accountNameIn);
            String oid = MyProvider.idToOid(OidEnum.REBLOG_OID, msgId, ma.getUserId());
            JSONObject result = new JSONObject();
            try {
                result = ma.getConnection().destroyStatus(oid);
                ok = (result != null);
                if (ok && MyLog.isLoggable(null, Log.VERBOSE)) {
                    Log.v(TAG, "destroyStatus response: " + result.toString(2));
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() == 404) {
                    // This means that there is no such "Status", so we may
                    // assume that it's Ok!
                    ok = true;
                } else {
                    Log.e(TAG, "destroyStatus Connection Exception: " + e.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (ok) {
                // And delete the status from the local storage
                try {
                    TimelineDownloader fl = new TimelineDownloader(ma,
                            MyService.this.getApplicationContext(),
                            TimelineTypeEnum.HOME);
                    fl.destroyReblog(msgId);
                } catch (Exception e) {
                    Log.e(TAG, "Error destroying reblog locally: " + e.toString());
                }
            }

            MyLog.d(TAG, "Destroying reblog " + (ok ? "succeded" : "failed") + ", id=" + msgId);
            return ok;
        }

        /**
         * @param statusId
         * @return boolean ok
         */
        private boolean getStatus(String accountNameIn, long msgId) {
            boolean ok = false;
            MyAccount ma = MyAccount.getMyAccount(accountNameIn);
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
            JSONObject result = new JSONObject();
            try {
                result = ma.getConnection().getStatus(oid);
                ok = (result != null);
            } catch (ConnectionException e) {
                if (e.getStatusCode() == 404) {
                    // This means that there is no such "Status"
                    // TODO: so we don't need to retry this command
                }
                Log.e(TAG, "getStatus Connection Exception: " + e.toString());
            }

            if (ok) {
                // And add the message to the local storage
                try {
                    TimelineDownloader fl = new TimelineDownloader(ma,
                            MyService.this.getApplicationContext(),
                            TimelineTypeEnum.ALL);
                    fl.insertMsgFromJSONObject(result);
                } catch (Exception e) {
                    Log.e(TAG, "Error inserting status: " + e.toString());
                }
            }
            MyLog.d(TAG, "getStatus " + (ok ? "succeded" : "failed") + ", id=" + msgId);

            notifyOfDataLoadingCompletion();
            return ok;
        }
        
        /**
         * @param status
         * @param replyToMsgId - Message Id
         * @param recipientUserId !=0 for Direct messages - User Id
         * @return ok
         */
        private boolean updateStatus(String accountNameIn, String status, long replyToMsgId, long recipientUserId) {
            boolean ok = false;
            MyAccount ma = MyAccount.getMyAccount(accountNameIn);
            JSONObject result = new JSONObject();
            try {
                if (recipientUserId == 0) {
                    String replyToMsgOid = MyProvider.idToOid(OidEnum.MSG_OID, replyToMsgId, 0);
                    result = ma.getConnection()
                            .updateStatus(status.trim(), replyToMsgOid);
                } else {
                    String recipientOid = MyProvider.idToOid(OidEnum.USER_OID, recipientUserId, 0);
                    // Currently we don't use Screen Name, I guess id is enough.
                    result = ma.getConnection()
                            .postDirectMessage(status.trim(), recipientOid);
                }
                ok = (result != null);
            } catch (ConnectionException e) {
                Log.e(TAG, "updateStatus Exception: " + e.toString());
            }
            if (ok) {
                try {
                    // The tweet was sent successfully
                    TimelineDownloader fl = new TimelineDownloader(ma, 
                            MyService.this.getApplicationContext(),
                            (recipientUserId == 0) ? TimelineTypeEnum.HOME : TimelineTypeEnum.DIRECT);

                    fl.insertMsgFromJSONObject(result, true);
                } catch (JSONException e) {
                    Log.e(TAG, "updateStatus JSONException: " + e.toString());
                }
            }
            return ok;
        }

        private boolean reblog(String accountNameIn, long rebloggedId) {
            MyAccount ma = MyAccount.getMyAccount(accountNameIn);
            String oid = MyProvider.idToOid(OidEnum.MSG_OID, rebloggedId, 0);
            boolean ok = false;
            JSONObject result = new JSONObject();
            try {
                result = ma.getConnection()
                        .postReblog(oid);
                ok = (result != null);
            } catch (ConnectionException e) {
                Log.e(TAG, "reblog Exception: " + e.toString());
            }
            if (ok) {
                try {
                    // The tweet was sent successfully
                    TimelineDownloader fl = new TimelineDownloader(ma, 
                            MyService.this.getApplicationContext(),
                            TimelineTypeEnum.HOME);

                    fl.insertMsgFromJSONObject(result, true);
                } catch (JSONException e) {
                    Log.e(TAG, "reblog JSONException: " + e.toString());
                }
            }
            return ok;
        }
        
        /**
         * @param accountNameIn If empty load the Timeline for all MyAccounts
         * @param loadHomeAndMentions - Should we load Home and Mentions
         * @param loadDirectMessages - Should we load direct messages
         * @return True if everything Succeeded
         */
        private boolean loadTimeline(String accountNameIn,
                MyDatabase.TimelineTypeEnum timelineType_in, long userId) {
            boolean okAllAccounts = true;
            
            if (TextUtils.isEmpty(accountNameIn)) {
                // Cycle for all accounts
                for (int ind=0; ind < MyAccount.list().length; ind++) {
                    MyAccount acc = MyAccount.list()[ind];
                    if (acc.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                        // Only if User was authenticated already
                        boolean ok = loadTimelineAccount(acc, timelineType_in, userId);
                        if (!ok) {
                            okAllAccounts = false;
                        }
                    }
                }
            } else {
                MyAccount acc = MyAccount.getMyAccount(accountNameIn);
                if (acc.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                    // Only if User was authenticated already
                    boolean ok = loadTimelineAccount(acc, timelineType_in, userId);
                    if (!ok) {
                        okAllAccounts = false;
                    }
                }
                // Notify only when data was loaded for one account
                // (presumably "Manual reload" from the Timeline)
                notifyOfDataLoadingCompletion();
            } // for one MyAccount

            return okAllAccounts;
        }

        /**
         * Load Timeline(s) for one MyAccount
         * @param acc MyAccount, should be not null
         * @param timelineType_in
         * @param userId - Required for the User timeline
         * @return True if everything Succeeded
         */
        private boolean loadTimelineAccount(MyAccount acc,
                MyDatabase.TimelineTypeEnum timelineType_in, long userId) {
                boolean okAllTimelines = true;
                if (acc.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                    // Only if the User was authenticated already

                    boolean ok = false;
                    int msgAdded = 0;
                    int mentionsAdded = 0;
                    int directedAdded = 0;
                    String descr = "(starting)";

                    TimelineTypeEnum[] atl;
                    if (timelineType_in == TimelineTypeEnum.ALL) {
                        atl = new TimelineTypeEnum[] {
                                TimelineTypeEnum.HOME, TimelineTypeEnum.MENTIONS,
                                TimelineTypeEnum.DIRECT
                        };
                    } else {
                        atl = new TimelineTypeEnum[] {
                                timelineType_in 
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
                                    + acc.getAccountGuid());

                            TimelineDownloader fl = null;
                            descr = "loading " + timelineType.save();
                            fl = new TimelineDownloader(acc,
                                    MyService.this.getApplicationContext(),
                                    timelineType, userId);
                            ok = fl.loadTimeline();
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
                                case USER:
                                    // Don't count anything for now...
                                    break;
                                default:
                                    ok = false;
                                    Log.e(TAG, descr + " - not implemented");
                            }

                            if (ok && timelineType == TimelineTypeEnum.HOME && !mIsStopping) {
                                // Currently this procedure is the same for all timelines,
                                // so let's do it only for one timeline type!
                                descr = "prune old records";
                                fl.pruneOldRecords();
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

                    message += " getting " + timelineType_in.save()
                            + " for " + acc.getAccountGuid();
                    if (msgAdded > 0) {
                        message += ", " + msgAdded + " tweets";
                    }
                    if (mentionsAdded > 0) {
                        message += ", " + mentionsAdded + " mentions";
                    }
                    if (directedAdded > 0) {
                        message += ", " + directedAdded + " directs";
                    }
                    MyLog.d(TAG, message);
                }
            return okAllTimelines;
        }
        
        /**
         * TODO: Different notifications for different Accounts
         * @param msgAdded Number of "Tweets" added
         * @param mentionsAdded
         * @param directedAdded
         */
        private void notifyOfUpdatedTimeline(int msgAdded, int mentionsAdded,
                int directedAdded) {
            int N = mCallbacks.beginBroadcast();

            for (int i = 0; i < N; i++) {
                try {
                    MyLog.d(TAG, "finishUpdateTimeline, Notifying callback no. " + i);
                    IMyServiceCallback cb = mCallbacks.getBroadcastItem(i);
                    if (cb != null) {
                        if (msgAdded > 0) {
                            cb.tweetsChanged(msgAdded);
                        }
                        if (mentionsAdded > 0) {
                            cb.repliesChanged(mentionsAdded);
                        }
                        if (directedAdded > 0) {
                            cb.messagesChanged(directedAdded);
                        }
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, e.toString());
                }
            }

            mCallbacks.finishBroadcast();

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
         * Currently the notification is not targeted 
         * to any particular receiver waiting for the data...
         */
        private void notifyOfDataLoadingCompletion() {
            int N = mCallbacks.beginBroadcast();

            for (int i = 0; i < N; i++) {
                try {
                    MyLog.v(TAG,
                            "Notifying of data loading completion, Notifying callback no. " + i);
                    IMyServiceCallback cb = mCallbacks.getBroadcastItem(i);
                    if (cb != null) {
                        cb.dataLoading(0);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, e.toString());
                }
            }
            mCallbacks.finishBroadcast();
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
            }

            // Set up the notification to display to the user
            Notification notification = new Notification(R.drawable.notification_icon,
                    (String) getText(R.string.notification_title), System.currentTimeMillis());

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

        /**
         * Ask the the Twitter service of how many more requests are allowed:
         * number of remaining API calls.
         * 
         * @return ok
         */
        private boolean rateLimitStatus(String accountNameIn) {
            boolean ok = false;
            JSONObject result = new JSONObject();
            int remaining = 0;
            int limit = 0;
            try {
                Connection conn = MyAccount.getMyAccount(accountNameIn).getConnection();
                result = conn.rateLimitStatus();
                ok = (result != null);
                if (ok) {
                    switch (conn.getApi()) {
                        case TWITTER1P0:
                            remaining = result.getInt("remaining_hits");
                            limit = result.getInt("hourly_limit");
                            break;
                        default:
                            JSONObject resources = result.getJSONObject("resources");
                            JSONObject limitObject = resources.getJSONObject("statuses").getJSONObject("/statuses/home_timeline");
                            remaining = limitObject.getInt("remaining");
                            limit = limitObject.getInt("limit");
                    }
                }
            } catch (ConnectionException e) {
                Log.e(TAG, "rateLimitStatus Exception: " + e.toString());
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
            }

            if (ok) {
                int N = mCallbacks.beginBroadcast();
                for (int i = 0; i < N; i++) {
                    try {
                        IMyServiceCallback cb = mCallbacks.getBroadcastItem(i);
                        if (cb != null) {
                            cb.rateLimitStatus(remaining, limit);
                        }
                    } catch (RemoteException e) {
                        MyLog.d(TAG, e.toString());
                    }
                }
                mCallbacks.finishBroadcast();
            }
            return ok;
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
     * Returns the number of milliseconds between two fetch actions.
     * 
     * @return the number of milliseconds
     */
    private int getFetchPeriodMs() {
        int periodSeconds = Integer.parseInt(getSp().getString(MyPreferences.KEY_FETCH_PERIOD, "180"));
        return (periodSeconds * MILLISECONDS);
    }

    /**
     * Starts the repeating Alarm that sends the fetch Intent.
     */
    private boolean scheduleRepeatingAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pIntent = newRepeatingIntent();
        int periodMs = getFetchPeriodMs();
        long firstTime = SystemClock.elapsedRealtime() + periodMs;
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, periodMs, pIntent);
        MyLog.d(TAG, "Started repeating alarm in a " + periodMs + " ms rhythm.");
        return true;
    }

    /**
     * Cancels the repeating Alarm that sends the fetch Intent.
     */
    private boolean cancelRepeatingAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pIntent = newRepeatingIntent();
        am.cancel(pIntent);
        MyLog.d(TAG, "Cancelled repeating alarm.");
        return true;
    }

    /**
     * Returns Intent to be send with Repeating Alarm.
     * This alarm will be received by {@link MyServiceManager}
     * @return the Intent
     */
    private PendingIntent newRepeatingIntent() {
        Intent intent = new Intent(ACTION_ALARM);
        intent.putExtra(MyService.EXTRA_MSGTYPE, CommandEnum.AUTOMATIC_UPDATE.save());
        PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        return pIntent;
    }

    /**
     * The IMyService is defined through IDL
     */
    private final IMyService.Stub mBinder = new IMyService.Stub() {
        public void registerCallback(IMyServiceCallback cb) {
            if (cb != null)
                mCallbacks.register(cb);
        }

        public void unregisterCallback(IMyServiceCallback cb) {
            if (cb != null)
                mCallbacks.unregister(cb);
        }
    };

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
