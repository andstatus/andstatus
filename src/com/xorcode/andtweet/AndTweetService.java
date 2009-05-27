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
import java.text.ChoiceFormat;
import java.text.MessageFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
import android.preference.PreferenceManager;
import android.util.Log;

import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.data.DirectMessages;
import com.xorcode.andtweet.data.FriendTimeline;
import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;

/**
 * This is an application service that serves as a connection between Android
 * and Twitter. Other applications can interact with it via IPC.
 */
public class AndTweetService extends Service {

	private static final String TAG = "AndTweetService";

	/**
	 * This is a list of callbacks that have been registered with the service.
	 */
	final RemoteCallbackList<IAndTweetServiceCallback> mCallbacks = new RemoteCallbackList<IAndTweetServiceCallback>();

	int mTimelineValue = 0;
	int mReportValue = 0;
	long mLastRunTime = 0;
	long mLastMessageRunTime = 0;
	long mLastTweetId = 0;
	long mLastMessageId = 0;

	private static final int MILLISECONDS = 1000;
	private static final int MSG_UPDATE_TIMELINE = 1;
	private static final int MSG_UPDATE_DIRECT_MESSAGES = 2;
	private static final int MSG_UPDATE_TIMELINE_DONE = 3;
	private static final int MSG_UPDATE_DIRECT_MESSAGES_DONE = 4;
	private static final int MSG_UPDATE_FOLLOWERS = 5;
	private static final int MSG_UPDATE_FOLLOWERS_DONE = 6;

	private static final int NOTIFY_DIRECT_MESSAGE = 1;
	private static final int NOTIFY_TIMELINE = 2;
	private static final int NOTIFY_REPLIES = 3;

	private String mUsername;
	private String mPassword;
	private int mFrequency = 180;
	private boolean mAutomaticUpdates;
	private boolean mNotificationsEnabled;
	private boolean mNotificationsVibrate;

	private NotificationManager mNM;
	private PowerManager.WakeLock mWL;


	@Override
	public void onCreate() {
		// Set up the notification manager.
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		// Set up the power manager.
		Log.d(TAG, "Acquiring wake lock.");
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWL = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		mWL.acquire();
		// Start the time line updater.
		mHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIMELINE, mFrequency * MILLISECONDS);
		mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DIRECT_MESSAGES, mFrequency * MILLISECONDS);
		mHandler.sendEmptyMessageDelayed(MSG_UPDATE_FOLLOWERS, 1800 * MILLISECONDS);
		Log.d(TAG, "Service created in context: " + getApplication().getApplicationContext().getPackageName());
	}

	@Override
	public void onDestroy() {
		// Unregister all callbacks.
		mCallbacks.kill();

		Log.d(TAG, "Service destroyed");

		// Remove messages, stopping loops.
		mHandler.removeMessages(MSG_UPDATE_TIMELINE);
		mHandler.removeMessages(MSG_UPDATE_DIRECT_MESSAGES);
		mHandler.removeMessages(MSG_UPDATE_FOLLOWERS);

		// Remove the wake lock.
		if (mWL != null) {
			Log.d(TAG, "Releasing wake lock.");
			mWL.release();
		}
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
			final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			final int N = mCallbacks.beginBroadcast();

			mUsername = sp.getString("twitter_username", null);
			mPassword = sp.getString("twitter_password", null);

			mNotificationsEnabled = sp.getBoolean("notifications_enabled", false);
			mNotificationsVibrate = sp.getBoolean("vibration", false);
			mAutomaticUpdates = sp.getBoolean("automatic_updates", false);

			mLastRunTime = sp.getLong("last_timeline_runtime", System.currentTimeMillis());
			mLastMessageRunTime = sp.getLong("last_messages_runtime", System.currentTimeMillis());

			mLastTweetId = sp.getLong("last_timeline_id", 0);
			mLastMessageId = sp.getLong("last_message_id", 0);

			final SharedPreferences.Editor prefsEditor = sp.edit();

			switch (msg.what) {
			case MSG_UPDATE_FOLLOWERS:
				if (mAutomaticUpdates) {
					Thread t = new Thread(mLoadFollowers);
					t.start();
				}
				sendMessageDelayed(obtainMessage(MSG_UPDATE_FOLLOWERS), 1800 * MILLISECONDS);
				break;

			case MSG_UPDATE_TIMELINE:
				if (mAutomaticUpdates) {
					Thread t = new Thread(mLoadTimeline);
					t.start();
				} else {
					sendMessageDelayed(obtainMessage(MSG_UPDATE_TIMELINE), mFrequency * MILLISECONDS);
				}
				break;

			case MSG_UPDATE_TIMELINE_DONE:
				mFrequency = Integer.parseInt(sp.getString("fetch_frequency", "180"));
				mLastRunTime = Long.valueOf(System.currentTimeMillis());
				prefsEditor.putLong("last_timeline_runtime", mLastRunTime);
				prefsEditor.putLong("last_timeline_id", mLastTweetId);
				prefsEditor.commit();
				// Broadcast new value to all clients
				for (int i = 0; i < N; i++) {
					try {
						mCallbacks.getBroadcastItem(i).tweetsChanged(msg.arg1);
						mCallbacks.getBroadcastItem(i).repliesChanged(msg.arg2);
					} catch (RemoteException e) {
						Log.e(TAG, e.getMessage());
					}
				}
				mCallbacks.finishBroadcast();
				if (msg.arg1 > 0) {
					notifyNewTweets(msg.arg1, NOTIFY_TIMELINE);
				}
				if (msg.arg2 > 0) {
					notifyNewTweets(msg.arg2, NOTIFY_REPLIES);
				}
				// Repeat mFrequency seconds (defaults to 180, 3 minutes)
				sendMessageDelayed(obtainMessage(MSG_UPDATE_TIMELINE), mFrequency * MILLISECONDS);
				break;

			case MSG_UPDATE_DIRECT_MESSAGES:
				if (mAutomaticUpdates) {
					Thread t = new Thread(mLoadMessages);
					t.start();
				} else {
					sendMessageDelayed(obtainMessage(MSG_UPDATE_DIRECT_MESSAGES), mFrequency * MILLISECONDS);
				}
				break;

			case MSG_UPDATE_DIRECT_MESSAGES_DONE:
				mLastMessageRunTime = Long.valueOf(System.currentTimeMillis());
				prefsEditor.putLong("last_messages_runtime", mLastMessageRunTime);
				prefsEditor.putLong("last_message_id", mLastMessageId);
				prefsEditor.commit();
				// Broadcast new value to all clients
				for (int i = 0; i < N; i++) {
					try {
						mCallbacks.getBroadcastItem(i).messagesChanged(msg.arg1);
					} catch (RemoteException e) {
						Log.e(TAG, e.getMessage());
					}
				}
				mCallbacks.finishBroadcast();
				if (msg.arg1 > 0) {
					// Notify user
					notifyNewTweets(msg.arg1, NOTIFY_DIRECT_MESSAGE);
				}
				// Repeat mFrequency seconds (defaults to 180, 3 minutes)
				sendMessageDelayed(obtainMessage(MSG_UPDATE_DIRECT_MESSAGES), mFrequency * MILLISECONDS);
				break;

			default:
				super.handleMessage(msg);
			}
		}
	};

	/**
	 * Notify the user of new tweets.
	 * 
	 * @param numTweets
	 */
	private void notifyNewTweets(int numTweets, int msgType) {
		// If no notifications are enabled, return
		if (!mNotificationsEnabled) {
			return;
		}

		final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean notificationsMessages = sp.getBoolean("notifications_messages", false);
		boolean notificationsReplies = sp.getBoolean("notifications_mentions", false);
		boolean notificationsTimeline = sp.getBoolean("notifications_timeline", false);

		// Make sure that notifications haven't been turned off for the message type
		switch (msgType) {
		case NOTIFY_REPLIES:
			if (!notificationsReplies) return;
			break;
		case NOTIFY_DIRECT_MESSAGE:
			if (!notificationsMessages) return;
			break;
		case NOTIFY_TIMELINE:
			if (!notificationsTimeline) return;
			break;
		}

		// Set up the notification to display to the user
		Notification notification = new Notification(android.R.drawable.stat_notify_chat,
				(String) getText(R.string.notification_title), System.currentTimeMillis());

		notification.vibrate = null;
		if (mNotificationsVibrate) {
			notification.vibrate = new long[] { 200, 300, 200, 300 };
		}

		notification.flags = Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
		notification.ledOffMS = 1000;
		notification.ledOnMS = 500;
		notification.ledARGB = Color.GREEN;

		String ringtone = sp.getString(PreferencesActivity.KEY_RINGTONE_PREFERENCE, null);
		if ("".equals(ringtone) || ringtone == null) {
			notification.sound = null;
		} else {
			Uri ringtoneUri = Uri.parse(ringtone);
			notification.sound = ringtoneUri;
		}

		// Set up the pending intent
		PendingIntent contentIntent;

		int messageTitle;
		int messageFormat;
		int singular;
		int plural;

		switch (msgType) {
		case NOTIFY_REPLIES:
			messageFormat = R.string.notification_new_mention_format;
			singular = R.string.notification_mention_singular;
			plural = R.string.notification_mention_plural;
			messageTitle = R.string.notification_title_mentions;
			Intent intent = new Intent(getApplicationContext(), TweetListActivity.class);
			intent.putExtra(SearchManager.QUERY, "@" + mUsername);
			Bundle appDataBundle = new Bundle();
			appDataBundle.putParcelable("content_uri", AndTweetDatabase.Tweets.SEARCH_URI);
			intent.putExtra(SearchManager.APP_DATA, appDataBundle);
			intent.setAction(Intent.ACTION_SEARCH);
			contentIntent = PendingIntent.getActivity(getApplicationContext(), numTweets, intent, 0);
			break;

		case NOTIFY_DIRECT_MESSAGE:
			messageFormat = R.string.notification_new_message_format;
			singular = R.string.notification_message_singular;
			plural = R.string.notification_message_plural;
			messageTitle = R.string.notification_title_messages;
			contentIntent = PendingIntent.getActivity(this, numTweets, new Intent(this, MessageListActivity.class), 0);
			break;

		case NOTIFY_TIMELINE:
		default:
			messageFormat = R.string.notification_new_tweet_format;
			singular = R.string.notification_tweet_singular;
			plural = R.string.notification_tweet_plural;
			messageTitle = R.string.notification_title;
			contentIntent = PendingIntent.getActivity(this, numTweets, new Intent(this, TweetListActivity.class), 0);
			break;
		}

		// Set up the message
		MessageFormat form = new MessageFormat(getText(messageFormat).toString());
		Object[] formArgs = new Object[] {numTweets};
		double[] tweetLimits = {1,2};
		String[] tweetPart = { getText(singular).toString(), getText(plural).toString() };
		ChoiceFormat tweetForm = new ChoiceFormat(tweetLimits, tweetPart);
		form.setFormatByArgumentIndex(0, tweetForm);
		String aMessage = form.format(formArgs); 

		// Set up the scrolling message of the notification
		notification.tickerText = aMessage;

		// Set the latest event information and send the notification
		notification.setLatestEventInfo(this, getText(messageTitle), aMessage, contentIntent);
		mNM.notify(msgType, notification);
	}

	protected Runnable mLoadTimeline = new Runnable() {
		public void run() {
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			mUsername = sp.getString("twitter_username", null);
			mPassword = sp.getString("twitter_password", null);
			FriendTimeline friendTimeline = new FriendTimeline(getContentResolver(), mUsername, mPassword, mLastTweetId);
			int aNewTweets = 0;
			int aReplyCount = 0;
			try {
				friendTimeline.loadTimeline(AndTweetDatabase.Tweets.TWEET_TYPE_REPLY);
				aReplyCount = friendTimeline.replyCount();
				friendTimeline.loadTimeline(AndTweetDatabase.Tweets.TWEET_TYPE_TWEET);
				aNewTweets = friendTimeline.newCount();
				aReplyCount += friendTimeline.replyCount();
				mLastTweetId = friendTimeline.lastId();
			} catch (ConnectionException e) {
				Log.e(TAG, "mLoadTimeline Connection Exception: " + e.getMessage());
			} catch (SQLiteConstraintException e) {
				Log.e(TAG, "mLoadTimeline SQLite Exception: " + e.getMessage());
			} catch (JSONException e) {
				Log.e(TAG, "mLoadTimeline JSON Exception: " + e.getMessage());
			} catch (ConnectionAuthenticationException e) {
				Log.e(TAG, "mLoadTimeline Authentication Exception: " + e.getMessage());
			} catch (ConnectionUnavailableException e) {
				Log.e(TAG, "mLoadTimeline FAIL Whale: " + e.getMessage());
			} catch (SocketTimeoutException e) {
				Log.e(TAG, "mLoadTimeline Connection Timeout: " + e.getMessage());
			}
			friendTimeline.pruneOldRecords(System.currentTimeMillis() - (86400 * 3 * MILLISECONDS));
			mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_TIMELINE_DONE, aNewTweets, aReplyCount));
		}
	};

	protected Runnable mLoadMessages = new Runnable() {
		public void run() {
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			mUsername = sp.getString("twitter_username", null);
			mPassword = sp.getString("twitter_password", null);
			DirectMessages directMessages = new DirectMessages(getContentResolver(), mUsername, mPassword, mLastMessageId);
			int aNewMessages = 0;
			try {
				directMessages.loadMessages();
				aNewMessages = directMessages.newCount();
				mLastMessageId = directMessages.lastId();
			} catch (ConnectionException e) {
				Log.e(TAG, "mLoadMessages Connection Exception: " + e.getMessage());
			} catch (SQLiteConstraintException e) {
				Log.e(TAG, "mLoadMessages SQLite Exception: " + e.getMessage());
			} catch (JSONException e) {
				Log.e(TAG, "mLoadMessages JSON Exception: " + e.getMessage());
			} catch (ConnectionAuthenticationException e) {
				Log.e(TAG, "mLoadMessages Authentication Exception: " + e.getMessage());
			} catch (ConnectionUnavailableException e) {
				Log.e(TAG, "mLoadMessages FAIL Whale: " + e.getMessage());
			} catch (SocketTimeoutException e) {
				Log.e(TAG, "mLoadMessages Connection Timeout: " + e.getMessage());
			}
			directMessages.pruneOldRecords(System.currentTimeMillis() - (86400 * 3 * MILLISECONDS));
			mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_DIRECT_MESSAGES_DONE, aNewMessages, 0));
		}
	};

	protected Runnable mLoadFollowers = new Runnable() {
		public void run() {
			final ContentResolver contentResolver = getContentResolver();
			final SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext());
			mUsername = sp.getString("twitter_username", null);
			mPassword = sp.getString("twitter_password", null);
			if (mUsername != null && mUsername.length() > 0) {
				Connection aConn = new Connection(mUsername, mPassword);
				try {
					JSONArray jArr = aConn.getFollowers();
					if (jArr != null) {
						for (int index = 0; index < jArr.length(); index++) {
							JSONObject jo = jArr.getJSONObject(index);
							ContentValues values = new ContentValues();
		
							// Construct the Uri to existing record
							Long lUserId = Long.parseLong(jo.getString("id"));
							Uri aUserUri = ContentUris.withAppendedId(AndTweetDatabase.Users.CONTENT_URI, lUserId);
		
							values.put(AndTweetDatabase.Users._ID, lUserId.toString());
							values.put(AndTweetDatabase.Users.AUTHOR_ID, jo.getString("screen_name"));

							if ((contentResolver.update(aUserUri, values, null, null)) == 0) {
								contentResolver.insert(AndTweetDatabase.Users.CONTENT_URI, values);
							}
						}
						getContentResolver().notifyChange(AndTweetDatabase.Users.CONTENT_URI, null);
					}
				} catch (JSONException e) {
					Log.e(TAG, e.getMessage());
				} catch (ConnectionException e) {
					Log.e(TAG, "loadFollowers Connection Exception: " + e.getMessage());
				} catch (ConnectionAuthenticationException e) {
					Log.e(TAG, "loadFollowers Authentication Exception: " + e.getMessage());
				} catch (ConnectionUnavailableException e) {
					Log.e(TAG, "loadFollowers FAIL Whale Exception: " + e.getMessage());
				} catch (SocketTimeoutException e) {
					Log.e(TAG, "loadFollowers Timeout Exception: " + e.getMessage());
				}
				mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_FOLLOWERS_DONE));
			}
		}
	};
}
