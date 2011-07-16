/* 
 * Copyright (C) 2008 Torgny Bjers
 * Copyright (c) 2011 yvolk (Yuri Volkov), http://yurivolkov.com
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

package com.xorcode.andtweet;

import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
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
import android.preference.PreferenceManager;
import android.util.Log;

import com.xorcode.andtweet.TwitterUser.CredentialsVerified;
import com.xorcode.andtweet.appwidget.AndTweetAppWidgetProvider;
import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.data.FriendTimeline;
import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;
import com.xorcode.andtweet.util.I18n;

/**
 * This is an application service that serves as a connection between Android
 * and Twitter. Other applications can interact with it via IPC.
 */
public class AndTweetService extends Service {
	private static final String TAG = AndTweetService.class.getSimpleName();
	/**
	 * Use this tag to change logging level of the whole application
	 * Is used is isLoggable(APPTAG, ... ) calls
	 */
    public static final String APPTAG = "AndTweet";

    private static final String packageName = AndTweetService.class.getPackage().getName();

    /**
     * Prefix of all actions of this Service
     */
    private static final String ACTIONPREFIX = packageName + ".action.";

    /**
     * Intent with this action sent when it is time to update AndTweet AppWidget.
     *
     * <p>This may be sent in response to some new information
     * is ready for notification (some changes...),
     * or the system booting.
     *
     * <p>
     * The intent will contain the following extras:
     * <ul>
     *  <li>{@link #EXTRA_MSGTYPE}</li>
     *  <li>{@link #EXTRA_NUMTWEETSMSGTYPE}</li>
     *  <li>{@link android.appwidget.AppWidgetManager#EXTRA_APPWIDGET_IDS}<br/>
     *     The appWidgetIds to update.  This may be all of the AppWidgets created for this
     *     provider, or just a subset.  The system tries to send updates for as few AppWidget
     *     instances as possible.</li>
     * 
     * @see AppWidgetProvider#onUpdate AppWidgetProvider.onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
     */
    public static final String ACTION_APPWIDGET_UPDATE = ACTIONPREFIX + "APPWIDGET_UPDATE";

    /**
     * This action is used in any intent sent to this service
     * Actual command to perform by this service is in the {@link #EXTRA_MSGTYPE} extra of the intent 
     * @see CommandEnum 
     */
    public static final String ACTION_GO = ACTIONPREFIX + "GO";
	
    /**	
     * These names of extras are used in the Intent-notification of new Tweets
     * (e.g. to notify Widget).
     */

	/**
     * This extra is used as a command to perform by AndTweetService and AndTweetAppWidgetProvider
     * Value of this extra is string code of CommandEnum (not serialized enum !)
     */
    public static final String EXTRA_MSGTYPE = packageName + ".MSGTYPE";

    /**
     * Command parameter: long - ID of the Tweet (or Message)
     */
    public static final String EXTRA_TWEETID = packageName + ".TWEETID";

    /**
     * Text of the status message
     */
    public static final String EXTRA_STATUS = packageName + ".STATUS";

    /**
     * Reply to
     */
    public static final String EXTRA_INREPLYTOID = packageName + ".INREPLYTOID";
        
    /**	
     * Number of new tweets. Value is integer
     */
    public static final String EXTRA_NUMTWEETS = packageName + ".NUMTWEETS";

    /**
     * This extra is used to determine which timeline to show in TimelineActivity
     * Value is integer (TODO: enum...) 
     */
    public static final String EXTRA_TIMELINE_TYPE = packageName + ".TIMELINE_TYPE";
    
    /**
     * The command to the AndTweetService or to AndTweetAppWidgetProvider as a enum
     * We use 'code' for persistence
     * @author yvolk
     */
    public enum CommandEnum {

        /**
         * The action is unknown
         */
        UNKNOWN("unknown"), 
        /**
         * The action is being sent by recurring alarm to
         * fetch the tweets, replies and other information in the background
         */
        AUTOMATIC_UPDATE("automatic-update"),
        /**
         * Fetch the tweets, replies and other information.
         */
        FETCH_TIMELINE("fetch-timeline"),
        /**
         * Fetch messages
         */
        FETCH_MESSAGES("fetch-messages"),
        /**
         * The recurring alarm that is used to implement recurring tweet downloads
         * should be started.
         */
        START_ALARM("start-alarm"),
        /**
        * The recurring alarm that is used to implement recurring tweet downloads
        * should be stopped.
        */
        STOP_ALARM("stop-alarm"),
        /**
         * The recurring alarm that is used to implement recurring tweet downloads
         * should be restarted.
         */
        RESTART_ALARM("restart-alarm"),

        CREATE_FAVORITE("create-favorite"),
        DESTROY_FAVORITE("destroy-favorite"),

        UPDATE_STATUS("update-status"),

        /**
         * Notify User about commands in the Queue
         */
        NOTIFY_QUEUE("notify-queue"),
        
        /**
         * Commands to the Widget
         */
        NOTIFY_DIRECT_MESSAGE("notify-direct-message"),
        NOTIFY_TIMELINE("notify-timeline"),
        NOTIFY_REPLIES("notify-replies"),
        /**
         * Clear previous notifications (because e.g. user open tweet list...) 
         */
        NOTIFY_CLEAR("notify-clear")
        ;

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
         * Returns the enum for a String action code
         * or UNKNOWN 
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
     * @author yvolk
     */
    private class CommandData {
        public CommandEnum command;
        public long itemId = 0;
        /**
         * Other command parameters
         */
        public Bundle bundle;

        private int hashcode = 0;
        
        /**
         * Number of retries left
         */
        public int retriesLeft = 0;
        
        public CommandData(Intent intent) {
            bundle = intent.getExtras();
            // Decode command
            String strCommand = "(no command)";
            if (bundle != null) {
                strCommand = bundle.getString(EXTRA_MSGTYPE);
                itemId = bundle.getLong(EXTRA_TWEETID);
            }
            command = CommandEnum.load(strCommand);
        }

        /**
         * It's used in equals() method
         * We need to distinguish duplicated commands
         */
        @Override
        public int hashCode() {
            if (hashcode == 0) {
                if (itemId != 0) {
                    hashcode =  (int) ((itemId << 4) & 0xFFFFFFFF);
                    hashcode |= command.ordinal();
                }
                if (hashcode == 0) {
                    if (command == CommandEnum.UPDATE_STATUS) {
                       String text = bundle.getString(EXTRA_STATUS);
                       hashcode = text.hashCode();
                    }
                }
                if (hashcode == 0) {
                    hashcode += command.ordinal();
                }
            }
            return hashcode;
        }
        
        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "CommandData [" 
                + "command=" + command.save() 
                + ( itemId == 0 ? "" :  "; id=" + itemId) 
                + ", hashCode=" + hashCode() 
                + "]";            
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            if (obj.getClass() != getClass()) {
                return false;
            }
            return (this.hashCode() == ((CommandData)obj).hashCode());
        }
    }
    
	/**
	 * This is a list of callbacks that have been registered with the service.
	 */
	final RemoteCallbackList<IAndTweetServiceCallback> mCallbacks = new RemoteCallbackList<IAndTweetServiceCallback>();

	private static final int MILLISECONDS = 1000;

	/**
	 * Send broadcast to Widgets even if there are no new tweets
	 */
    // TODO: Maybe this should be additional setting...
    public static boolean updateWidgetsOnEveryUpdate = true;
    
	private boolean mNotificationsEnabled;
	private boolean mNotificationsVibrate;

	private NotificationManager mNM;

    /**
     * Commands queue to be processed by the Service
     */
    private BlockingQueue<CommandData> mCommands = new ArrayBlockingQueue<CommandData>(100, true);
    /**
     * Retry Commands queue
     */
    private BlockingQueue<CommandData> mRetryQueue = new ArrayBlockingQueue<CommandData>(100, true);
	
	/**
	 * The set of threads that are currently executing commands
	 * For now let's have only ONE working thread 
	 * (it seems there is some problem in parallel execution...)
	 */
	private Set<CommandExecutor> mExecutors = new HashSet<CommandExecutor>();
	/**
	 * The number of listeners returned by the last broadcast start call.
	 */
	private volatile int mBroadcastListenerCount = 0;
	/**
	 * The reference to the wake lock used to keep the CPU from stopping
	 * during downloads.
	 */
	private volatile PowerManager.WakeLock mWakeLock = null;

    /** 
     * @return Single instance of SharedPreferences is returned, this is why we may synchronize on the object
     */
    private SharedPreferences getSp() {
       return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

	@Override
	public void onCreate() {
		d(TAG, "Service created in context: "
				+ getApplication().getApplicationContext().getPackageName());

		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        registerReceiver(intentReceiver, new IntentFilter(ACTION_GO));
	}

	private BroadcastReceiver intentReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent intent) {
			d(TAG, "onReceive()");
			receiveCommand(intent);
		}

	};

	@Override
	public void onDestroy() {
	    // Unregister all callbacks.
		mCallbacks.kill();

		cancelRepeatingAlarm();
		
		unregisterReceiver(intentReceiver);

		d(TAG, "Service destroyed");
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Select the interface to return. If your service only implements
		// a single interface, you can just return it here without checking
		// the Intent.
		if (IAndTweetService.class.getName().equals(intent.getAction())) {
			return mBinder;
		}
		return null;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId); 
		d(TAG, "onStart(): startid: " + startId);
		receiveCommand(intent);
	}

	/**
	 * Put Intent to the Command's queue
	 * and Start Execution thread if none is already running 
	 * 
	 * @param Intent containing command and it's parameters. 
	 * It may be null to initialize execution only.
	 */
	private synchronized void receiveCommand(Intent intent) {
	    if (mCommands.isEmpty()) {
	        // This is a good place to send commands from retry Queue
	        while (!mRetryQueue.isEmpty()) {
	           CommandData commandData = mRetryQueue.poll(); 
	           if (!mCommands.contains(commandData)) {
	               if (!mCommands.offer(commandData)) {
	                   Log.e(TAG, "mCommands is full?");
	               }
	           }
	        }
	    }
	    
        if (intent != null) {
            CommandData commandData = new CommandData(intent); 
            
            // Maybe this command may be processed synchronously without Internet connection?
            if (processCommandImmediately(commandData)) {
                // Don't add to the queue
            } else if (mCommands.contains(commandData)) {
                d(TAG, "Duplicated " + commandData);
            } else {
                d(TAG, "Adding to the queue " + commandData);
                if (!mCommands.offer(commandData)) {
                    Log.e(TAG, "mCommands is full?");
                }
            }
        }
        
        // Start Executor if necessary
        startEndStuff(true, null, null);
	}

    /**
     * @param commandData
     * @return true if the command was processed (either successfully or not...)
     */
    private boolean processCommandImmediately(CommandData commandData) {
        boolean processed = false;
        // Processed successfully?
        boolean ok = true;
        processed = (commandData == null);
        if (!processed) {
            processed = true;
            switch (commandData.command) {
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
                default:
                    processed = false;
                    break;
            }
            if (processed) {
                d(TAG, (ok ? "Succeeded" : "Failed") + " " + commandData);
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
    private synchronized void startEndStuff(boolean start, CommandExecutor executorIn, String logMsg) {
        if (start) {
            SharedPreferences sp = getSp();
            mNotificationsEnabled = sp.getBoolean("notifications_enabled", false);
            mNotificationsVibrate = sp.getBoolean("vibration", false);

            if (!mCommands.isEmpty()) {
                // Don't even launch executor if we're not online
                if (isOnline()) {
                    // only one Executing thread for now...
                    if (mExecutors.isEmpty()) {
                        CommandExecutor executor;
                        if (executorIn != null) {
                            executor = executorIn;
                        } else {
                            executor = new CommandExecutor();
                        }
                        if (logMsg != null) {
                            d(TAG, logMsg);
                        }
                        mExecutors.add(executor);
                        if (mExecutors.size() == 1) {
                            mWakeLock = getWakeLock();
                            mBroadcastListenerCount = mCallbacks.beginBroadcast();
                            d(TAG, "No other threads running so starting new broadcast for "
                                    + mBroadcastListenerCount + " listeners");
                        }
                        executor.execute();
                    }
                } else {
                    notifyOfQueue();
                }
            }
        } else {
            // Stop
            if (logMsg != null) {
                d(TAG, logMsg);
            }
            mExecutors.remove(executorIn);
            if (mExecutors.size() == 0) {
                d(TAG, "Ending last thread so also ending broadcast.");
                mWakeLock.release();
                mCallbacks.finishBroadcast();
                notifyOfQueue();
            }
        }
    }
    
    /**
     * Notify user of the commands Queue size
     */
    private void notifyOfQueue() {
        int count = mRetryQueue.size() + mCommands.size();
        if (count == 0) {
            // Clear notification
            mNM.cancel(CommandEnum.NOTIFY_QUEUE.ordinal());
        } else if (mNotificationsEnabled) {
            if (mRetryQueue.size() > 0) {
                d(TAG, mRetryQueue.size() + " commands in Retry Queue.");
            }
            if (mCommands.size() > 0) {
                d(TAG, mCommands.size() + " commands in Main Queue.");
            }

            // Set up the notification to display to the user
            Notification notification = new Notification(R.drawable.notification_icon,
                    (String) getText(R.string.notification_title), System.currentTimeMillis());

            int messageTitle;
            String aMessage = "";

            aMessage = I18n.formatQuantityMessage(getApplicationContext(),
                    R.string.notification_queue_format, count,
                    R.array.notification_queue_patterns, R.array.notification_queue_formats);
            messageTitle = R.string.notification_title_queue;

            // Set up the scrolling message of the notification
            notification.tickerText = aMessage;

            /**
             * Set the latest event information and send the notification
             * Actually don't start any intent
             * @see http://stackoverflow.com/questions/4232006/android-notification-pendingintent-problem 
            */
            PendingIntent pi = PendingIntent.getActivity(this, 0, null, 0); 
            notification.setLatestEventInfo(this, getText(messageTitle), aMessage, pi);
            mNM.notify(CommandEnum.NOTIFY_QUEUE.ordinal(), notification);
        }
    }

    /**
     * Command executor
     * @author yvolk
     *
     */
    private class CommandExecutor extends AsyncTask<Void, Void, JSONObject> {
        // private boolean skip = false;

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected JSONObject doInBackground(Void... arg0) {
            JSONObject jso = null;

            int what = 0;
            String message = "";
            
            do {
                boolean ok = false;
                // Get commands from the Queue one by one and execute them 
                // The queue is Blocking, so we can do this
                CommandData commandData = mCommands.poll();
                if (commandData == null) { 
                    // All work is done
                    break;
                }
                
                commandData.retriesLeft -= 1;
                boolean retry = false;
                d(TAG, "Executing " + commandData);
                
                switch (commandData.command) {
                    case AUTOMATIC_UPDATE:
                        d(TAG, "Getting tweets, replies and messages");
                        ok = loadTimeline(true, true);
                        break;
                    case FETCH_TIMELINE:
                        d(TAG, "Getting tweets and replies");
                        ok = loadTimeline(true, false);
                        break;
                    case FETCH_MESSAGES:
                        d(TAG, "Getting messages");
                        ok = loadTimeline(false, true);
                        break;
                    case CREATE_FAVORITE:
                    case DESTROY_FAVORITE:
                        ok = createOrDestroyFavorite(commandData.command == CommandEnum.CREATE_FAVORITE, commandData.itemId);
                        // Retry in a case of an error
                        retry = !ok;
                        break;
                    case UPDATE_STATUS:
                        String status = commandData.bundle.getString(EXTRA_STATUS).trim();
                        long inReplyToId = commandData.bundle.getLong(EXTRA_INREPLYTOID);
                        ok = updateStatus(status, inReplyToId);
                        retry = !ok;
                        break;
                    default:
                        Log.e(TAG, "Unexpected command here " + commandData);
                }
                d(TAG, (ok ? "Succeeded" : "Failed") + " " + commandData);
                if (retry) {
                    if (commandData.retriesLeft < 0) {
                        // This means that retriesLeft was not set yet,
                        // so let's set it to some default value, the same for any command
                        // that needs to be retried...
                        commandData.retriesLeft = 9;
                    }
                    // Check if any retries left (actually 0 means this was the last retry)
                    if (commandData.retriesLeft > 0) {
                        // Put the command to the retry queue
                        if (!mRetryQueue.contains(commandData)) {
                            if (!mRetryQueue.offer(commandData)) {
                                Log.e(TAG, "mRetryQueue is full?");
                            }
                        }
                    } else {
                        Log.e(TAG, "Couldn't execute " + commandData);
                    }
                }
                if (!ok && !isOnline()) {
                    // Don't bother with other commands if we're not Online :-)
                    break;
                }
            } while (true);

            try {
                jso = new JSONObject();
                jso.put("what", what);
                jso.put("message", message);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return jso;
        }

        /**
         * TODO: Delete unnecessary lines...
         * This is in the UI thread, so we can mess with the UI
         * @return ok
         */
        protected void onPostExecute(JSONObject jso) {
            //boolean succeeded = false;
            String message = null;
            if (jso != null) {
                try {
                    int what = jso.getInt("what");
                    message = jso.getString("message");

                    switch (what) {
                        case 0:
                            
                            //succeeded = true;
                            break;
                    }
                } catch (JSONException e) {
                    // Auto-generated catch block
                    e.printStackTrace();
                }
            }
            startEndStuff(false, this, message);
        }
        
        /**
         * 
         * @param create true - create, false - destroy
         * @param statusId
         * @return boolean ok
         */
        private boolean createOrDestroyFavorite(boolean create, long statusId) {
            boolean ok = false;
            JSONObject result = new JSONObject();
            try {
                if (create) {
                    result = TwitterUser.getTwitterUser(AndTweetService.this.getApplicationContext()).getConnection()
                    .createFavorite(statusId);
                } else {
                    result = TwitterUser.getTwitterUser(AndTweetService.this.getApplicationContext()).getConnection()
                    .destroyFavorite(statusId);
                }
                ok = (result != null);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.toString());
            } catch (ConnectionException e) {
                Log.e(TAG, (create ? "create" : "destroy") + "Favorite Connection Exception: " + e.toString());
            } catch (ConnectionAuthenticationException e) {

            } catch (ConnectionUnavailableException e) {

            } catch (SocketTimeoutException e) {

            }

            if (ok) {
                try {
                    Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI,
                            result.getLong("id"));
                    Cursor c = getContentResolver().query(uri, new String[] {
                            Tweets._ID, Tweets.AUTHOR_ID, Tweets.TWEET_TYPE
                    }, null, null, null);
                    try {
                        c.moveToFirst();
                        FriendTimeline fl = new FriendTimeline(AndTweetService.this.getApplicationContext(),
                                c.getInt(c.getColumnIndex(Tweets.TWEET_TYPE)));
                        fl.insertFromJSONObject(result, true);
                    } catch (Exception e) {
                        Log.e(TAG, "e: " + e.toString());
                    } finally {
                        if (c != null && !c.isClosed())
                            c.close();
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error marking as " + (create ? "" : "not ") + "favorite: " + e.toString());
                }
            }
            
            // TODO: Maybe we need to notify the caller about the result?!

            d(TAG, (create ? "Creating" : "Destroying") + " favorite " + (ok ? "succeded" : "failed") + ", id=" + statusId);
            return ok;
        }

        /**
         * 
         * @param status
         * @param inReplyToId
         * @return ok
         */
        private boolean updateStatus(String status, long inReplyToId) {
            boolean ok = false;
            JSONObject result = new JSONObject();
            try {
                result = TwitterUser.getTwitterUser(AndTweetService.this.getApplicationContext()).getConnection()
                .updateStatus(status.trim(), inReplyToId);
                ok = (result != null);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.toString());
            } catch (ConnectionException e) {
                Log.e(TAG, "updateStatus Connection Exception: " + e.toString());
            } catch (ConnectionAuthenticationException e) {
                Log.e(TAG, "updateStatus Connection Authentication Exception: " + e.toString());
            }
            if (ok) {
                // The tweet was sent successfully
                FriendTimeline fl = new FriendTimeline(AndTweetService.this.getApplicationContext(),
                        AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
                try {
                    fl.insertFromJSONObject(result, true);
                } catch (JSONException e) {
                    Log.e(TAG, "updateStatus JSONException: " + e.toString());
                }
            }
            return ok;
        }
        
        /**
         * 
         * @param loadTweets - Should we load tweets
         * @param loadMessages - Should we load messages
         * @return ok
         */
        public boolean loadTimeline(boolean loadTweets, boolean loadMessages) {

            // TODO: Cycle for all users...
            boolean ok = false;
            int aNewTweets = 0;
            int aReplyCount = 0;
            int aNewMessages = 0;

            if (TwitterUser.getTwitterUser(AndTweetService.this.getApplicationContext())
                    .getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                // Only if User was authenticated already
                try {
                    FriendTimeline fl = null;
                    ok = true;
                    if (ok && loadTweets) {
                        fl = new FriendTimeline(AndTweetService.this.getApplicationContext(),
                                AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS);
                        ok = fl.loadTimeline();
                        aReplyCount = fl.replyCount();

                        if (ok) {
                            fl = new FriendTimeline(AndTweetService.this.getApplicationContext(),
                                    AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
                            ok = fl.loadTimeline();
                            aNewTweets = fl.newCount();
                            aReplyCount += fl.replyCount();
                        }

                        fl.pruneOldRecords();
                    }

                    if (ok && loadMessages) {
                        fl = new FriendTimeline(AndTweetService.this.getApplicationContext(),
                                AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES);
                        ok = fl.loadTimeline();
                        aNewMessages = fl.newCount();
                        fl.pruneOldRecords();
                    }
                } catch (ConnectionException e) {
                    Log.e(TAG, "mLoadTimeline Connection Exception: " + e.toString());
                } catch (SQLiteConstraintException e) {
                    Log.e(TAG, "mLoadTimeline SQLite Exception: " + e.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "mLoadTimeline JSON Exception: " + e.toString());
                } catch (ConnectionAuthenticationException e) {
                    Log.e(TAG, "mLoadTimeline Authentication Exception: " + e.toString());
                } catch (ConnectionUnavailableException e) {
                    Log.e(TAG, "mLoadTimeline FAIL Whale: " + e.toString());
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "mLoadTimeline Connection Timeout: " + e.toString());
                }
            }

            if (ok) {
                notifyOfUpdatedTimeline(aNewTweets, aReplyCount, aNewMessages);
            }

            String message = (ok ? "Succeeded" : "Failed") + " getting ";
            if (loadTweets) {
                message += aNewTweets + " tweets, " + aReplyCount + " replies";
            }
            if (loadMessages) {
                if (loadTweets) {
                    message += " and ";
                }
                message += aNewMessages + " messages";
            }
            AndTweetService.d(TAG, message);

            return ok;
        }
        
        private void notifyOfUpdatedTimeline(int tweetsChanged, int repliesChanged, int messagesChanged) {

            // TODO: It's not so simple... I think :-) 
            int N = mBroadcastListenerCount;
            
            for (int i = 0; i < N; i++) {
                try {
                    d(TAG, "finishUpdateTimeline, Notifying callback no. " + i);
                    IAndTweetServiceCallback cb = mCallbacks.getBroadcastItem(i);
                    if (cb != null) {
                        if (tweetsChanged > 0) {
                            cb.tweetsChanged(tweetsChanged);
                        }
                        if (repliesChanged > 0) {
                            cb.repliesChanged(repliesChanged);
                        }
                        if (messagesChanged > 0) {
                            cb.messagesChanged(messagesChanged);
                        }
                        cb.dataLoading(0);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, e.toString());
                }
            }
            boolean notified = false;
            if (repliesChanged > 0) {
                notifyOfNewTweets(repliesChanged, CommandEnum.NOTIFY_REPLIES);
                notified = true;
            }
            if (messagesChanged > 0) {
                notifyOfNewTweets(messagesChanged, CommandEnum.NOTIFY_DIRECT_MESSAGE);
                notified = true;
            }
            if (tweetsChanged > 0 || !notified) {
                notifyOfNewTweets(tweetsChanged, CommandEnum.NOTIFY_TIMELINE);
                notified = true;
            }
        }

        /**
         * Notify the user of new tweets.
         * 
         * @param numTweets
         */
        private void notifyOfNewTweets(int numTweets, CommandEnum msgType) {
            d(TAG, "notifyOfNewTweets n=" + numTweets + "; msgType=" + msgType);

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
                ringtone = sp.getString(PreferencesActivity.KEY_RINGTONE_PREFERENCE, null);
            }

            // Make sure that notifications haven't been turned off for the message
            // type
            switch (msgType) {
                case NOTIFY_REPLIES:
                    if (!notificationsReplies)
                        return;
                    break;
                case NOTIFY_DIRECT_MESSAGE:
                    if (!notificationsMessages)
                        return;
                    break;
                case NOTIFY_TIMELINE:
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
            // com.xorcode.andtweet.TimelineActivity.onOptionsItemSelected
            switch (msgType) {
                case NOTIFY_REPLIES:
                    aMessage = I18n
                            .formatQuantityMessage(getApplicationContext(),
                                    R.string.notification_new_mention_format, numTweets,
                                    R.array.notification_mention_patterns,
                                    R.array.notification_mention_formats);
                    messageTitle = R.string.notification_title_mentions;
                    intent = new Intent(getApplicationContext(), TweetListActivity.class);
                    intent.putExtra(AndTweetService.EXTRA_TIMELINE_TYPE,
                            AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS);
                    contentIntent = PendingIntent.getActivity(getApplicationContext(), numTweets,
                            intent, 0);
                    break;

                case NOTIFY_DIRECT_MESSAGE:
                    aMessage = I18n
                            .formatQuantityMessage(getApplicationContext(),
                                    R.string.notification_new_message_format, numTweets,
                                    R.array.notification_message_patterns,
                                    R.array.notification_message_formats);
                    messageTitle = R.string.notification_title_messages;
                    intent = new Intent(getApplicationContext(), MessageListActivity.class);
                    intent.putExtra(AndTweetService.EXTRA_TIMELINE_TYPE,
                            AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES);
                    contentIntent = PendingIntent.getActivity(getApplicationContext(), numTweets,
                            intent, 0);
                    break;

                case NOTIFY_TIMELINE:
                default:
                    aMessage = I18n.formatQuantityMessage(getApplicationContext(),
                            R.string.notification_new_tweet_format, numTweets,
                            R.array.notification_tweet_patterns, R.array.notification_tweet_formats);
                    messageTitle = R.string.notification_title;
                    intent = new Intent(getApplicationContext(), TweetListActivity.class);
                    intent.putExtra(AndTweetService.EXTRA_TIMELINE_TYPE,
                            AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
                    contentIntent = PendingIntent.getActivity(getApplicationContext(), numTweets,
                            intent, 0);
                    break;
            }

            // Set up the scrolling message of the notification
            notification.tickerText = aMessage;

            // Set the latest event information and send the notification
            notification.setLatestEventInfo(AndTweetService.this, getText(messageTitle), aMessage, contentIntent);
            mNM.notify(msgType.ordinal(), notification);
        }

        /** 
         * Send Update intent to AndTweet Widget(s),
         * if there are some installed... (e.g. on the Home screen...) 
         * @see AndTweetAppWidgetProvider
         */
        private void updateWidgets(int numTweets, CommandEnum msgType) {
            Intent intent = new Intent(ACTION_APPWIDGET_UPDATE);
            intent.putExtra(EXTRA_MSGTYPE, msgType.save());
            intent.putExtra(EXTRA_NUMTWEETS, numTweets);
            sendBroadcast(intent);
        }
    }
      
	private PowerManager.WakeLock getWakeLock() {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = pm.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wakeLock.acquire();
		return wakeLock;
	}

	/**
	 * Returns the number of milliseconds between two fetch actions. 
	 *  
	 * @return the number of milliseconds
	 */
	private int getFetchFrequencyS() {
		int frequencyS =  Integer.parseInt(getSp().getString("fetch_frequency", "180"));
		return (frequencyS * MILLISECONDS);
	}

	/**
	 * Starts the repeating Alarm that sends the fetch Intent.
	 */
	private boolean scheduleRepeatingAlarm() {
		final AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		final PendingIntent pIntent = getRepeatingIntent();
		final int frequencyMs = getFetchFrequencyS();
        final long firstTime = SystemClock.elapsedRealtime() + frequencyMs;
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, frequencyMs,   pIntent);
        d(TAG, "Started repeating alarm in a " + frequencyMs + "ms rhythm.");
        return true;
	}

	/**
	 * Cancels the repeating Alarm that sends the fetch Intent.
	 */
	private boolean cancelRepeatingAlarm() {
		final AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		final PendingIntent pIntent = getRepeatingIntent();
		am.cancel(pIntent);
		d(TAG, "Cancelled repeating alarm.");
		return true;
	}

	/**
	 * Returns the recurring AlarmHandler Intent.
	 * @return the Intent 
	 */
	private PendingIntent getRepeatingIntent() {
		Intent intent = new Intent(ACTION_GO);
        intent.putExtra(AndTweetService.EXTRA_MSGTYPE, CommandEnum.AUTOMATIC_UPDATE.save());
		PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
		return pIntent;
	}

	/**
	 * The IAndTweetService is defined through IDL
	 */
	private final IAndTweetService.Stub mBinder = new IAndTweetService.Stub() {
		public void registerCallback(IAndTweetServiceCallback cb) {
			if (cb != null)
				mCallbacks.register(cb);
		}

		public void unregisterCallback(IAndTweetServiceCallback cb) {
			if (cb != null)
				mCallbacks.unregister(cb);
		}
	};

    /**
     * We use this function before actual requests of Internet services Based on
     * http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts
     */
    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // test for connection
        if (cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isAvailable()
                && cm.getActiveNetworkInfo().isConnected()) {
            return true;
        } else {
            v(TAG, "Internet Connection Not Present");
            return false;
        }
    }
	
    /**
     * Utility method to send the Intent to the {@link AndTweetService} that
     * starts the recurring alarm.
     * 
     * @param context the context to use for sending the Intent
     */
    public static void startAutomaticUpdates(Context context) {
        Intent serviceIntent = new Intent(IAndTweetService.class.getName());
        serviceIntent.putExtra(AndTweetService.EXTRA_MSGTYPE, CommandEnum.START_ALARM.save());
        context.startService(serviceIntent);
    }

    /**
     * Utility method to send the Intent to the {@link AndTweetService} that
     * stops the recurring alarm.
     * 
     * @param context the context to use for sending the Intent
     */
    public static void stopAutomaticUpdates(Context context) {
        Intent serviceIntent = new Intent(IAndTweetService.class.getName());
        serviceIntent.putExtra(AndTweetService.EXTRA_MSGTYPE, CommandEnum.STOP_ALARM.save());
        context.startService(serviceIntent);
    }

    /**
     * Utility method to send the Intent to the {@link AndTweetService} that
     * restarts the recurring alarm. This is useful for changing the frequency.
     * 
     * @param context the context to use for sending the Intent
     */
    public static void restartAutomaticUpdates(Context context) {
        Intent serviceIntent = new Intent(IAndTweetService.class.getName());
        serviceIntent.putExtra(AndTweetService.EXTRA_MSGTYPE, CommandEnum.RESTART_ALARM.save());
        context.startService(serviceIntent);
    }

    /**
     * Shortcut for debugging messages of the application 
     */
    public static int d(String tag, String msg) {
        int i = 0;
        if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
            i = Log.d(TAG, msg);
        }
        return i;
    }

    /**
     * Shortcut for debugging messages of the application 
     */
    public static int d(String tag, String msg, Throwable tr) {
        int i = 0;
        if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
          i = Log.d(TAG, msg, tr);
        }
        return i;
    }
    
    /**
     * Shortcut for verbose messages of the application 
     */
    public static int v(String tag, String msg) {
        int i = 0;
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            i = Log.v(TAG, msg);
        }
        return i;
    }
}
