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
import java.net.SocketTimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.SearchRecentSuggestions;
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

import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.data.FriendTimeline;
import com.xorcode.andtweet.data.PagedCursorAdapter;
import com.xorcode.andtweet.data.SearchableCursorAdapter;
import com.xorcode.andtweet.data.TimelineSearchSuggestionProvider;
import com.xorcode.andtweet.data.TweetBinder;
import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.data.AndTweetDatabase.Users;
import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;
import com.xorcode.andtweet.util.AtTokenizer;

/**
 * @author torgny.bjers
 * 
 */
public class TweetListActivity extends TimelineActivity {

	public static final String TAG = "AndTweet";

	// Context menu items
	public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 2;
	public static final int CONTEXT_MENU_ITEM_STAR = Menu.FIRST + 3;
	public static final int CONTEXT_MENU_ITEM_DIRECT_MESSAGE = Menu.FIRST + 4;
	public static final int CONTEXT_MENU_ITEM_UNFOLLOW = Menu.FIRST + 5;
	public static final int CONTEXT_MENU_ITEM_BLOCK = Menu.FIRST + 6;
	public static final int CONTEXT_MENU_ITEM_RETWEET = Menu.FIRST + 7;
	public static final int CONTEXT_MENU_ITEM_PROFILE = Menu.FIRST + 8;

	// Views and widgets
	private Button mSendButton;
	private MultiAutoCompleteTextView mEditText;
	private TextView mCharsLeftText;

	// Text limits
	private int mCurrentChars = 0;
	private int mLimitChars = 140;
	private boolean mInitializing = false;

	// Database cursors
	private Cursor mFriendsCursor;

	protected long mReplyId = 0;

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
			if (savedInstanceState.containsKey(BUNDLE_KEY_CURRENT_PAGE)) {
				mCurrentPage = savedInstanceState.getInt(BUNDLE_KEY_CURRENT_PAGE);
			}
			if (savedInstanceState.containsKey(BUNDLE_KEY_IS_LOADING)) {
				mIsLoading = savedInstanceState.getBoolean(BUNDLE_KEY_IS_LOADING);
			}
		}
		
		final Intent intent = getIntent();
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			intent.setData(AndTweetDatabase.Tweets.SEARCH_URI);
		} else {
			if (intent.getData() == null) {
				intent.setData(AndTweetDatabase.Tweets.CONTENT_URI);
			}
		}

		// Set up views
		mSendButton = (Button) findViewById(R.id.messageEditSendButton);
		mEditText = (MultiAutoCompleteTextView) findViewById(R.id.messageEditTextAC);
		mCharsLeftText = (TextView) findViewById(R.id.messageEditCharsLeftTextView);

		// Create list footer for loading messages
		mListFooter = new LinearLayout(getApplicationContext());
		mListFooter.setClickable(false);
		getListView().addFooterView(mListFooter);
		LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
		View tv = inflater.inflate(R.layout.item_loading, null);
		mListFooter.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		mListFooter.setVisibility(View.INVISIBLE);

		getListView().setOnScrollListener(this);

		initUI();
	}

	@Override
	public boolean onSearchRequested() {
		Bundle appDataBundle = new Bundle();
		appDataBundle.putParcelable("content_uri", AndTweetDatabase.Tweets.SEARCH_URI);
		startSearch(null, false, appDataBundle, false);
		return true;
	}

	@Override
	public void onNewIntent(final Intent newIntent) {
		super.onNewIntent(newIntent);
		// get and process search query here
		if (Intent.ACTION_SEARCH.equals(newIntent.getAction())) {
			doSearchQuery(newIntent);
		}
	}

	protected void doSearchQuery(final Intent queryIntent) {
		// The search query is provided as an "extra" string in the query intent
		final String queryString = queryIntent.getStringExtra(SearchManager.QUERY);

		// Record the query string in the recent queries suggestions provider
		SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
				TimelineSearchSuggestionProvider.AUTHORITY, TimelineSearchSuggestionProvider.MODE);
		suggestions.saveRecentQuery(queryString, null);

		// Extract content URI from the search data
		final Bundle appData = queryIntent.getBundleExtra(SearchManager.APP_DATA);
		if (appData != null) {
			final Intent intent = getIntent();
			Uri contentUri = appData.getParcelable("content_uri");
			String selection = appData.getString("selection");
			String[] selectionArgs = appData.getStringArray("selectionArgs");
			if (queryString != null) {
				contentUri = Uri.withAppendedPath((Uri) appData.getParcelable("content_uri"), Uri.encode(queryString));
			} else {
				contentUri = AndTweetDatabase.Tweets.CONTENT_URI;
			}
			if (contentUri != null) {
				intent.setData(contentUri);
				mCurrentPage = 1;
				if (mCursor != null && !mCursor.isClosed()) {
					mCursor.close();
				}
				mCursor = getContentResolver().query(contentUri, PROJECTION, selection, selectionArgs, Tweets.DEFAULT_SORT_ORDER + " LIMIT 0," + (mCurrentPage * 20));
				createAdapters();
			}
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		final Intent intent = getIntent();
		try {
			mFriendsCursor = getContentResolver().query(Users.CONTENT_URI, FRIENDS_PROJECTION, null, null, Users.DEFAULT_SORT_ORDER);
		} catch (SQLiteDiskIOException e) {
			showDialog(DIALOG_EXTERNAL_STORAGE_MISSING);
			return;
		}
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
		    doSearchQuery(intent);
		} else {
			mCursor = getContentResolver().query(getIntent().getData(), PROJECTION, Tweets.TWEET_TYPE + " = ?", new String[] { String.valueOf(Tweets.TWEET_TYPE_TWEET) }, Tweets.DEFAULT_SORT_ORDER + " LIMIT 0," + (mCurrentPage * 20));
			createAdapters();
		}
		mEditText.requestFocus();
		if ("com.xorcode.andtweet.INITIALIZE".equals(intent.getAction())) {
			intent.setAction(null);
			Log.d(TAG, "onStart() Initializing...");
			mInitializing = true;
			// Clean up databases in case there is data in there
			getContentResolver().delete(AndTweetDatabase.Tweets.CONTENT_URI, null, null);
			getContentResolver().delete(AndTweetDatabase.DirectMessages.CONTENT_URI, null, null);
			getContentResolver().delete(AndTweetDatabase.Users.CONTENT_URI, null, null);
			// Display the indeterminate progress bar
			showDialog(DIALOG_TIMELINE_LOADING);
			// Display the list footer progress bar
			mListFooter.setVisibility(View.VISIBLE);
			// Start the manual reload thread
			Thread thread = new Thread(mManualReload);
			thread.start();
		} else {
			bindToService();
		}
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
		if (a != null && a.getCursor() != null && !a.getCursor().isClosed()) {
			a.getCursor().close();
		}
		if (mCursor != null && !mCursor.isClosed()) {
			mCursor.close();
		}
		if (mFriendsCursor != null && !mFriendsCursor.isClosed()) {
			mFriendsCursor.close();
		}
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
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.reload_menu_item:
			showDialog(DIALOG_TIMELINE_LOADING);
			mListFooter.setVisibility(View.VISIBLE);
			Thread thread = new Thread(mManualReload);
			thread.start();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);

		// Get the adapter context menu information
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return;
		}

		// Get the record for the currently selected item
		Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, info.id);
		Cursor c = getContentResolver().query(uri, new String[] { Tweets._ID, Tweets.MESSAGE, Tweets.AUTHOR_ID }, null, null, null);
		try {
			c.moveToFirst();
			menu.setHeaderTitle(c.getString(c.getColumnIndex(Tweets.MESSAGE)));
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		} finally {
			if (c != null && !c.isClosed()) c.close();
		}

		int m = 0;

		// Add menu items
		menu.add(0, CONTEXT_MENU_ITEM_REPLY, m++, R.string.menu_item_reply);
		//menu.add(0, CONTEXT_MENU_ITEM_DIRECT_MESSAGE, m++, R.string.menu_item_direct_message);
		//menu.add(0, CONTEXT_MENU_ITEM_RETWEET, m++, R.string.menu_item_retweet);
		//menu.add(0, CONTEXT_MENU_ITEM_STAR, m++, R.string.menu_item_star);
		//menu.add(0, CONTEXT_MENU_ITEM_UNFOLLOW, m++, R.string.menu_item_unfollow);
		//menu.add(0, CONTEXT_MENU_ITEM_BLOCK, m++, R.string.menu_item_block);
		//menu.add(0, CONTEXT_MENU_ITEM_PROFILE, m++, R.string.menu_item_view_profile);
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
				mEditText.setText("");
				mEditText.append(reply, 0, reply.length());
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

	/**
	 * Create adapters
	 */
	private void createAdapters() {
		int listItemId = R.layout.tweetlist_item;
		if (mSP.getBoolean("appearance_use_avatars", false)) {
			listItemId = R.layout.tweetlist_item_avatar;
		}
		PagedCursorAdapter tweetsAdapter = new PagedCursorAdapter(
			TweetListActivity.this,
			listItemId,
			mCursor,
			new String[] { Tweets.AUTHOR_ID, Tweets.MESSAGE, Tweets.SENT_DATE },
			new int[] { R.id.tweet_screen_name, R.id.tweet_message, R.id.tweet_sent },
			getIntent().getData(),
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
		try {
			friendsAdapter.setStringConversionColumn(mFriendsCursor.getColumnIndexOrThrow(Users.AUTHOR_ID));
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Could not set string conversion column on mFriendsCursor", e);
		}

		mEditText.setAdapter(friendsAdapter);
		mEditText.setTokenizer(new AtTokenizer());
	}

	/**
	 * Listener for send button clicks.
	 */
	private View.OnClickListener mSendOnClickListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mEditText.length() > 0) {
				//setProgressBarIndeterminateVisibility(true);
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
					//setProgressBarIndeterminateVisibility(true);
					mListFooter.setVisibility(View.VISIBLE);
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
	 * Updates the activity title.
	 */
	@Override
	public void updateTitle() {
		Thread thread = new Thread(mUpdateTitle);
		thread.start();
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
				if (mIsLoading) {
					showDialog(DIALOG_TIMELINE_LOADING);
				} else {
					dismissDialog(DIALOG_TIMELINE_LOADING);
				}
				break;

			case MSG_UPDATE_STATUS:
				JSONObject result = (JSONObject) msg.obj;
				if (result.optString("error").length() > 0) {
					Toast.makeText(TweetListActivity.this, (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
				} else {
					String username = mSP.getString("twitter_username", null);
					String password = mSP.getString("twitter_password", null);
					FriendTimeline fl = new FriendTimeline(getContentResolver(), username, password, mSP.getLong("last_timeline_runtime", System.currentTimeMillis()));
					try {
						fl.insertFromJSONObject(result, AndTweetDatabase.Tweets.TWEET_TYPE_TWEET, true);
					} catch (JSONException e) {
						Toast.makeText(TweetListActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
					}
					Toast.makeText(TweetListActivity.this, R.string.message_sent, Toast.LENGTH_SHORT).show();
					mReplyId = 0;
					mEditText.setText("");
					mEditText.clearFocus();
					mEditText.requestFocus();
				}
				dismissDialog(DIALOG_SENDING_MESSAGE);
				break;

			case MSG_AUTHENTICATION_ERROR:
				switch (msg.arg1) {
				case MSG_MANUAL_RELOAD:
					dismissDialog(DIALOG_TIMELINE_LOADING);
					break;
				case MSG_UPDATE_STATUS:
					dismissDialog(DIALOG_SENDING_MESSAGE);
					break;
				}
				mListFooter.setVisibility(View.INVISIBLE);
				showDialog(DIALOG_AUTHENTICATION_FAILED);
				break;

			case MSG_SERVICE_UNAVAILABLE_ERROR:
				switch (msg.arg1) {
				case MSG_MANUAL_RELOAD:
					dismissDialog(DIALOG_TIMELINE_LOADING);
					break;
				case MSG_UPDATE_STATUS:
					dismissDialog(DIALOG_SENDING_MESSAGE);
					break;
				}
				mListFooter.setVisibility(View.INVISIBLE);
				showDialog(DIALOG_SERVICE_UNAVAILABLE);
				break;

			case MSG_MANUAL_RELOAD:
				dismissDialog(DIALOG_TIMELINE_LOADING);
				mIsLoading = false;
				Toast.makeText(TweetListActivity.this, R.string.timeline_reloaded, Toast.LENGTH_SHORT).show();
				mListFooter.setVisibility(View.INVISIBLE);
				updateTitle();
				if (mInitializing) {
					mInitializing = false;
					bindToService();
				}
				SharedPreferences.Editor prefsEditor = mSP.edit();
				prefsEditor.putLong("last_timeline_runtime", System.currentTimeMillis());
				prefsEditor.commit();
				break;

			case MSG_LOAD_ITEMS:
				mListFooter.setVisibility(View.INVISIBLE);
				switch (msg.arg1) {
				case STATUS_LOAD_ITEMS_SUCCESS:
					updateTitle();
					mIsLoading = false;
					mListFooter.setVisibility(View.INVISIBLE);
					((SimpleCursorAdapter) getListAdapter()).changeCursor(mCursor);
					//setProgressBarIndeterminateVisibility(false);
					break;
				case STATUS_LOAD_ITEMS_FAILURE:
					break;
				}
				break;

			case MSG_UPDATED_TITLE:
				String username = mSP.getString("twitter_username", null);
				JSONObject status = (JSONObject) msg.obj;
				try {
					setTitle(getString(R.string.activity_title_format, new Object[] {getTitle(), username}), status.getInt("remaining_hits") + "/" + status.getInt("hourly_limit"));
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;

			case MSG_CONNECTION_TIMEOUT_EXCEPTION:
				switch (msg.arg1) {
				case MSG_MANUAL_RELOAD:
					dismissDialog(DIALOG_TIMELINE_LOADING);
					break;
				case MSG_UPDATE_STATUS:
					dismissDialog(DIALOG_SENDING_MESSAGE);
					break;
				}
				mListFooter.setVisibility(View.INVISIBLE);
				showDialog(DIALOG_CONNECTION_TIMEOUT);
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
			String username = mSP.getString("twitter_username", null);
			String password = mSP.getString("twitter_password", null);
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
			} catch (ConnectionUnavailableException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR, MSG_UPDATE_STATUS, 0));
				return;
			} catch (SocketTimeoutException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION, MSG_UPDATE_STATUS, 0));
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
			final SharedPreferences.Editor prefsEditor = mSP.edit();
			String username = mSP.getString("twitter_username", null);
			String password = mSP.getString("twitter_password", null);
			long lastTweetId = mSP.getLong("last_timeline_id", 0);
			FriendTimeline friendTimeline = new FriendTimeline(getContentResolver(), username, password, lastTweetId);
			int aNewTweets = 0;
			int aReplyCount = 0;
			try {
				friendTimeline.loadTimeline(AndTweetDatabase.Tweets.TWEET_TYPE_REPLY, mInitializing);
				aReplyCount = friendTimeline.replyCount();
				friendTimeline.loadTimeline(AndTweetDatabase.Tweets.TWEET_TYPE_TWEET, mInitializing);
				aNewTweets = friendTimeline.newCount();
				aReplyCount += friendTimeline.replyCount();
				lastTweetId = friendTimeline.lastId();
				prefsEditor.putLong("last_timeline_id", lastTweetId);
				prefsEditor.commit();
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
			} catch (ConnectionUnavailableException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR, MSG_MANUAL_RELOAD, 0));
				return;
			} catch (SocketTimeoutException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION, MSG_MANUAL_RELOAD, 0));
				return;
			}
			mHandler.sendMessage(mHandler.obtainMessage(MSG_MANUAL_RELOAD, aNewTweets, aReplyCount));
		}
	};

	/**
	 * Load more items into the list.
	 */
	protected Runnable mLoadListItems = new Runnable() {
		public void run() {
			mCursor = ((PagedCursorAdapter) TweetListActivity.this.getListAdapter()).runQuery("LIMIT 0," + (++mCurrentPage * 20));
			mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_LOAD_ITEMS, STATUS_LOAD_ITEMS_SUCCESS, 0), 400);
		}
	};

	protected Runnable mUpdateTitle = new Runnable() {
		public void run() {
			String username = mSP.getString("twitter_username", null);
			String password = mSP.getString("twitter_password", null);
			Connection c = new Connection(username, password);
			try {
				JSONObject status = c.rateLimitStatus();
				mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATED_TITLE, status));
			} catch (JSONException e) {
			} catch (ConnectionException e) {
			} catch (ConnectionAuthenticationException e) {
			} catch (ConnectionUnavailableException e) {
			} catch (SocketTimeoutException e) {
			}
		}
	};
}
