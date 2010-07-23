/* 
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

package com.xorcode.andtweet;

import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.Service;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.xorcode.andtweet.appwidget.AndTweetAppWidgetProvider;
import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.data.DirectMessages;
import com.xorcode.andtweet.data.FriendTimeline;
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
     * Intent with this action sent when it is time to update AndTweet AppWidget.
     *
     * <p>This may be sent in response to some new information
     * is ready for notification (some changes...),
     * or the system booting.
     *
     * <p>
     * The intent will contain the following extras:
     * <ul>
     * 	<li>{@link #EXTRA_MSGTYPE}</li>
     * 	<li>{@link #EXTRA_NUMTWEETSMSGTYPE}</li>
     * 	<li>{@link android.appwidget.AppWidgetManager#EXTRA_APPWIDGET_IDS}<br/>
     *     The appWidgetIds to update.  This may be all of the AppWidgets created for this
     *     provider, or just a subset.  The system tries to send updates for as few AppWidget
     *     instances as possible.</li>
     * 
     * @see AppWidgetProvider#onUpdate AppWidgetProvider.onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
     */
	private static final String ACTIONPREFIX = packageName + ".action.";

	/**
	 * The message string that triggers an update of the widget.
	 */
    public static final String ACTION_APPWIDGET_UPDATE = ACTIONPREFIX + "APPWIDGET_UPDATE";

    /**
	 * The message string that is send to AndTweetService to signal that
	 * the recurring alarm that is used to implement recurring tweet downloads
	 * should be started.
	 */
    public static final String ACTION_START_ALARM = ACTIONPREFIX + "START_ALARM";

    /**
	 * The message string that is send to AndTweetService to signal that
	 * the recurring alarm that is used to implement recurring tweet downloads
	 * should be stopped.
	 */
	public static final String ACTION_STOP_ALARM = ACTIONPREFIX + "STOP_ALARM";

	/**
	 * The message string that is send to AndTweetService to signal that
	 * the recurring alarm that is used to implement recurring tweet downloads
	 * should be restarted.
	 */
	public static final String ACTION_RESTART_ALARM = ACTIONPREFIX + "RESTART_ALARM";

	/**
	 * The message string that the recurring alarm sends to signal
	 * to AndTweetService that it should fetch the tweets and
	 * other information.
	 */
	private static final String ACTION_FETCH = ACTIONPREFIX + "FETCH";

    /**	
     * These names of extras are used in the Intent-notification of new Tweets
     * (e.g. to notify Widget).
     * For types of messages see {@link #NOTIFY_DIRECT_MESSAGE}
     */
    public static final String EXTRA_MSGTYPE = packageName + ".MSGTYPE";

    /**	
     * Number of new tweets...
     */
    public static final String EXTRA_NUMTWEETS = packageName + ".NUMTWEETS";

    /**
     * This extra is used to determine which timeline to show in TimelineActivity 
     */
    public static final String EXTRA_TIMELINE_TYPE = packageName + ".TIMELINE_TYPE";
    
	/**
	 * This is a list of callbacks that have been registered with the service.
	 */
	final RemoteCallbackList<IAndTweetServiceCallback> mCallbacks = new RemoteCallbackList<IAndTweetServiceCallback>();

	private static final int MILLISECONDS = 1000;
	private static final int MSG_UPDATE_TIMELINE = 1;
	private static final int MSG_UPDATE_DIRECT_MESSAGES = 2;

    /**
     * A sentinel value that this class will never use as a msgType
     */
    public static final int NOTIFY_INVALID = 0;
	/**
	 * Types of new tweets notification messages
	 */
	public static final int NOTIFY_DIRECT_MESSAGE = 1;
	public static final int NOTIFY_TIMELINE = 2;
	public static final int NOTIFY_REPLIES = 3;
	/**
	 * Clear previous notifications (because e.g. user open tweet list...) 
	 */
	public static final int NOTIFY_CLEAR = 4;

	/**
	 * Send broadcast to Widgets even if there are no new tweets
	 */
    // TODO: Maybe this should be additional setting...
    public static boolean updateWidgetsOnEveryUpdate = true;

	private boolean mNotificationsEnabled;
	private boolean mNotificationsVibrate;

	private NotificationManager mNM;

	/**
	 * The set of threads that are currently performing downloads of
	 * information from the Twitter servers.
	 */
	private Set<Runnable> mFetchingThreads = new HashSet<Runnable>();
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
		Log.d(TAG, "Service created in context: "
				+ getApplication().getApplicationContext().getPackageName());

		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		registerReceiver(intentReceiver, new IntentFilter(ACTION_FETCH));
	}

	private BroadcastReceiver intentReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent intent) {
			Log.d(TAG, "onReceive()");
			handleIntent(intent.getAction());
		}

	};

	@Override
	public void onDestroy() {
		// Unregister all callbacks.
		mCallbacks.kill();

		cancelRepeatingAlarm();
		
		unregisterReceiver(intentReceiver);

		Log.d(TAG, "Service destroyed");
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
		Log.d(TAG, "onStart(): " + intent + " startid: " + startId);
		Bundle extras = intent.getExtras();
		if (extras != null) {
			String action = extras.getString(EXTRA_MSGTYPE);
			handleIntent(action);
		}
	}

	/**
	 * Handles the action that came with an incoming intent and is used to update 
	 * the alarm setting and to start fetching.
	 * 
	 * @param action the action string to handle
	 */
	private void handleIntent(String action) {
		if (ACTION_FETCH.equals(action)) {
			Log.d(TAG, "ACTION_FETCH");
            mHandler.sendEmptyMessage(MSG_UPDATE_TIMELINE);
            mHandler.sendEmptyMessage(MSG_UPDATE_DIRECT_MESSAGES);
		}
		else if (ACTION_START_ALARM.equals(action)) {
			Log.d(TAG, "ACTION_START_ALARM");
			scheduleRepeatingAlarm();
		}
		else if (ACTION_STOP_ALARM.equals(action)) {
			Log.d(TAG, "ACTION_STOP_ALARM");
			cancelRepeatingAlarm();
		}
		else if (ACTION_RESTART_ALARM.equals(action)) {
			Log.d(TAG, "ACTION_RESTART_ALARM");
			cancelRepeatingAlarm();
			scheduleRepeatingAlarm();
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
	 * Bookkeeping method that should be called before a Twitter transaction is to be performed.
	 * 
	 * @param runnable the {@link Runnable} that is about to connect to Twitter
	 * @param logMsg a log message to include for debugging
	 * @return the number of broadcast receivers
	 */
	private synchronized int startStuff(Runnable runnable, String logMsg) {
	    Log.d(TAG, logMsg);
	    mFetchingThreads.add(runnable);
	    if (mFetchingThreads.size() == 1) {
	        Log.d(TAG, "No other threads running so starting new broadcast.");
	        mWakeLock = getWakeLock();
	        mBroadcastListenerCount = mCallbacks.beginBroadcast();
	    }
	    
	    return mBroadcastListenerCount;
	}

	/**
	 * Bookkeeping method that should be called after a Twitter transaction was performed.
	 * 
	 * @param runnable the {@link Runnable} that ended its Twitter connection
	 * @param logMsg a log message to include for debugging
	 */
	private synchronized void endStuff(Runnable runnable, String logMsg) {
	    Log.d(TAG, logMsg);
	    mFetchingThreads.remove(runnable);
	    if (mFetchingThreads.size() == 0) {
	        Log.d(TAG, "Ending last thread so also ending broadcast.");
	        mWakeLock.release();
	        mCallbacks.finishBroadcast();
	    }
	}

	/**
	 * Returns if a specific Twitter connection is still running.
	 * 
	 * @param runnable the {@link Runnable} the check if it is currently running
	 * @return true, if the Runnable is running, else false
	 */
	private boolean stuffRunning(Runnable runnable) {
	    return mFetchingThreads.contains(runnable);
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
	private void scheduleRepeatingAlarm() {
		final AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		final PendingIntent pIntent = getRepeatingIntent();
		final int frequencyMs = getFetchFrequencyS();
        final long firstTime = SystemClock.elapsedRealtime() + frequencyMs;
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, frequencyMs,   pIntent);
        Log.d(TAG, "Started repeating alarm in a " + frequencyMs + "ms rhythm.");
	}

	/**
	 * Cancels the repeating Alarm that sends the fetch Intent.
	 */
	private void cancelRepeatingAlarm() {
		final AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		final PendingIntent pIntent = getRepeatingIntent();
		am.cancel(pIntent);
		Log.d(TAG, "Cancelled repeating alarm.");
	}

	/**
	 * Returns the recurring AlarmHandler Intent.
	 * @return the Intent 
	 */
	private PendingIntent getRepeatingIntent() {
		Intent intent = new Intent(ACTION_FETCH);
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
	 * Our Handler used to execute operations on the main thread. This is used
	 * to schedule updates of timeline data from Twitter.
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "handleMessage what=" + msg.what);

            SharedPreferences sp = getSp();
            synchronized (sp) {
                mNotificationsEnabled = sp.getBoolean("notifications_enabled", false);
                mNotificationsVibrate = sp.getBoolean("vibration", false);
            }

			switch (msg.what) {
				case MSG_UPDATE_TIMELINE: {
					Thread t = new Thread(mLoadTimeline);
					t.start();
					break;
				}
				case MSG_UPDATE_DIRECT_MESSAGES: {
					Thread t = new Thread(mLoadMessages);
					t.start();
					break;
				}
				default: {
					super.handleMessage(msg);
				}
			}
		}
	};

	/**
	 * Notify the user of new tweets.
	 * 
	 * @param numTweets
	 */
	private void notifyNewTweets(int numTweets, int msgType) {
		Log.d(TAG, "notifyNewTweets n=" + numTweets + "; msgType=" + msgType);

		if (updateWidgetsOnEveryUpdate) {
            // Notify widgets even about the fact, that update occurred
		    //   even if there was nothing new
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
		Notification notification = new Notification(
				R.drawable.notification_icon,
				(String) getText(R.string.notification_title), System
						.currentTimeMillis());

		notification.vibrate = null;
		if (mNotificationsVibrate) {
			notification.vibrate = new long[] { 200, 300, 200, 300 };
		}

		notification.flags = Notification.FLAG_SHOW_LIGHTS
				| Notification.FLAG_AUTO_CANCEL;
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
        String aMessage = ""; 

		switch (msgType) {
		case NOTIFY_REPLIES:
			aMessage = I18n.formatQuantityMessage(getApplicationContext(),
			        R.string.notification_new_mention_format,
			        numTweets,
                    R.array.notification_mention_patterns,
                    R.array.notification_mention_formats);
			messageTitle = R.string.notification_title_mentions;
			Intent intent = new Intent(getApplicationContext(),
					TweetListActivity.class);
			intent.putExtra(SearchManager.QUERY, "@" + sp.getString("twitter_username", null));
			Bundle appDataBundle = new Bundle();
			appDataBundle.putParcelable("content_uri",
					AndTweetDatabase.Tweets.SEARCH_URI);
			intent.putExtra(SearchManager.APP_DATA, appDataBundle);
			intent.setAction(Intent.ACTION_SEARCH);
			contentIntent = PendingIntent.getActivity(getApplicationContext(),
					numTweets, intent, 0);
			break;

		case NOTIFY_DIRECT_MESSAGE:
            aMessage = I18n.formatQuantityMessage(getApplicationContext(),
                    R.string.notification_new_message_format,
                    numTweets,
                    R.array.notification_message_patterns,
                    R.array.notification_message_formats);
			messageTitle = R.string.notification_title_messages;
			contentIntent = PendingIntent.getActivity(this, numTweets,
					new Intent(this, MessageListActivity.class), 0);
			break;

		case NOTIFY_TIMELINE:
		default:
            aMessage = I18n.formatQuantityMessage(getApplicationContext(),
                    R.string.notification_new_tweet_format,
                    numTweets,
                    R.array.notification_tweet_patterns,
                    R.array.notification_tweet_formats);
			messageTitle = R.string.notification_title;
			contentIntent = PendingIntent.getActivity(this, numTweets, new Intent(this, TweetListActivity.class), 0);
			break;
		}

		// Set up the scrolling message of the notification
		notification.tickerText = aMessage;

		// Set the latest event information and send the notification
		notification.setLatestEventInfo(this, getText(messageTitle), aMessage, contentIntent);
		mNM.notify(msgType, notification);
	}

	/** 
	 * Send Update intent to AndTweet Widget(s),
	 * if there are some installed... (e.g. on the Home screen...) 
	 * @see AndTweetAppWidgetProvider
	 */
	private void updateWidgets(int numTweets, int msgType) {
		Intent intent = new Intent(ACTION_APPWIDGET_UPDATE);
		intent.putExtra(EXTRA_NUMTWEETS, numTweets);
		intent.putExtra(EXTRA_MSGTYPE, msgType);
		sendBroadcast(intent);
	}

	protected Runnable mLoadTimeline = new Runnable() {
		public void run() {
            if (stuffRunning(this)) {
                return;
            }
		    final int N = startStuff(this, "Getting tweets and replies.");
		    
			int aNewTweets = 0;
			int aReplyCount = 0;
			try {
	            FriendTimeline fl = new FriendTimeline(AndTweetService.this.getApplicationContext(), AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS);
				fl.loadTimeline();
				aReplyCount = fl.replyCount();
                
				fl = new FriendTimeline(AndTweetService.this.getApplicationContext(), AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
				fl.loadTimeline();
				aNewTweets = fl.newCount();
				aReplyCount += fl.replyCount();
				
				fl.pruneOldRecords();
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

			finishUpdateTimeline(aNewTweets, aReplyCount, N);

			endStuff(this, "Ended getting " + aNewTweets + " tweets and "
			        + aReplyCount + " replies.");
		}
	};

	private void finishUpdateTimeline(int tweetsChanged, int repliesChanged,
			final int N) {
	    
		for (int i = 0; i < N; i++) {
			try {
				Log.d(TAG, "Notifying callback no. " + i);
				IAndTweetServiceCallback cb = mCallbacks.getBroadcastItem(i);
				if (cb != null) {
					cb.tweetsChanged(tweetsChanged);
					cb.repliesChanged(repliesChanged);
				}
			} catch (RemoteException e) {
				Log.e(TAG, e.toString());
			}
		}
		if (tweetsChanged > 0 || repliesChanged == 0) {
			notifyNewTweets(tweetsChanged, NOTIFY_TIMELINE);
		}
		if (repliesChanged > 0) {
			notifyNewTweets(repliesChanged, NOTIFY_REPLIES);
		}
	}

	protected Runnable mLoadMessages = new Runnable() {
		public void run() {
		    if (stuffRunning(this)) {
		        return;
		    }
			final int N = startStuff(this, "Getting direct messages.");

			int aNewMessages = 0;
			try {
	            DirectMessages dm = new DirectMessages(getApplicationContext());
				dm.loadMessages();
				aNewMessages = dm.newCount();
				dm.pruneOldRecords();
			} catch (ConnectionException e) {
				Log.e(TAG, "mLoadMessages Connection Exception: " + e.toString());
			} catch (SQLiteConstraintException e) {
				Log.e(TAG, "mLoadMessages SQLite Exception: " + e.toString());
			} catch (JSONException e) {
				Log.e(TAG, "mLoadMessages JSON Exception: " + e.toString());
			} catch (ConnectionAuthenticationException e) {
				Log.e(TAG, "mLoadMessages Authentication Exception: " + e.toString());
			} catch (ConnectionUnavailableException e) {
				Log.e(TAG, "mLoadMessages FAIL Whale: " + e.toString());
			} catch (SocketTimeoutException e) {
				Log.e(TAG, "mLoadMessages Connection Timeout: " + e.toString());
			}

			finishUpdateDirectMessages(aNewMessages, N);

			endStuff(this, "Ended getting direct messages.");
		}
	};

	private void finishUpdateDirectMessages(int messagesChanged, final int N) {

		for (int i = 0; i < N; i++) {
			try {
				mCallbacks.getBroadcastItem(i).messagesChanged(messagesChanged);
			} catch (RemoteException e) {
				Log.e(TAG, e.toString());
			}
		}

		notifyNewTweets(messagesChanged, NOTIFY_DIRECT_MESSAGE);
	}

    /**
     * Utility method to send the Intent to the {@link AndTweetService} that
     * starts the recurring alarm.
     * 
     * @param context the context to use for sending the Intent
     */
    public static void startAutomaticUpdates(Context context) {
        Intent serviceIntent = new Intent(IAndTweetService.class.getName());
        serviceIntent.putExtra(AndTweetService.EXTRA_MSGTYPE, AndTweetService.ACTION_START_ALARM);
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
        serviceIntent.putExtra(AndTweetService.EXTRA_MSGTYPE, AndTweetService.ACTION_STOP_ALARM);
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
        serviceIntent.putExtra(AndTweetService.EXTRA_MSGTYPE, AndTweetService.ACTION_RESTART_ALARM);
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
}
