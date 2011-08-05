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

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.data.AndTweetPreferences;
import com.xorcode.andtweet.data.PagedCursorAdapter;
import com.xorcode.andtweet.data.SearchableCursorAdapter;
import com.xorcode.andtweet.data.TweetBinder;
import com.xorcode.andtweet.data.AndTweetDatabase.Users;

/**
 * @author torgny.bjers
 * 
 */
public class MessageListActivity extends TimelineActivity {

    private static final String TAG = MessageListActivity.class.getSimpleName();

	// Context menu items
	public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 3;
	public static final int CONTEXT_MENU_ITEM_STAR = Menu.FIRST + 4;
	public static final int CONTEXT_MENU_ITEM_DIRECT_MESSAGE = Menu.FIRST + 5;
	public static final int CONTEXT_MENU_ITEM_UNFOLLOW = Menu.FIRST + 6;
	public static final int CONTEXT_MENU_ITEM_BLOCK = Menu.FIRST + 7;
	public static final int CONTEXT_MENU_ITEM_RETWEET = Menu.FIRST + 8;
	public static final int CONTEXT_MENU_ITEM_PROFILE = Menu.FIRST + 9;

	// TODO: get rid of this variable just like in TweetListActivity...
	private int mCurrentPage = 1;	
	
	// Database cursors
	private Cursor mFriendsCursor;

	// Table columns to use for the direct messages data
	private static final String[] PROJECTION = new String[] {
		AndTweetDatabase.DirectMessages._ID,
		AndTweetDatabase.DirectMessages.AUTHOR_ID,
		AndTweetDatabase.DirectMessages.MESSAGE,
		AndTweetDatabase.DirectMessages.SENT_DATE
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

        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "onCreate");
        }

		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey(BUNDLE_KEY_IS_LOADING)) {
				mIsLoading = savedInstanceState.getBoolean(BUNDLE_KEY_IS_LOADING);
			}
		}

		final Intent intent = getIntent();
		intent.setData(AndTweetDatabase.DirectMessages.CONTENT_URI);

		findViewById(R.id.tweetlist_info).setVisibility(View.GONE);
		findViewById(R.id.tweetlist_editor).setVisibility(View.GONE);

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
	    appDataBundle.putParcelable("content_uri", AndTweetDatabase.DirectMessages.CONTENT_URI);
		startSearch(null, false, appDataBundle, false);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		findViewById(R.id.tweetlist_info).setVisibility(View.GONE);
		findViewById(R.id.tweetlist_editor).setVisibility(View.GONE);
	}

	@Override
	protected void onStart() {
		super.onStart();
		mCursor = getContentResolver().query(getIntent().getData(), PROJECTION, null, null, AndTweetDatabase.DirectMessages.DEFAULT_SORT_ORDER + " LIMIT 0," + (mCurrentPage * PAGE_SIZE));
		mFriendsCursor = getContentResolver().query(Users.CONTENT_URI, FRIENDS_PROJECTION, null, null, Users.DEFAULT_SORT_ORDER);
		createAdapters();
		setProgressBarIndeterminateVisibility(mIsLoading);
		bindToService();
	}

	@Override
	protected void onStop() {
		super.onStop();
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
		outState.putBoolean(BUNDLE_KEY_IS_LOADING, mIsLoading);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		// Add menu items
		//menu.add(0, CONTEXT_MENU_ITEM_REPLY, 0, R.string.menu_item_reply);
		//menu.add(0, CONTEXT_MENU_ITEM_RETWEET, 1, R.string.menu_item_retweet);
		//menu.add(0, CONTEXT_MENU_ITEM_STAR, 2, R.string.menu_item_star);
		//menu.add(0, CONTEXT_MENU_ITEM_DIRECT_MESSAGE, 3, R.string.menu_item_direct_message);
		//menu.add(0, CONTEXT_MENU_ITEM_PROFILE, 4, R.string.menu_item_view_profile);
		//menu.add(0, CONTEXT_MENU_ITEM_UNFOLLOW, 5, R.string.menu_item_unfollow);
		//menu.add(0, CONTEXT_MENU_ITEM_BLOCK, 6, R.string.menu_item_block);
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
			Uri uri = ContentUris.withAppendedId(AndTweetDatabase.DirectMessages.CONTENT_URI, info.id);
			Cursor c = getContentResolver().query(uri, new String[] { AndTweetDatabase.DirectMessages._ID, AndTweetDatabase.DirectMessages.AUTHOR_ID }, null, null, null);
			try {
				c.moveToFirst();
			} catch (Exception e) {
	            Log.e(TAG, "onContextItemSelected: " + e.toString());
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
	}

	private void createAdapters() {
		int listItemId = R.layout.messagelist_item;
		if (AndTweetPreferences.getDefaultSharedPreferences().getBoolean("appearance_use_avatars", false)) {
			listItemId = R.layout.messagelist_item_avatar;
		}
		PagedCursorAdapter directMessagesAdapter = new PagedCursorAdapter(
			MessageListActivity.this,
			listItemId,
			mCursor,
			new String[] { AndTweetDatabase.DirectMessages.AUTHOR_ID, AndTweetDatabase.DirectMessages.MESSAGE, AndTweetDatabase.DirectMessages.SENT_DATE },
			new int[] { R.id.tweet_screen_name, R.id.tweet_message, R.id.tweet_sent },
			AndTweetDatabase.DirectMessages.CONTENT_URI,
			PROJECTION,
			AndTweetDatabase.DirectMessages.DEFAULT_SORT_ORDER
		);
		directMessagesAdapter.setViewBinder(new TweetBinder());

		setListAdapter(directMessagesAdapter);

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
	}

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
	    
	    // TODO: This class is not fully implemented
	    // Compare it to the TweetListActivity
        Toast.makeText(this, R.string.unimplemented, Toast.LENGTH_SHORT).show();
        return;
	    
//		if (id <= 0) {
//			return;
//		}
//		Uri uri = ContentUris.withAppendedId(AndTweetDatabase.DirectMessages.CONTENT_URI, id);
//		String action = getIntent().getAction();
//		if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
//			setResult(RESULT_OK, new Intent().setData(uri));
//		} else {
//		    /** 
//		     * TODO: Such Activity is not implemented, resulting in the error:
//		     * ERROR/AndroidRuntime(944): 
//		     *  android.content.ActivityNotFoundException: No Activity found to handle Intent
//		     *  { action=android.intent.action.VIEW 
//		     *  data=content://com.xorcode.andtweet/directmessages/944723647
//		     */
//			startActivity(new Intent(Intent.ACTION_VIEW, uri));
//		}
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
			if (view.getLastVisiblePosition() >= mTotalItemCount - 1 && mTotalItemCount > 0) {
				if (getListView().getFooterViewsCount() == 1 && !mIsLoading) {
					mIsLoading = true;
					setProgressBarIndeterminateVisibility(true);
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
			case MSG_DIRECT_MESSAGES_CHANGED:
				int numTweets = msg.arg1;
				if (numTweets > 0) {
					mNM.cancelAll();
				}
				break;

			case MSG_DATA_LOADING:
				mIsLoading = msg.arg1 == 1 ? true : false;
		        setProgressBarIndeterminateVisibility(mIsLoading);
				break;

			case MSG_AUTHENTICATION_ERROR:
				switch (msg.arg1) {
				case MSG_MANUAL_RELOAD:
					setProgressBarIndeterminateVisibility(false);
					break;
				case MSG_UPDATE_STATUS:
					break;
				}
				showDialog(DIALOG_AUTHENTICATION_FAILED);
				break;

			case MSG_SERVICE_UNAVAILABLE_ERROR:
				switch (msg.arg1) {
				case MSG_MANUAL_RELOAD:
					setProgressBarIndeterminateVisibility(false);
					break;
				case MSG_UPDATE_STATUS:
					break;
				}
				showDialog(DIALOG_SERVICE_UNAVAILABLE);
				break;

			case MSG_MANUAL_RELOAD:
				mIsLoading = false;
				Toast.makeText(MessageListActivity.this, R.string.timeline_reloaded, Toast.LENGTH_SHORT).show();
				setProgressBarIndeterminateVisibility(false);
				break;

			case MSG_LOAD_ITEMS:
				switch (msg.arg1) {
				case STATUS_LOAD_ITEMS_SUCCESS:
					mIsLoading = false;
					mListFooter.setVisibility(View.INVISIBLE);
					((SimpleCursorAdapter) getListAdapter()).changeCursor(mCursor);
					setProgressBarIndeterminateVisibility(false);
					break;
				case STATUS_LOAD_ITEMS_FAILURE:
					break;
				}
				break;

			case MSG_CONNECTION_TIMEOUT_EXCEPTION:
				switch (msg.arg1) {
				case MSG_MANUAL_RELOAD:
					setProgressBarIndeterminateVisibility(false);
					break;
				case MSG_UPDATE_STATUS:
					break;
				}
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
	protected Runnable mSendMessage = new Runnable() {
		public void run() {
		}
	};

	/**
	 * Load more items into the list.
	 */
	protected Runnable mLoadListItems  = new Runnable() {
		public void run() {
			mCursor = ((PagedCursorAdapter) MessageListActivity.this.getListAdapter()).runQuery("LIMIT 0," + (++mCurrentPage * PAGE_SIZE));
			mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_LOAD_ITEMS, STATUS_LOAD_ITEMS_SUCCESS, 0), 400);
		}
	};
}
