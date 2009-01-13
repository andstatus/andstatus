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

package com.xorcode.andtweet.view;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import com.xorcode.andtweet.IAndTweetService;
import com.xorcode.andtweet.IAndTweetServiceCallback;
import com.xorcode.andtweet.R;
import com.xorcode.andtweet.data.AndTweet;
import com.xorcode.andtweet.data.FriendTimeline;
import com.xorcode.andtweet.data.TweetBinder;
import com.xorcode.andtweet.data.AndTweet.Tweets;
import com.xorcode.andtweet.data.AndTweet.Users;
import com.xorcode.andtweet.util.AtTokenizer;

/**
 * @author torgny.bjers
 * 
 */
public class TweetList extends Activity {

	private static final String TAG = "AndTweet";

	private static final int REQUEST_CODE_PREFERENCES = 1;

	public static final int OPTIONS_MENU_PREFERENCES = Menu.FIRST;
	public static final int OPTIONS_MENU_RELOAD = Menu.FIRST + 1;

	public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 2;
	public static final int CONTEXT_MENU_ITEM_STAR = Menu.FIRST + 3;

	private static final String[] TWEETS_PROJECTION = new String[] {
		Tweets._ID,
		Tweets.AUTHOR_ID,
		Tweets.MESSAGE,
		Tweets.SENT_DATE
	};

	private static final String[] FRIENDS_PROJECTION = new String[] {
		Users._ID,
		Users.AUTHOR_ID
	};

	private static final int MSG_TWEETS_CHANGED = 1;
	private static final int MSG_DATA_LOADING = 2;
	private static final int MSG_UPDATE_STATUS = 3;
	private static final int MSG_MANUAL_RELOAD = 4;

	private NotificationManager mNM;
	private SharedPreferences mSP;

	/**
	 * Send button
	 */
	private Button mSendButton;

	/**
	 * Message edit auto-complete TextView
	 */
	private MultiAutoCompleteTextView mEditText;

	/**
	 * Remaining characters
	 */
	private TextView mCharsLeftText;

	/**
	 * Current character count
	 */
	private int mCurrentChars = 0;

	/**
	 * Character limit
	 */
	private int mLimitChars = 140;

	/**
	 * Message List
	 */
	private ListView mMessageList;

	/**
	 * Service interface connection
	 */
	IAndTweetService mService = null;

	/**
	 * Service binding indicator
	 */
	private static boolean mIsBound;

	/**
	 * In reply-to ID
	 */
	private long mReplyId = 0;

	/**
	 * Progress dialog for notifying user about events.
	 */
	private ProgressDialog mProgressDialog;

	/**
	 * Called when the activity is first created.
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 * 
	 * @param savedInstanceState
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set up notification manager
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		// Request window features
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		// Set up preference manager
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		mSP = PreferenceManager.getDefaultSharedPreferences(this);
		if (mSP.getString("twitter_password", "").length() == 0 || mSP.getString("twitter_username", "").length() == 0) {
			startActivity(new Intent(this, Preferences.class));
		}

        // Set up the content view and load data
        setContentView(R.layout.tweetlist);

		// Set up views
		mMessageList = (ListView) findViewById(R.id.messagesListView);
		mSendButton = (Button) findViewById(R.id.messageEditSendButton);
		mEditText = (MultiAutoCompleteTextView) findViewById(R.id.messageEditTextAC);
		mCharsLeftText = (TextView) findViewById(R.id.messageEditCharsLeftTextView);

		Intent intent = getIntent();
		if (intent.getData() == null) {
			intent.setData(Tweets.CONTENT_URI);
		}

		initUI();
	}

	@Override
	protected void onStart() {
		super.onStart();
		bindToService();
		fillList();
		loadFriends();
		mReplyId = 0;
		mEditText.requestFocus();
	}

	@Override
	protected void onStop() {
		super.onStop();
		disconnectService();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		disconnectService();
		clearNotifications();
		mNM.cancel(R.string.app_name);
	}

	/**
	 * Retrieve the text that is currently in the editor.
	 * 
	 * @return Text currently in the editor
	 */
	CharSequence getSavedText() {
		return ((MultiAutoCompleteTextView) findViewById(R.id.messageEditTextAC)).getText();
	}

	/**
	 * Set the text in the text editor.
	 * 
	 * @param text
	 */
	void setSavedText(CharSequence text) {
		((MultiAutoCompleteTextView) findViewById(R.id.messageEditTextAC)).setText(text);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, OPTIONS_MENU_PREFERENCES, 0, R.string.options_menu_preferences).setShortcut(
				'1', 'p').setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, OPTIONS_MENU_RELOAD, 1, R.string.options_menu_reload).setShortcut(
				'2', 'r').setIcon(android.R.drawable.ic_menu_rotate);

		Intent intent = new Intent(null, getIntent().getData());
		intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
		menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, new ComponentName(this,
				TweetList.class), null, intent, 0, null);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case OPTIONS_MENU_PREFERENCES:
			Intent launchPreferencesIntent = new Intent().setClass(this, Preferences.class);
			startActivityForResult(launchPreferencesIntent, REQUEST_CODE_PREFERENCES);
			return true;
		case OPTIONS_MENU_RELOAD:
			setProgressBarIndeterminateVisibility(true);
			Thread thread = new Thread(mManualReload);
			thread.start();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CODE_PREFERENCES) {
			if (!mSP.getBoolean("automatic_updates", false)) {
				destroyService();
			}
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		// Add menu items
		menu.add(0, CONTEXT_MENU_ITEM_REPLY, 0, R.string.menu_item_reply);
		menu.add(0, CONTEXT_MENU_ITEM_STAR, 1, R.string.menu_item_star);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return false;
		}

		switch (item.getItemId()) {
		case CONTEXT_MENU_ITEM_REPLY:
			Uri uri = ContentUris.withAppendedId(getIntent().getData(), info.id);
			Cursor c = managedQuery(uri, new String[] { Tweets._ID, Tweets.AUTHOR_ID }, null, null, null);
			try {
				c.moveToFirst();
				mEditText.requestFocus();
				String reply = "@" + c.getString(c.getColumnIndex(Tweets.AUTHOR_ID)) + " ";
				mEditText.setText("");
				mEditText.append(reply, 0, reply.length());
				mReplyId = c.getLong(c.getColumnIndex(Tweets._ID));
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
			return true;
		case CONTEXT_MENU_ITEM_STAR:
			return true;
		}
		return false;
	}

	/**
	 * Initialize service and bind to it
	 */
	private void bindToService() {
		if (mSP.contains("automatic_updates") && mSP.getBoolean("automatic_updates", false)) {
			Log.d(TAG, "Automatic updates enabled");
			Intent serviceIntent = new Intent(IAndTweetService.class.getName());
			if (!mIsBound) {
				startService(serviceIntent);
				mIsBound = true;
			}
			bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
		}
	}

	/**
	 * Initialize UI
	 */
	private void initUI() {
		mSendButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setProgressBarIndeterminateVisibility(true);
				mProgressDialog = ProgressDialog.show(TweetList.this, getText(R.string.dialog_title_sending_message), getText(R.string.dialog_summary_sending_message), true, false);
				Thread thread = new Thread(mSendUpdate);
				thread.start();
			}
		});

		mCharsLeftText.setText(String.valueOf(mLimitChars - mEditText.length()));

		// Attach listeners to the text field
		mEditText.setOnFocusChangeListener(mEditTextFocusChangeListener);
		mEditText.setOnKeyListener(mEditTextKeyListener);
	}

	/**
	 * Fill the ListView with Tweet items.
	 */
	private void fillList() {
		Cursor cursor = managedQuery(getIntent().getData(), TWEETS_PROJECTION, null, null,
				Tweets.DEFAULT_SORT_ORDER + " LIMIT 20");
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.tweetlist_item,
				cursor, new String[] {
					AndTweet.Tweets.AUTHOR_ID, AndTweet.Tweets.MESSAGE, AndTweet.Tweets.SENT_DATE
				}, new int[] {
					R.id.tweetlist_item_screen_name, R.id.tweetlist_item_text,
					R.id.tweetlist_item_date
				});
		adapter.setViewBinder(new TweetBinder());
		mMessageList.setAdapter(adapter);
		mMessageList.setOnCreateContextMenuListener(this);
		mMessageList.setOnItemClickListener(mOnItemClickListener);
	}

	/**
	 * Fill in the context menu with User items.
	 */
	private void loadFriends() {
		Cursor cursor = managedQuery(Users.CONTENT_URI, FRIENDS_PROJECTION, null, null,
				Users.DEFAULT_SORT_ORDER);
		ArrayList<String> aFriends = new ArrayList<String>();
		while (cursor.moveToNext()) {
			aFriends.add(cursor.getString(cursor.getColumnIndex(Users.AUTHOR_ID)));
		}
		ArrayAdapter<String> friendsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, aFriends);
		mEditText.setAdapter(friendsAdapter);
		mEditText.setTokenizer(new AtTokenizer());
	}

	/**
	 * Disconnect and unregister the service.
	 */
	private void disconnectService() {
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
	 * Destroy the service.
	 */
	private void destroyService() {
		disconnectService();
		stopService(new Intent(IAndTweetService.class.getName()));
		mService = null;
		mIsBound = false;
	}

	private void clearNotifications() {
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(R.string.app_name);
	}

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

	private IAndTweetServiceCallback mServiceCallback = new IAndTweetServiceCallback.Stub() {
		/**
		 * Value changed callback method
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
	};

	private Handler mHandler = new Handler() {
		/**
		 * Message handler
		 * 
		 * @param msg
		 */
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_TWEETS_CHANGED:
				int numTweets = msg.arg1;
				if (numTweets > 0) {
			        setProgressBarIndeterminateVisibility(true);
					fillList();
			        setProgressBarIndeterminateVisibility(false);
				}
				break;
			case MSG_DATA_LOADING:
		        // Request progress bar
		        setProgressBarIndeterminateVisibility(msg.arg1 == 1 ? true : false);
				break;
			case MSG_UPDATE_STATUS:
				JSONObject result = (JSONObject) msg.obj;
				if (result.optString("error").length() > 0) {
					Toast.makeText(TweetList.this, (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(TweetList.this, R.string.message_sent, Toast.LENGTH_SHORT).show();
					mEditText.setText("");
					mEditText.clearFocus();
					mEditText.requestFocus();
				}
				setProgressBarIndeterminateVisibility(false);
				mProgressDialog.dismiss();
				break;
			case MSG_MANUAL_RELOAD:
				Toast.makeText(TweetList.this, R.string.timeline_reloaded, Toast.LENGTH_SHORT).show();
				setProgressBarIndeterminateVisibility(false);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};

	/**
	 * Listener for key events on the multi-line text field.
	 */
	private OnKeyListener mEditTextKeyListener = new View.OnKeyListener() {
		/**
		 * Event that listens for keys. Returns true if the limit has been
		 * reached, and false if the user is allowed to continue typing.
		 * 
		 * @param v
		 * @param keyCode
		 * @param event
		 * @return boolean
		 */
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			MultiAutoCompleteTextView editTxt = (MultiAutoCompleteTextView) v;
			mCurrentChars = editTxt.length();
			if (mCurrentChars == 0) {
				mReplyId = 0;
			}
			if (keyCode != KeyEvent.KEYCODE_DEL && mCurrentChars > mLimitChars) {
				return true;
			}
			mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));
			return false;
		}
	};

	/**
	 * Listener for focus changes of the multi-line text field.
	 */
	private OnFocusChangeListener mEditTextFocusChangeListener = new View.OnFocusChangeListener() {
		/**
		 * Event triggered when focus of text view changes.
		 * 
		 * @param v
		 * @param hasFocus
		 */
		public void onFocusChange(View v, boolean hasFocus) {
			MultiAutoCompleteTextView editTxt = (MultiAutoCompleteTextView) v;
			mCurrentChars = editTxt.length();
			mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));
		}
	};

	/**
	 * Listener that checks for clicks on the main list view.
	 */
	private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
		/**
		 * @param adapterView
		 * @param view
		 * @param position
		 * @param id
		 */
		public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
			Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
			String action = getIntent().getAction();
			if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
				setResult(RESULT_OK, new Intent().setData(uri));
			} else {
				startActivity(new Intent(Intent.ACTION_VIEW, uri));
			}
		}
	};

	private Runnable mSendUpdate = new Runnable() {
		public void run() {
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			String username = sp.getString("twitter_username", null);
			String password = sp.getString("twitter_password", null);
			String message = mEditText.getText().toString();
			com.xorcode.andtweet.net.Connection aConn = new com.xorcode.andtweet.net.Connection(username, password);
			JSONObject result = new JSONObject();
			try {
				result = aConn.updateStatus(message, mReplyId);
			} catch (UnsupportedEncodingException e) {
				Log.e(TAG, e.getMessage());
			} catch (JSONException e) {
				Log.e(TAG, e.getMessage());
				e.printStackTrace();
			}
			mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_STATUS, result));
		}
	};

	private Runnable mManualReload = new Runnable() {
		public void run() {
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			String username = sp.getString("twitter_username", null);
			String password = sp.getString("twitter_password", null);
			FriendTimeline friendTimeline = new FriendTimeline(getContentResolver(), username, password);
			int aNewTweets = friendTimeline.loadTimeline();
			mHandler.sendMessage(mHandler.obtainMessage(MSG_MANUAL_RELOAD, aNewTweets, 0));
		}
	};
}