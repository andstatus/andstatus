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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.TwitterUser.CredentialsVerified;

/**
 * @author torgny.bjers
 */
public class TimelineActivity extends ListActivity implements ITimelineActivity {

    private static final String TAG = TimelineActivity.class.getSimpleName();

	// Handler message codes
	public static final int MSG_TWEETS_CHANGED = 1;
	public static final int MSG_DATA_LOADING = 2;
	/**
	 * My tweet ("What's happening?"...) is being sending
	 */
	public static final int MSG_UPDATE_STATUS = 3;
	public static final int MSG_MANUAL_RELOAD = 4;
	public static final int MSG_AUTHENTICATION_ERROR = 5;
	public static final int MSG_LOAD_ITEMS = 6;
	public static final int MSG_DIRECT_MESSAGES_CHANGED = 7;
	public static final int MSG_SERVICE_UNAVAILABLE_ERROR = 8;
	public static final int MSG_REPLIES_CHANGED = 9;
	public static final int MSG_UPDATED_TITLE = 10;
	public static final int MSG_CONNECTION_TIMEOUT_EXCEPTION = 11;
	public static final int MSG_STATUS_DESTROY = 12;
	public static final int MSG_FAVORITE_CREATE = 13;
	public static final int MSG_FAVORITE_DESTROY = 14;
	public static final int MSG_CONNECTION_EXCEPTION = 15;

	// Handler message status codes
	public static final int STATUS_LOAD_ITEMS_FAILURE = 0;
	public static final int STATUS_LOAD_ITEMS_SUCCESS = 1;

	// Dialog identifier codes
	public static final int DIALOG_AUTHENTICATION_FAILED = 1;
	public static final int DIALOG_SENDING_MESSAGE = 2;
	public static final int DIALOG_SERVICE_UNAVAILABLE = 3;
	public static final int DIALOG_EXTERNAL_STORAGE = 4;
	public static final int DIALOG_TIMELINE_LOADING = 5;
	public static final int DIALOG_EXTERNAL_STORAGE_MISSING = 6;
	public static final int DIALOG_CONNECTION_TIMEOUT = 7;
	public static final int DIALOG_EXECUTING_COMMAND = 8;

	// Intent bundle result keys
	public static final String INTENT_RESULT_KEY_AUTHENTICATION = "authentication";

	// Bundle identifier keys
	public static final String BUNDLE_KEY_REPLY_ID = "replyId";
	public static final String BUNDLE_KEY_CURRENT_ID = "currentId";
	public static final String BUNDLE_KEY_CURRENT_PAGE = "currentPage";
	public static final String BUNDLE_KEY_IS_LOADING = "isLoading";

	private static final String LAST_POS_KEY = "last_position";
	
	public static final int MILLISECONDS = 1000;

	/**
	 *  List footer for loading messages,
	 *   appears at the bottom of the list of tweets
	 *  In fact, it is not visible but it is used to find out
	 *   when User wants to see items that were not loaded into the list...
	 */
	protected LinearLayout mListFooter;

	protected Cursor mCursor;
	protected NotificationManager mNM;
	protected SharedPreferences mSP;
	protected ProgressDialog mProgressDialog;
	protected Handler mHandler;

	/**
	 * "Number of pages of tweets" loaded into the list of Tweets.
	 * Start from one page and increase it (and load more Tweets)
	 *  in a case User scrolls down to the end of list.
	 * This value limits _maximum_ number of rows queried from AndTweetProvider (query 'limit'),
	 *  so actual number of Tweets loaded may be less...
	 * TODO: Now "page size" is hard coded = 20 ... 
	 */
	protected int mCurrentPage = 1;
	/**
	 * Number of items (Tweets) in the list.
	 * It is used to find out when we need to load more items.
	 */
	protected int mTotalItemCount = 0;

	/**
	 * Is connected to the application service?
	 */
	protected boolean mIsBound;
    /**
     * See mServiceCallback also
     */
    protected IAndTweetService mService;
	
	/**
	 * Items are being loaded into the list (asynchronously...)
	 */
	protected boolean mIsLoading;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "onCreate");
        }

		// Set up preference manager
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		mSP = PreferenceManager.getDefaultSharedPreferences(this);

		// Request window features before loading the content view
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

		if (TwitterUser.getTwitterUser(this, false).getCredentialsVerified() != CredentialsVerified.SUCCEEDED) {
			startActivity(new Intent(this, SplashActivity.class));
			finish();
		}

		loadTheme();
		setContentView(R.layout.tweetlist);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.timeline_title);
		updateTitle();

		/*
		if (mSP.getBoolean("storage_use_external", false)) {
			if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				showDialog(DIALOG_EXTERNAL_STORAGE_MISSING);
			}
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
				Toast.makeText(this, "External storage mounted read-only. Cannot write to database. Please re-mount your storage and try again.", Toast.LENGTH_LONG).show();
				destroyService();
				finish();
			}
		}

		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			if (!mSP.getBoolean("confirmed_external_storage_use", false)) {
				showDialog(DIALOG_EXTERNAL_STORAGE);
			}
		}
		*/

		// Set up notification manager
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}
	
	@Override
	protected void onResume() {
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "onResume");
        }
		super.onResume();
		loadPosition();
	}
	
	private void savePosition() {
		final int firstItem = getListView().getFirstVisiblePosition();
		final long firstItemId = getListView().getAdapter().getItemId(firstItem);
		final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		final SharedPreferences.Editor prefsEditor = sp.edit();
		prefsEditor.putLong(LAST_POS_KEY, firstItemId);
		prefsEditor.commit();
	}

	/**
	 * TODO: This won't work in a case the tweet was not loaded into the list. 
	 *  So maybe we need to control that "Number of pages of tweets" (mCurrentPage) also
	 *  ... maybe not :-)
	 */
	private void loadPosition() {
		final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		try {
			final long firstItemId = sp.getLong(LAST_POS_KEY, -1);
			int scrollPos = listPosForId(firstItemId);
			if (scrollPos > 0) {
				getListView().setSelectionFromTop(scrollPos - 1, 0);
			}
			else {
				scrollPos = getListView().getCount() - 1;
				setSelectionAtBottom(scrollPos);
			}
		} catch (Exception e) {
			Editor ed = sp.edit();
			ed.remove(LAST_POS_KEY);
			ed.commit();
		}
	}


	private void setSelectionAtBottom(int scrollPos) {
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "setSelectionAtBottom, 1");
        }
		final int viewHeight = getListView().getHeight();
		final int childHeight;
			childHeight = 30;
		final int y = viewHeight - childHeight;
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "set position of last item to " + y);
        }
		getListView().setSelectionFromTop(scrollPos, y);
	}

	/**
	 * Returns the position of the item with the given ID.
	 * 
	 * @param searchedId the ID of the item whose position 
	 * in the list is to be returned.
	 * 
	 * @return the position in the list or -1 if the item was not found
	 */
	private int listPosForId(long searchedId) {
		int listPos;
		boolean itemFound = false;
		final ListView lv = getListView();
		final int itemCount = lv.getCount();
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "item count: "+ itemCount);
        }
		for (listPos = 0; (!itemFound && (listPos < itemCount)); listPos++) {
			long itemId = lv.getItemIdAtPosition(listPos);
			itemFound = (itemId == searchedId);
		}
		
		if (!itemFound) {
			listPos = -1;
		}
		return listPos;
	}
	
	
	@Override
	public void onContentChanged() {
		super.onContentChanged();
        if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
            Log.d(TAG, "Content changed");
        }
	}
	
	
	
	@Override
    protected void onPause() {
        super.onPause();
        // The activity just lost its focus, 
        // so we have to start notifying the User about new events after his moment.
        clearNotifications();
		savePosition();
    }

	private void clearNotifications() {
		try {
			// TODO: Check if there are any notifications
			// and if none than don't waist time for this:
			
			mNM.cancelAll();
	
			// Reset notifications on AppWidget(s)
			Intent intent = new Intent(AndTweetService.ACTION_APPWIDGET_UPDATE);
			intent.putExtra(AndTweetService.EXTRA_MSGTYPE, AndTweetService.NOTIFY_CLEAR);
			sendBroadcast(intent); }
		finally {
			// Nothing yet...
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_AUTHENTICATION_FAILED:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_authentication_failed)
				.setMessage(R.string.dialog_summary_authentication_failed)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
						startActivity(new Intent(TimelineActivity.this, PreferencesActivity.class));
					}
				}).create();

		case DIALOG_SERVICE_UNAVAILABLE:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_service_unavailable)
				.setMessage(R.string.dialog_summary_service_unavailable)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
					}
				}).create();

		case DIALOG_SENDING_MESSAGE:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
			mProgressDialog.setTitle(R.string.dialog_title_sending_message);
			mProgressDialog.setMessage(getText(R.string.dialog_summary_sending_message));
			return mProgressDialog;

		case DIALOG_TIMELINE_LOADING:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
			mProgressDialog.setTitle(R.string.dialog_title_timeline_loading);
			mProgressDialog.setMessage(getText(R.string.dialog_summary_timeline_loading));
			return mProgressDialog;

		case DIALOG_EXTERNAL_STORAGE:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.dialog_title_external_storage)
				.setMessage(R.string.dialog_summary_external_storage)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
						SharedPreferences.Editor editor = mSP.edit();
						editor.putBoolean("confirmed_external_storage_use", true);
						editor.putBoolean("storage_use_external", true);
						editor.commit();
						destroyService();
						finish();
						Intent intent = new Intent(TimelineActivity.this, TweetListActivity.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					}
				})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
						SharedPreferences.Editor editor = mSP.edit();
						editor.putBoolean("confirmed_external_storage_use", true);
						editor.commit();
					}
				}).create();

		case DIALOG_EXTERNAL_STORAGE_MISSING:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_external_storage_missing)
				.setMessage(R.string.dialog_summary_external_storage_missing)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
						SharedPreferences.Editor editor = mSP.edit();
						editor.putBoolean("confirmed_external_storage_use", true);
						editor.putBoolean("storage_use_external", false);
						editor.commit();
						destroyService();
						finish();
						Intent intent = new Intent(TimelineActivity.this, TweetListActivity.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					}
				})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
						destroyService();
						finish();
					}
				}).create();

		case DIALOG_CONNECTION_TIMEOUT:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_connection_timeout)
				.setMessage(R.string.dialog_summary_connection_timeout)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
					}
				}).create();

		case DIALOG_EXECUTING_COMMAND:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
			mProgressDialog.setTitle(R.string.dialog_title_executing_command);
			mProgressDialog.setMessage(getText(R.string.dialog_summary_executing_command));
			return mProgressDialog;

		default:
			return super.onCreateDialog(id);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.timeline, menu);

		Intent intent = new Intent(null, getIntent().getData());
		intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
		menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, new ComponentName(this, TweetListActivity.class), null, intent, 0, null);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;
		Bundle appDataBundle;

		switch (item.getItemId()) {
		case R.id.preferences_menu_id:
			startActivity(new Intent(this, PreferencesActivity.class));
			break;

		case R.id.friends_timeline_menu_id:
			intent = new Intent(this, TweetListActivity.class);
			appDataBundle = new Bundle();
			appDataBundle.putParcelable("content_uri", AndTweetDatabase.Tweets.SEARCH_URI);
			// TODO: Do we really need these "selection" and "selectionArgs"?
			// There are NO other tweet types (yet) except 1 and 2...
			appDataBundle.putString("selection", AndTweetDatabase.Tweets.TWEET_TYPE + " IN (?, ?)");
			appDataBundle.putStringArray("selectionArgs", new String[] { String.valueOf(Tweets.TWEET_TYPE_TWEET), String.valueOf(Tweets.TWEET_TYPE_REPLY) });
			intent.putExtra(SearchManager.APP_DATA, appDataBundle);
			intent.setAction(Intent.ACTION_SEARCH);
			startActivity(intent);
			break;

		case R.id.direct_messages_menu_id:
			intent = new Intent(this, MessageListActivity.class);
			appDataBundle = new Bundle();
			appDataBundle.putParcelable("content_uri", AndTweetDatabase.DirectMessages.SEARCH_URI);
			intent.putExtra(SearchManager.APP_DATA, appDataBundle);
			intent.setAction(Intent.ACTION_SEARCH);
			startActivity(intent);
			break;

		case R.id.search_menu_id:
			onSearchRequested();
			break;

		case R.id.mentions_menu_id:
		    // Mentions is now query of the TweetList view...
			intent = new Intent(this, TweetListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			String username = mSP.getString("twitter_username", null);
			if (username != null) {
				intent.putExtra(SearchManager.QUERY, "@" + username);
			}
			appDataBundle = new Bundle();
			appDataBundle.putParcelable("content_uri", AndTweetDatabase.Tweets.SEARCH_URI);
			intent.putExtra(SearchManager.APP_DATA, appDataBundle);
			intent.setAction(Intent.ACTION_SEARCH);
			startActivity(intent);
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {}

	public boolean onKey(View v, int keyCode, KeyEvent event) {
		return false;
	}

	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {}

	public void onScrollStateChanged(AbsListView view, int scrollState) {}

	/**
	 * Load the theme for preferences.
	 */
	public void loadTheme() {
		boolean light = mSP.getBoolean("appearance_light_theme", false);
		StringBuilder theme = new StringBuilder();
		String name = mSP.getString("theme", "AndTweet");
		if (name.indexOf("Theme.") > -1) {
			name = name.substring(name.indexOf("Theme."));
		}
		theme.append("Theme.");
		if (light) {
			theme.append("Light.");
		}
		theme.append(name);
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "loadTheme; theme=\"" + theme.toString() + "\"");
        }
		setTheme((int) getResources().getIdentifier(theme.toString(), "style", "com.xorcode.andtweet"));
	}

	/**
	 * Sets the title with a left and right title.
	 * 
	 * @param leftText Left title part
	 * @param rightText Right title part
	 */
	public void setTitle(CharSequence leftText, CharSequence rightText) {
		TextView leftTitle = (TextView) findViewById(R.id.custom_title_left_text);
		TextView rightTitle = (TextView) findViewById(R.id.custom_title_right_text);
		leftTitle.setText(leftText);
		rightTitle.setText(rightText);
	}

	/**
	 * Updates the activity title.
	 */
	public void updateTitle() {
		String username = mSP.getString("twitter_username", null);
		setTitle(getString(R.string.activity_title_format, new Object[] {getTitle(), username}), "");
	}

	/**
	 * Retrieve the text that is currently in the editor.
	 * 
	 * @return Text currently in the editor
	 */
	protected CharSequence getSavedText() {
		return ((EditText) findViewById(R.id.edtTweetInput)).getText();
	}

	/**
	 * Set the text in the text editor.
	 * 
	 * @param text
	 */
	protected void setSavedText(CharSequence text) {
		((EditText) findViewById(R.id.edtTweetInput)).setText(text);
	}

	/**
	 * Initialize the user interface.
	 */
	protected void initUI() {
		// Attach listeners to the message list
		getListView().setOnCreateContextMenuListener(this);
		getListView().setOnItemClickListener(this);
	}

	/**
	 * Check to see if the system has a hardware keyboard.
	 * @return
	 */
	protected boolean hasHardwareKeyboard() {
		Configuration c = getResources().getConfiguration();
		switch (c.keyboard) {
		case Configuration.KEYBOARD_12KEY:
		case Configuration.KEYBOARD_QWERTY:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Initialize service and bind to it.
	 */
	protected void bindToService() {
		if (mSP.contains("automatic_updates") && mSP.getBoolean("automatic_updates", false)) {
			Intent serviceIntent = new Intent(IAndTweetService.class.getName());
			if (!mIsBound) {
				startService(serviceIntent);
				mIsBound = true;
			}
			bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
		}
	}

	/**
	 * Disconnect and unregister the service.
	 */
	protected void disconnectService() {
		if (mIsBound) {
			if (mService != null) {
				try {
					mService.unregisterCallback(mServiceCallback);
				} catch (RemoteException e) {
					// Service crashed, not much we can do.
				}
			}
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	/**
	 * Disconnects from the service and stops it.
	 */
	protected void destroyService() {
		disconnectService();
		stopService(new Intent(IAndTweetService.class.getName()));
		mService = null;
		mIsBound = false;
	}

	/**
	 * Service connection handler.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = IAndTweetService.Stub.asInterface(service);
			// We want to monitor the service for as long as we are
			// connected to it.
			try {
				mService.registerCallback(mServiceCallback);
			} catch (RemoteException e) {
				// Service has already crashed, nothing much we can do
				// except hope that it will restart.
			}
		}

		public void onServiceDisconnected(ComponentName name) {
			mService = null;
		}
	};

	/**
	 * Service callback handler.
	 */
	protected IAndTweetServiceCallback mServiceCallback = new IAndTweetServiceCallback.Stub() {
		/**
		 * Tweets changed callback method
		 * 
		 * @param value
		 * @throws RemoteException
		 */
		public void tweetsChanged(int value) throws RemoteException {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_TWEETS_CHANGED, value, 0));
		}

		/**
		 * dataLoading callback method
		 * 
		 * @param value
		 * @throws RemoteException
		 */
		public void dataLoading(int value) throws RemoteException {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_DATA_LOADING, value, 0));
		}

		/**
		 * Messages changed callback method
		 * 
		 * @param value
		 * @throws RemoteException
		 */
		public void messagesChanged(int value) throws RemoteException {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_DIRECT_MESSAGES_CHANGED, value, 0));
		}

		/**
		 * Replies changed callback method
		 * 
		 * @param value
		 * @throws RemoteException
		 */
		public void repliesChanged(int value) throws RemoteException {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_REPLIES_CHANGED, value, 0));
		}
	};
}
