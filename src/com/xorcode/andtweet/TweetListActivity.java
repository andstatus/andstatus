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

import java.io.UnsupportedEncodingException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.xorcode.andtweet.data.FriendTimeline;
import com.xorcode.andtweet.data.PagedCursorAdapter;
import com.xorcode.andtweet.data.SearchableCursorAdapter;
import com.xorcode.andtweet.data.TweetBinder;
import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.data.AndTweetDatabase.Users;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.util.AtTokenizer;

/**
 * @author torgny.bjers
 * 
 */
public class TweetListActivity extends TimelineActivity {

	public static final String TAG = "AndTweet";

	// ActivityForResult request codes
	private static final int REQUEST_CODE_PREFERENCES = 1;

	// Options menu items
	public static final int OPTIONS_MENU_PREFERENCES = Menu.FIRST;
	public static final int OPTIONS_MENU_MORE = Menu.FIRST + 1;
	public static final int OPTIONS_MENU_RELOAD = Menu.FIRST + 2;

	// Context menu items
	public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 2;
	public static final int CONTEXT_MENU_ITEM_STAR = Menu.FIRST + 3;
	public static final int CONTEXT_MENU_ITEM_DIRECT_MESSAGE = Menu.FIRST + 4;
	public static final int CONTEXT_MENU_ITEM_UNFOLLOW = Menu.FIRST + 5;
	public static final int CONTEXT_MENU_ITEM_BLOCK = Menu.FIRST + 6;
	public static final int CONTEXT_MENU_ITEM_RETWEET = Menu.FIRST + 7;
	public static final int CONTEXT_MENU_ITEM_PROFILE = Menu.FIRST + 8;

	// Dialog identifier codes
	private static final int DIALOG_AUTHENTICATION_FAILED = 1;
	private static final int DIALOG_SENDING_MESSAGE = 2;

	// Bundle identifier keys
	private static final String BUNDLE_KEY_REPLY_ID = "replyId";
	private static final String BUNDLE_KEY_CURRENT_PAGE = "currentPage";
	private static final String BUNDLE_KEY_IS_LOADING = "isLoading";

	// Views and widgets
	private Button mSendButton;
	private MultiAutoCompleteTextView mEditText;
	private TextView mCharsLeftText;
	private LinearLayout mListFooter;
	private ProgressDialog mProgressDialog;

	// Text limits
	private int mCurrentChars = 0;
	private int mLimitChars = 140;

	// Database cursors
	private Cursor mFriendsCursor;

	private int mCurrentPage = 1;
	private long mReplyId = 0;
	private int mTotalItemCount = 0;

	// Table columns to use for the tweets data
	private static final String[] PROJECTION = new String[] {
		Tweets._ID,
		Tweets.AUTHOR_ID,
		Tweets.MESSAGE,
		Tweets.IN_REPLY_TO_AUTHOR_ID,
		Tweets.SENT_DATE
	};

	// Table columns to use for the user data
	private static final String[] FRIENDS_PROJECTION = new String[] {
		Users._ID,
		Users.AUTHOR_ID
	};

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

		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey(BUNDLE_KEY_REPLY_ID)) {
				mReplyId = savedInstanceState.getLong(BUNDLE_KEY_REPLY_ID);
			}
			if (savedInstanceState.containsKey(BUNDLE_KEY_REPLY_ID)) {
				mCurrentPage = savedInstanceState.getInt(BUNDLE_KEY_CURRENT_PAGE);
			}
			if (savedInstanceState.containsKey(BUNDLE_KEY_IS_LOADING)) {
				mIsLoading = savedInstanceState.getBoolean(BUNDLE_KEY_IS_LOADING);
			}
		}

		final Intent intent = getIntent();
		if (intent.getData() == null) {
			intent.setData(Tweets.CONTENT_URI);
		}

		// Set up views
		mSendButton = (Button) findViewById(R.id.messageEditSendButton);
		mEditText = (MultiAutoCompleteTextView) findViewById(R.id.messageEditTextAC);
		mCharsLeftText = (TextView) findViewById(R.id.messageEditCharsLeftTextView);

		// Create list footer for loading messages
		mListFooter = new LinearLayout(getApplicationContext());
		mListFooter.setClickable(false);
		getListView().addFooterView(mListFooter);

		getListView().setOnScrollListener(this);

		initUI();
	}

	@Override
	protected void onStart() {
		super.onStart();
		mCursor = getContentResolver().query(getIntent().getData(), PROJECTION, null, null, Tweets.DEFAULT_SORT_ORDER + " LIMIT 0," + (mCurrentPage * 20));
		mFriendsCursor = getContentResolver().query(Users.CONTENT_URI, FRIENDS_PROJECTION, null, null, Users.DEFAULT_SORT_ORDER);
		createAdapters();
		setProgressBarIndeterminateVisibility(mIsLoading);
		bindToService();
		mEditText.requestFocus();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCharsLeftText.setText(String.valueOf(mLimitChars - mEditText.length()));
	}

	@Override
	protected void onStop() {
		super.onStop();
		SimpleCursorAdapter a = (SimpleCursorAdapter) mEditText.getAdapter();
		if (a.getCursor() != null && !a.getCursor().isClosed()) {
			a.getCursor().close();
		}
		mCursor.close();
		mFriendsCursor.close();
		disconnectService();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		disconnectService();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putLong(BUNDLE_KEY_REPLY_ID, mReplyId);
		outState.putInt(BUNDLE_KEY_CURRENT_PAGE, mCurrentPage);
		outState.putBoolean(BUNDLE_KEY_IS_LOADING, mIsLoading);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		int order = 0;
		int groupId = 0;

		menu.add(groupId, OPTIONS_MENU_PREFERENCES, order++, R.string.options_menu_preferences)
			.setShortcut('1', 'p')
			.setIcon(android.R.drawable.ic_menu_preferences);

		menu.add(groupId, OPTIONS_MENU_RELOAD, order++, R.string.options_menu_reload)
			.setShortcut('2', 'r')
			.setIcon(android.R.drawable.ic_menu_rotate);

		menu.add(groupId, OPTIONS_MENU_MORE, order++, R.string.options_menu_more)
		.setShortcut('3', 'm')
		.setIcon(android.R.drawable.ic_menu_more);

		Intent intent = new Intent(null, getIntent().getData());
		intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
		menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, new ComponentName(this, TweetListActivity.class), null, intent, 0, null);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case OPTIONS_MENU_PREFERENCES:
			startActivityForResult(new Intent(this, PreferencesActivity.class), REQUEST_CODE_PREFERENCES);
			break;

		case OPTIONS_MENU_RELOAD:
			setProgressBarIndeterminateVisibility(true);
			Thread thread = new Thread(mManualReload);
			thread.start();
			break;

		case OPTIONS_MENU_MORE:
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
		super.onCreateContextMenu(menu, view, menuInfo);
		// Add menu items
		menu.add(0, CONTEXT_MENU_ITEM_REPLY, 0, R.string.menu_item_reply);
		menu.add(0, CONTEXT_MENU_ITEM_RETWEET, 1, R.string.menu_item_retweet);
		menu.add(0, CONTEXT_MENU_ITEM_STAR, 2, R.string.menu_item_star);
		menu.add(0, CONTEXT_MENU_ITEM_DIRECT_MESSAGE, 3, R.string.menu_item_direct_message);
		menu.add(0, CONTEXT_MENU_ITEM_PROFILE, 4, R.string.menu_item_view_profile);
		menu.add(0, CONTEXT_MENU_ITEM_UNFOLLOW, 5, R.string.menu_item_unfollow);
		menu.add(0, CONTEXT_MENU_ITEM_BLOCK, 6, R.string.menu_item_block);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return false;
		}

		switch (item.getItemId()) {
		case CONTEXT_MENU_ITEM_REPLY:
			Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, info.id);
			Cursor c = getContentResolver().query(uri, new String[] { Tweets._ID, Tweets.AUTHOR_ID }, null, null, null);
			try {
				c.moveToFirst();
				mEditText.requestFocus();
				String reply = "@" + c.getString(c.getColumnIndex(Tweets.AUTHOR_ID)) + " ";
				mEditText.setText("");				mEditText.append(reply, 0, reply.length());
				mReplyId = c.getLong(c.getColumnIndex(Tweets._ID));
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			} finally {
				if (c != null && !c.isClosed()) c.close();
			}
			return true;

		case CONTEXT_MENU_ITEM_STAR:
		case CONTEXT_MENU_ITEM_RETWEET:
		case CONTEXT_MENU_ITEM_UNFOLLOW:
		case CONTEXT_MENU_ITEM_BLOCK:
		case CONTEXT_MENU_ITEM_DIRECT_MESSAGE:
		case CONTEXT_MENU_ITEM_PROFILE:
			Toast.makeText(this, R.string.unimplemented, Toast.LENGTH_SHORT).show();
			return true;
		}
		return false;
	}

	@Override protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_AUTHENTICATION_FAILED:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_authentication_failed)
				.setMessage(R.string.dialog_summary_authentication_failed)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
						startActivityForResult(new Intent(TweetListActivity.this, PreferencesActivity.class), REQUEST_CODE_PREFERENCES);
					}
				}).create();

		case DIALOG_SENDING_MESSAGE:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setTitle(R.string.dialog_title_sending_message);
			mProgressDialog.setMessage(getText(R.string.dialog_summary_sending_message));
			return mProgressDialog;

		default:
			return super.onCreateDialog(id);
		}
	}

	/**
	 * Initialize UI
	 */
	@Override
	protected void initUI() {
		super.initUI();
		// Set up the send button
		mSendButton.setOnClickListener(mSendOnClickListener);
		// Attach listeners to the text field
		mEditText.setOnFocusChangeListener(mEditTextFocusChangeListener);
		mEditText.setOnKeyListener(mEditTextKeyListener);
	}

	private void createAdapters() {
		PagedCursorAdapter tweetsAdapter = new PagedCursorAdapter(
			TweetListActivity.this,
			R.layout.tweetlist_item,
			mCursor,
			new String[] { Tweets.AUTHOR_ID, Tweets.MESSAGE, Tweets.SENT_DATE },
			new int[] { R.id.tweet_screen_name, R.id.tweet_message, R.id.tweet_sent },
			Tweets.CONTENT_URI,
			PROJECTION,
			Tweets.DEFAULT_SORT_ORDER
		);
		tweetsAdapter.setViewBinder(new TweetBinder());

		setListAdapter(tweetsAdapter);

		SearchableCursorAdapter friendsAdapter = new SearchableCursorAdapter(
			this,
			android.R.layout.simple_dropdown_item_1line,
			mFriendsCursor,
			new String[] { Users.AUTHOR_ID },
			new int[] { android.R.id.text1 },
			Users.CONTENT_URI,
			FRIENDS_PROJECTION,
			Users.DEFAULT_SORT_ORDER
		);
		friendsAdapter.setStringConversionColumn(mFriendsCursor.getColumnIndexOrThrow(Users.AUTHOR_ID));

		mEditText.setAdapter(friendsAdapter);
		mEditText.setTokenizer(new AtTokenizer());
	}

	/**
	 * Listener for send button clicks.
	 */
	private View.OnClickListener mSendOnClickListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mEditText.length() > 0) {
				setProgressBarIndeterminateVisibility(true);
				showDialog(DIALOG_SENDING_MESSAGE);
				Thread thread = new Thread(mSendUpdate);
				thread.start();
			} else {
				Toast.makeText(TweetListActivity.this, R.string.cannot_send_empty_message, Toast.LENGTH_SHORT).show();
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
	 * 
	 * @param adapterView
	 * @param view
	 * @param position
	 * @param id
	 */
	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
		Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, id);
		String action = getIntent().getAction();
		if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
			setResult(RESULT_OK, new Intent().setData(uri));
		} else {
			startActivity(new Intent(Intent.ACTION_VIEW, uri));
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		mTotalItemCount = totalItemCount;
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (mIsLoading) {
			return;
		}
		switch (scrollState) {
		case SCROLL_STATE_IDLE:
			if (view.getLastVisiblePosition() >= mTotalItemCount - 1) {
				if (getListView().getFooterViewsCount() == 1 && !mIsLoading) {
					mIsLoading = true;
					setProgressBarIndeterminateVisibility(true);
					mListFooter.setVisibility(View.VISIBLE);
					LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
					View tv = inflater.inflate(R.layout.item_loading, null);
					mListFooter.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
					Log.d(TAG, "List footer height: " + mListFooter.getHeight());
					Thread thread = new Thread(mLoadListItems);
					thread.start();
				}
			}
			break;
		case SCROLL_STATE_TOUCH_SCROLL:
			break;
		case SCROLL_STATE_FLING:
			break;
		}
	}

	/**
	 * Message handler for messages from threads.
	 */
	protected Handler mHandler = new Handler() {
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
					mNM.cancelAll();
				}
				break;

			case MSG_DATA_LOADING:
				mIsLoading = msg.arg1 == 1 ? true : false;
		        setProgressBarIndeterminateVisibility(mIsLoading);
				break;

			case MSG_UPDATE_STATUS:
				JSONObject result = (JSONObject) msg.obj;
				if (result.optString("error").length() > 0) {
					Toast.makeText(TweetListActivity.this, (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
				} else {
					SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
					String username = sp.getString("twitter_username", null);
					String password = sp.getString("twitter_password", null);
					FriendTimeline fl = new FriendTimeline(getContentResolver(), username, password);
					try {
						fl.insertFromJSONObject(result, true);
					} catch (JSONException e) {
						Toast.makeText(TweetListActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
					}
					Toast.makeText(TweetListActivity.this, R.string.message_sent, Toast.LENGTH_SHORT).show();
					mReplyId = 0;
					mEditText.setText("");
					mEditText.clearFocus();
					mEditText.requestFocus();
				}
				setProgressBarIndeterminateVisibility(false);
				dismissDialog(DIALOG_SENDING_MESSAGE);
				break;

			case MSG_AUTHENTICATION_ERROR:
				int source = msg.arg1;
				switch (source) {
				case MSG_MANUAL_RELOAD:
					setProgressBarIndeterminateVisibility(false);
					break;
				case MSG_UPDATE_STATUS:
					break;
				}
				showDialog(DIALOG_AUTHENTICATION_FAILED);
				break;

			case MSG_MANUAL_RELOAD:
				mIsLoading = false;
				Toast.makeText(TweetListActivity.this, R.string.timeline_reloaded, Toast.LENGTH_SHORT).show();
				setProgressBarIndeterminateVisibility(false);
				break;

			case MSG_LOAD_ITEMS:
				switch (msg.arg1) {
				case STATUS_LOAD_ITEMS_SUCCESS:
					mIsLoading = false;
					mListFooter.removeAllViewsInLayout();
					mListFooter.setVisibility(View.GONE);
					((SimpleCursorAdapter) getListAdapter()).changeCursor(mCursor);
					setProgressBarIndeterminateVisibility(false);
					break;
				case STATUS_LOAD_ITEMS_FAILURE:
					break;
				}
				break;

			default:
				super.handleMessage(msg);
			}
		}
	};

	/**
	 * Handles threaded sending of messages.
	 */
	protected Runnable mSendUpdate = new Runnable() {
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
			} catch (ConnectionException e) {
				Log.e(TAG, "mSendUpdate Connection Exception: " + e.getMessage());
				return;
			} catch (ConnectionAuthenticationException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTHENTICATION_ERROR, MSG_UPDATE_STATUS, 0));
				return;
			}
			mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_STATUS, result));
		}
	};

	/**
	 * Handles threaded manual reload of the timeline.
	 */
	protected Runnable mManualReload = new Runnable() {
		public void run() {
			mIsLoading = true;
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			String username = sp.getString("twitter_username", null);
			String password = sp.getString("twitter_password", null);
			FriendTimeline friendTimeline = new FriendTimeline(getContentResolver(), username, password);
			int aNewTweets = 0;
			try {
				aNewTweets = friendTimeline.loadTimeline();
			} catch (ConnectionException e) {
				Log.e(TAG, "mManualReload Connection Exception: " + e.getMessage());
				return;
			} catch (SQLiteConstraintException e) {
				Log.e(TAG, "mManualReload database exception: " + e.getMessage());
				return;
			} catch (JSONException e) {
				Log.e(TAG, "mManualReload JSON exception: " + e.getMessage());
				return;
			} catch (ConnectionAuthenticationException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTHENTICATION_ERROR, MSG_MANUAL_RELOAD, 0));
				return;
			}
			mHandler.sendMessage(mHandler.obtainMessage(MSG_MANUAL_RELOAD, aNewTweets, 0));
		}
	};

	/**
	 * Load more items into the list.
	 */
	protected Runnable mLoadListItems  = new Runnable() {
		public void run() {
			mCursor = ((PagedCursorAdapter) TweetListActivity.this.getListAdapter()).runQuery("LIMIT 0," + (mCurrentPage++ * 20));
			mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_LOAD_ITEMS, STATUS_LOAD_ITEMS_SUCCESS, 0), 400);
		}
	};
}