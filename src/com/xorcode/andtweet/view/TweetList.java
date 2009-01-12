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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.NotificationManager;
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
import android.widget.AdapterView.OnItemClickListener;

import com.xorcode.andtweet.IAndTweetService;
import com.xorcode.andtweet.IAndTweetServiceCallback;
import com.xorcode.andtweet.R;
import com.xorcode.andtweet.data.AndTweet;
import com.xorcode.andtweet.data.TweetBinder;
import com.xorcode.andtweet.data.AndTweet.Tweets;
import com.xorcode.andtweet.util.AtTokenizer;

/**
 * @author torgny.bjers
 * 
 */
public class TweetList extends Activity {

	private static final String TAG = "TweetList";

	private static final int REQUEST_CODE_PREFERENCES = 1;

	public static final int OPTIONS_MENU_PREFERENCES = Menu.FIRST;

	public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 2;
	public static final int CONTEXT_MENU_ITEM_STAR = Menu.FIRST + 3;

	private static final String[] PROJECTION = new String[] {
		Tweets._ID, Tweets.AUTHOR_ID, Tweets.MESSAGE, Tweets.SENT_DATE
	};

	private static final int MSG_TWEETS_CHANGED = 1;
	private static final int MSG_DATA_LOADING = 2;

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
	 * List of friends
	 */
	private ArrayList<String> mFriends = new ArrayList<String>();

	/**
	 * Service interface connection
	 */
	IAndTweetService mService = null;

	/**
	 * Service binding indicator
	 */
	private static boolean mIsBound;

	private Cursor mCursor;

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
		String aUsername = mSP.getString("twitter_username", "");
		if (aUsername != null && aUsername.length() == 0) {
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
		mCursor = managedQuery(getIntent().getData(), PROJECTION, null, null,
				Tweets.DEFAULT_SORT_ORDER + " LIMIT 20");
		fillList();
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
		if (mCursor != null && !mCursor.isClosed()) {
			mCursor.close();
		}
		mNM.cancel(R.string.app_name);
	}

	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, OPTIONS_MENU_PREFERENCES, 0, R.string.options_menu_preferences).setShortcut(
				'3', 'p').setIcon(android.R.drawable.ic_menu_preferences);

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
		case CONTEXT_MENU_ITEM_REPLY: {
			Uri uri = ContentUris.withAppendedId(getIntent().getData(), info.id);
			Cursor c = managedQuery(uri, new String[] { Tweets._ID, Tweets.AUTHOR_ID }, null, null, null);
			try {
				c.moveToFirst();
				mEditText.requestFocus();
				String reply = "@" + c.getString(c.getColumnIndex(Tweets.AUTHOR_ID)) + " ";
				mEditText.setText("");
				mEditText.append(reply, 0, reply.length());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			} finally {
				c.close();
			}
			// getContentResolver().delete(noteUri, null, null);
			return true;
		}
		case CONTEXT_MENU_ITEM_STAR: {
			return true;
		}
		}
		return false;
	}

	public void onListItemClick(ListView listView, View view, int position, long id) {
		
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
			}
		});

		mCharsLeftText.setText(String.valueOf(mLimitChars - mEditText.length()));

		// Attach listeners to the text field
		mEditText.setOnFocusChangeListener(mEditTextFocusChangeListener);
		mEditText.setOnKeyListener(mEditTextKeyListener);

		if (mFriends.isEmpty()) {
			loadFriends();
			ArrayAdapter<String> friendsAdapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_dropdown_item_1line, mFriends);
			mEditText.setAdapter(friendsAdapter);
			mEditText.setTokenizer(new AtTokenizer());
		}
	}

	/**
	 * Fill the ListView with Tweet items.
	 */
	private void fillList() {
		Cursor cursor = managedQuery(getIntent().getData(), PROJECTION, null, null,
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
	 * Temporary method for loading friends from an asset file.
	 */
	private void loadFriends() {
		try {
			InputStream is = getAssets().open("friends.json");
			int size = is.available();
			byte[] buffer = new byte[size];
			is.read(buffer);
			is.close();
			try {
				JSONArray jArr = new JSONArray(new String(buffer));
				for (int index = 0; index < jArr.length(); index++) {
					JSONObject jo = jArr.getJSONObject(index);
					if (jo.has("screen_name")) {
						mFriends.add(jo.getString("screen_name"));
					}
				}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
		 * Value changed callback method
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
					fillList();
				}
				break;
			case MSG_DATA_LOADING:
		        // Request progress bar
		        setProgressBarIndeterminateVisibility(msg.arg1 == 1 ? true : false);
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
			if (keyCode != KeyEvent.KEYCODE_DEL && mCurrentChars > mLimitChars) {
				return true;
			}
			mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));
			return false;
		}
	};

	/**
	 * 
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

	private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
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
}