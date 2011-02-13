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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.SearchRecentSuggestions;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;
import com.xorcode.andtweet.util.SelectionAndArgs;

/**
 * @author torgny.bjers
 */
public class TweetListActivity extends TimelineActivity {

    private static final String TAG = TweetListActivity.class.getSimpleName();

    // Context menu items
    public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 2;

    public static final int CONTEXT_MENU_ITEM_FAVORITE = Menu.FIRST + 3;

    public static final int CONTEXT_MENU_ITEM_DIRECT_MESSAGE = Menu.FIRST + 4;

    public static final int CONTEXT_MENU_ITEM_UNFOLLOW = Menu.FIRST + 5;

    public static final int CONTEXT_MENU_ITEM_BLOCK = Menu.FIRST + 6;

    public static final int CONTEXT_MENU_ITEM_RETWEET = Menu.FIRST + 7;

    public static final int CONTEXT_MENU_ITEM_PROFILE = Menu.FIRST + 8;

    public static final int CONTEXT_MENU_ITEM_DESTROY_FAVORITE = Menu.FIRST + 9;

    public static final int CONTEXT_MENU_ITEM_DESTROY_STATUS = Menu.FIRST + 10;

    // Views and widgets
    private Button mSendButton;

    private EditText mEditText;

    private TextView mCharsLeftText;

    // Text limits
    private int mCurrentChars = 0;

    private int mLimitChars = 140;

    private boolean mInitializing = false;

    // Database cursors
    private Cursor mFriendsCursor;

    /**
     * Id of the Tweet to which we are replying
     */
    protected long mReplyId = 0;

    /**
     * Id of the Tweet that was selected (clicked, or whose context menu item
     * was selected) TODO: clicked, restore position...
     */
    protected long mCurrentId = 0;

    // Table columns to use for the tweets data
    private static final String[] PROJECTION = new String[] {
            Tweets._ID, Tweets.AUTHOR_ID, Tweets.MESSAGE, Tweets.IN_REPLY_TO_AUTHOR_ID,
            Tweets.FAVORITED, Tweets.SENT_DATE
    };

    // Table columns to use for the user data
    private static final String[] FRIENDS_PROJECTION = new String[] {
            Users._ID, Users.AUTHOR_ID
    };

    /**
     * Called when the activity is first created.
     * 
     * @see android.app.Activity#onCreate(android.os.Bundle)
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "onCreate");
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BUNDLE_KEY_REPLY_ID)) {
                mReplyId = savedInstanceState.getLong(BUNDLE_KEY_REPLY_ID);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_IS_LOADING)) {
                mIsLoading = savedInstanceState.getBoolean(BUNDLE_KEY_IS_LOADING);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_CURRENT_ID)) {
                mCurrentId = savedInstanceState.getLong(BUNDLE_KEY_CURRENT_ID);
            }
        }

        // Set up views
        mSendButton = (Button) findViewById(R.id.messageEditSendButton);
        mEditText = (EditText) findViewById(R.id.edtTweetInput);
        mCharsLeftText = (TextView) findViewById(R.id.messageEditCharsLeftTextView);

        // Create list footer for loading messages
        mListFooter = new LinearLayout(getApplicationContext());
        mListFooter.setClickable(false);
        getListView().addFooterView(mListFooter);
        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        View tv = inflater.inflate(R.layout.item_loading, null);
        mListFooter.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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
    public void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        // All actions are actually search actions...
        // So get and process search query here
        doSearchQuery(newIntent, false, false);
    }

    /**
     * @param queryIntent
     * @param otherThread This method is being accessed from other thread
     * @param loadOneMorePage load one more page of tweets
     */
    protected void doSearchQuery(Intent queryIntent, boolean otherThread, boolean loadOneMorePage) {
        // The search query is provided as an "extra" string in the query intent
        // TODO maybe use mQueryString here...
        String queryString = queryIntent.getStringExtra(SearchManager.QUERY);
        Intent intent = getIntent();

        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "doSearchQuery; queryString=\"" + queryString + "\"; TimelineType="
                    + mTimelineType);
        }

        if (queryString != null && queryString.length() > 0) {
            // Record the query string in the recent queries suggestions
            // provider
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    TimelineSearchSuggestionProvider.AUTHORITY,
                    TimelineSearchSuggestionProvider.MODE);
            suggestions.saveRecentQuery(queryString, null);
        } else {
            // It looks like Android resets the Query we've set for Mentions...
            // Maybe we should define Mentions in other way?!
            queryString = "";
            if (mTimelineType == Tweets.TIMELINE_TYPE_MENTIONS) {
                queryString = "@" + TwitterUser.getTwitterUser(this).getUsername();
            }
        }
        intent.putExtra(SearchManager.QUERY, queryString);

        // TODO: Too many contentUri assignments :-(
        Uri contentUri = Tweets.CONTENT_URI;
        if (queryString != null && queryString.length() > 0) {
            contentUri = Tweets.SEARCH_URI;
        }

        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = Tweets.DEFAULT_SORT_ORDER;
        long firstItemId = -1;

        // Extract content URI from the search data
        Bundle appData = queryIntent.getBundleExtra(SearchManager.APP_DATA);
        if (appData != null) {
            sa.addSelection(appData.getString("selection"), appData.getStringArray("selectionArgs"));
            contentUri = appData.getParcelable("content_uri");
        }
        if (queryString != null && queryString.length() > 0) {
            contentUri = Uri.withAppendedPath(contentUri, Uri.encode(queryString));
        } else {
            contentUri = Tweets.CONTENT_URI;
        }
        if (!contentUri.equals(intent.getData())) {
            intent.setData(contentUri);
        }

        if (appData == null || sa.nArgs == 0) {
            // In fact this is needed every time you want to load next page of
            // tweets.
            // So we have to duplicate here everything we set in
            // com.xorcode.andtweet.TimelineActivity.onOptionsItemSelected()
            sa.clear();
            sa.addSelection(
                    Tweets.TWEET_TYPE + " IN (?, ?)",
                    new String[] {
                            String.valueOf(Tweets.TIMELINE_TYPE_FRIENDS),
                            String.valueOf(Tweets.TIMELINE_TYPE_MENTIONS)
                    });
            if (mTimelineType == Tweets.TIMELINE_TYPE_FAVORITES) {
                sa.addSelection(AndTweetDatabase.Tweets.FAVORITED + " = ?", new String[] {
                    "1"
                });
            }
        }

        if (!positionLoaded) {
            // We have to ensure that saved position will be
            // loaded from database into the list
            firstItemId = getSavedPosition();
        }

        int nTweets = 0;
        if (mCursor != null && !mCursor.isClosed()) {
            if (positionLoaded) {
                // If position is NOT loaded - this cursor is from other
                // timeline/search
                // and we shouldn't care how much rows are there.
                nTweets = mCursor.getCount();
            }
            if (!otherThread) {
                mCursor.close();
            }
        }

        if (firstItemId > 0) {
            if (sa.nArgs == 0) {
                sa.addSelection(
                        "AndTweetDatabase.Tweets.TWEET_TYPE" + " IN (?, ?)" + ")",
                        new String[] {
                                String.valueOf(Tweets.TIMELINE_TYPE_FRIENDS),
                                String.valueOf(Tweets.TIMELINE_TYPE_MENTIONS)
                        });
            }
            sa.addSelection(Tweets._ID + " >= ?", new String[] {
                String.valueOf(firstItemId)
            });
        } else {
            if (loadOneMorePage) {
                nTweets += PAGE_SIZE;
            } else if (nTweets < PAGE_SIZE) {
                nTweets = PAGE_SIZE;
            }
            sortOrder += " LIMIT 0," + nTweets;
        }

        // This is for testing pruneOldRecords
//        try {
//            FriendTimeline fl = new FriendTimeline(TweetListActivity.this,
//                    AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
//            fl.pruneOldRecords();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        mCursor = getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        if (!otherThread) {
            createAdapters();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "onStart");
        }
        Intent intent = getIntent();
        try {
            // The "ContentResolver" is (by default...) AndTweetProvider
            // instance
            mFriendsCursor = getContentResolver().query(Users.CONTENT_URI, FRIENDS_PROJECTION,
                    null, null, Users.DEFAULT_SORT_ORDER);
        } catch (SQLiteDiskIOException e) {
            showDialog(DIALOG_EXTERNAL_STORAGE_MISSING);
            return;
        }
        doSearchQuery(intent, false, false);

        if (hasHardwareKeyboard()) {
            mEditText.requestFocus();
        }
        if ("com.xorcode.andtweet.INITIALIZE".equals(intent.getAction())) {
            intent.setAction(null);
            if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
                Log.d(TAG, "onStart() Initializing...");
            }
            mInitializing = true;
            // Clean up databases in case there is data in there
            getContentResolver().delete(AndTweetDatabase.Tweets.CONTENT_URI, null, null);
            getContentResolver().delete(AndTweetDatabase.DirectMessages.CONTENT_URI, null, null);
            getContentResolver().delete(AndTweetDatabase.Users.CONTENT_URI, null, null);

            // TODO: Manual reload should start for every new user, not only for
            // "first run" of the application
            manualReload();
        } else {
            bindToService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "onResume");
        }
        mCharsLeftText.setText(String.valueOf(mLimitChars - mEditText.length()));
    }

    @Override
    protected void onStop() {
        super.onStop();
        /*
         * SimpleCursorAdapter a = (SimpleCursorAdapter) mEditText.getAdapter();
         * if (a != null && a.getCursor() != null && !a.getCursor().isClosed())
         * { a.getCursor().close(); }
         */
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
        outState.putBoolean(BUNDLE_KEY_IS_LOADING, mIsLoading);
        outState.putLong(BUNDLE_KEY_CURRENT_ID, mCurrentId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reload_menu_item:
                manualReload();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void manualReload() {
        // Only newer tweets (newer that last loaded) are being loaded
        // from the Internet,
        // old tweets are not reloaded.
        showDialog(DIALOG_TIMELINE_LOADING);
        mListFooter.setVisibility(View.VISIBLE);
        Thread thread = new Thread(mManualReload);
        thread.start();
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

        int m = 0;

        // Add menu items
        menu.add(0, CONTEXT_MENU_ITEM_REPLY, m++, R.string.menu_item_reply);
        menu.add(0, CONTEXT_MENU_ITEM_RETWEET, m++, R.string.menu_item_retweet);
        // menu.add(0, CONTEXT_MENU_ITEM_DIRECT_MESSAGE, m++,
        // R.string.menu_item_direct_message);
        // menu.add(0, CONTEXT_MENU_ITEM_UNFOLLOW, m++,
        // R.string.menu_item_unfollow);
        // menu.add(0, CONTEXT_MENU_ITEM_BLOCK, m++, R.string.menu_item_block);
        // menu.add(0, CONTEXT_MENU_ITEM_PROFILE, m++,
        // R.string.menu_item_view_profile);

        // Get the record for the currently selected item
        Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, info.id);
        Cursor c = getContentResolver().query(uri, new String[] {
                Tweets._ID, Tweets.MESSAGE, Tweets.AUTHOR_ID, Tweets.FAVORITED
        }, null, null, null);
        try {
            c.moveToFirst();
            menu.setHeaderTitle(c.getString(c.getColumnIndex(Tweets.MESSAGE)));
            if (c.getInt(c.getColumnIndex(Tweets.FAVORITED)) == 1) {
                menu.add(0, CONTEXT_MENU_ITEM_DESTROY_FAVORITE, m++,
                        R.string.menu_item_destroy_favorite);
            } else {
                menu.add(0, CONTEXT_MENU_ITEM_FAVORITE, m++, R.string.menu_item_favorite);
            }
            if (mSP.getString("twitter_username", null).equals(
                    c.getString(c.getColumnIndex(Tweets.AUTHOR_ID)))) {
                menu.add(0, CONTEXT_MENU_ITEM_DESTROY_STATUS, m++,
                        R.string.menu_item_destroy_status);
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreateContextMenu: " + e.toString());
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
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

        mCurrentId = info.id;

        Uri uri;
        Cursor c;
        Thread thread;

        switch (item.getItemId()) {
            case CONTEXT_MENU_ITEM_REPLY:
                uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, info.id);
                c = getContentResolver().query(uri, new String[] {
                        Tweets._ID, Tweets.AUTHOR_ID
                }, null, null, null);
                try {
                    c.moveToFirst();
                    if (hasHardwareKeyboard()) {
                        mEditText.requestFocus();
                    }
                    String reply = "@" + c.getString(c.getColumnIndex(Tweets.AUTHOR_ID)) + " ";
                    mEditText.setText("");
                    mEditText.append(reply, 0, reply.length());
                    mReplyId = c.getLong(c.getColumnIndex(Tweets._ID));
                } catch (Exception e) {
                    Log.e(TAG, "onContextItemSelected: " + e.toString());
                    return false;
                } finally {
                    if (c != null && !c.isClosed())
                        c.close();
                }
                return true;

            case CONTEXT_MENU_ITEM_RETWEET:
                uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, info.id);
                c = getContentResolver().query(uri, new String[] {
                        Tweets._ID, Tweets.AUTHOR_ID, Tweets.MESSAGE
                }, null, null, null);
                try {
                    c.moveToFirst();
                    if (hasHardwareKeyboard()) {
                        mEditText.requestFocus();
                    }
                    StringBuilder message = new StringBuilder();
                    String reply = "RT @" + c.getString(c.getColumnIndex(Tweets.AUTHOR_ID)) + " ";
                    message.append(reply);
                    CharSequence text = c.getString(c.getColumnIndex(Tweets.MESSAGE));
                    int len = 140 - reply.length() - 3;
                    if (text.length() < len) {
                        len = text.length();
                    }
                    message.append(text, 0, len);
                    if (message.length() == 137) {
                        message.append("...");
                    }
                    mEditText.setText("");
                    mEditText.append(message, 0, message.length());
                } catch (Exception e) {
                    Log.e(TAG, "onContextItemSelected: " + e.toString());
                    return false;
                } finally {
                    if (c != null && !c.isClosed())
                        c.close();
                }
                return true;

            case CONTEXT_MENU_ITEM_DESTROY_STATUS:
                showDialog(DIALOG_EXECUTING_COMMAND);
                thread = new Thread(mDestroyStatus);
                thread.start();
                return true;

            case CONTEXT_MENU_ITEM_FAVORITE:
                showDialog(DIALOG_EXECUTING_COMMAND);
                thread = new Thread(mCreateFavorite);
                thread.start();
                return true;

            case CONTEXT_MENU_ITEM_DESTROY_FAVORITE:
                showDialog(DIALOG_EXECUTING_COMMAND);
                thread = new Thread(mDestroyFavorite);
                thread.start();
                return true;

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
     * Send the message.
     */
    void sendMessage() {
        String msg = mEditText.getText().toString();
        if (TextUtils.isEmpty(msg.trim())) {
            Toast.makeText(TweetListActivity.this, R.string.cannot_send_empty_message,
                    Toast.LENGTH_SHORT).show();
        } else {
            showDialog(DIALOG_SENDING_MESSAGE);
            Thread thread = new Thread(mSendUpdate);
            thread.start();
            closeSoftKeyboard();
        }
    }

    /**
     * Close the on-screen keyboard.
     */
    private void closeSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }

    /**
     * Initialize UI
     */
    @Override
    protected void initUI() {
        super.initUI();

        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendMessage();
            }
        });

        mEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                mCurrentChars = s.length();
                mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mEditText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    mCurrentChars = mEditText.length();
                    if (mCurrentChars == 0) {
                        mReplyId = 0;
                    }
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                            sendMessage();
                            return true;
                        case KeyEvent.KEYCODE_ENTER:
                            if (event.isAltPressed()) {
                                mEditText.append("\n");
                                return true;
                            }
                        default:
                            if (keyCode != KeyEvent.KEYCODE_DEL && mCurrentChars > mLimitChars) {
                                return true;
                            }
                            mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));
                            break;
                    }
                }
                return false;
            }
        });

        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null) {
                    if (event.isAltPressed()) {
                        return false;
                    }
                }
                sendMessage();
                return true;
            }
        });
    }

    /**
     * Create adapters
     */
    private void createAdapters() {
        int listItemId = R.layout.tweetlist_item;
        if (mSP.getBoolean("appearance_use_avatars", false)) {
            listItemId = R.layout.tweetlist_item_avatar;
        }
        PagedCursorAdapter tweetsAdapter = new PagedCursorAdapter(TweetListActivity.this,
                listItemId, mCursor, new String[] {
                        Tweets.AUTHOR_ID, Tweets.MESSAGE, Tweets.SENT_DATE, Tweets.FAVORITED
                }, new int[] {
                        R.id.tweet_screen_name, R.id.tweet_message, R.id.tweet_sent,
                        R.id.tweet_favorite
                }, getIntent().getData(), PROJECTION, Tweets.DEFAULT_SORT_ORDER);
        tweetsAdapter.setViewBinder(new TweetBinder());

        setListAdapter(tweetsAdapter);

        SearchableCursorAdapter friendsAdapter = new SearchableCursorAdapter(this,
                android.R.layout.simple_dropdown_item_1line, mFriendsCursor, new String[] {
                    Users.AUTHOR_ID
                }, new int[] {
                    android.R.id.text1
                }, Users.CONTENT_URI, FRIENDS_PROJECTION, Users.DEFAULT_SORT_ORDER);
        try {
            friendsAdapter.setStringConversionColumn(mFriendsCursor
                    .getColumnIndexOrThrow(Users.AUTHOR_ID));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not set string conversion column on mFriendsCursor", e);
        }

        // mEditText.setAdapter(friendsAdapter);
        // mEditText.setTokenizer(new AtTokenizer());
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
        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
            Log.v(TAG, "onItemClick, id=" + id);
        }
        if (id <= 0) {
            return;
        }
        Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
                Log.d(TAG, "onItemClick, setData=" + uri);
            }
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
                Log.d(TAG, "onItemClick, startActivity=" + uri);
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        mTotalItemCount = totalItemCount;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        super.onScrollStateChanged(view, scrollState);
        if (mIsLoading) {
            return;
        }
        switch (scrollState) {
            case SCROLL_STATE_IDLE:
                if (view.getLastVisiblePosition() >= mTotalItemCount - 1 && mTotalItemCount > 0) {
                    if (getListView().getFooterViewsCount() == 1 && !mIsLoading) {
                        mIsLoading = true;
                        // setProgressBarIndeterminateVisibility(true);
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
        // First set less detailed title
        super.updateTitle();
        // Then start asynchronous task that will set detailed info
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
            JSONObject result = null;
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
                        try {
                            dismissDialog(DIALOG_TIMELINE_LOADING);
                        } catch (IllegalArgumentException e) {
                            AndTweetService.d(TAG, "", e);
                        }
                    }
                    break;

                case MSG_UPDATE_STATUS:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TweetListActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TweetListActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        // The tweet was sent successfully
                        FriendTimeline fl = new FriendTimeline(TweetListActivity.this,
                                AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
                        try {
                            fl.insertFromJSONObject(result, true);
                        } catch (JSONException e) {
                            Toast.makeText(TweetListActivity.this, e.toString(), Toast.LENGTH_SHORT)
                                    .show();
                        }
                        Toast.makeText(TweetListActivity.this, R.string.message_sent,
                                Toast.LENGTH_SHORT).show();
                        mReplyId = 0;
                        // So we may clear the text box with the sent tweet
                        // text...
                        mEditText.setText("");
                        if (hasHardwareKeyboard()) {
                            mEditText.requestFocus();
                        }
                    }
                    try {
                        dismissDialog(DIALOG_SENDING_MESSAGE);
                    } catch (IllegalArgumentException e) {
                        AndTweetService.d(TAG, "", e);
                    }
                    break;

                case MSG_AUTHENTICATION_ERROR:
                    switch (msg.arg1) {
                        case MSG_MANUAL_RELOAD:
                            try {
                                dismissDialog(DIALOG_TIMELINE_LOADING);
                            } catch (IllegalArgumentException e) {
                                AndTweetService.d(TAG, "", e);
                            }
                            break;
                        case MSG_UPDATE_STATUS:
                            try {
                                dismissDialog(DIALOG_SENDING_MESSAGE);
                            } catch (IllegalArgumentException e) {
                                AndTweetService.d(TAG, "", e);
                            }
                            break;
                    }
                    mListFooter.setVisibility(View.INVISIBLE);
                    showDialog(DIALOG_AUTHENTICATION_FAILED);
                    break;

                case MSG_SERVICE_UNAVAILABLE_ERROR:
                    switch (msg.arg1) {
                        case MSG_MANUAL_RELOAD:
                            try {
                                dismissDialog(DIALOG_TIMELINE_LOADING);
                            } catch (IllegalArgumentException e) {
                                AndTweetService.d(TAG, "", e);
                            }
                            break;
                        case MSG_UPDATE_STATUS:
                            try {
                                dismissDialog(DIALOG_SENDING_MESSAGE);
                            } catch (IllegalArgumentException e) {
                                AndTweetService.d(TAG, "", e);
                            }
                            break;
                    }
                    mListFooter.setVisibility(View.INVISIBLE);
                    showDialog(DIALOG_SERVICE_UNAVAILABLE);
                    break;

                case MSG_MANUAL_RELOAD:
                    try {
                        dismissDialog(DIALOG_TIMELINE_LOADING);
                    } catch (IllegalArgumentException e) {
                        AndTweetService.d(TAG, "", e);
                    }
                    mIsLoading = false;
                    Toast.makeText(TweetListActivity.this, R.string.timeline_reloaded,
                            Toast.LENGTH_SHORT).show();
                    mListFooter.setVisibility(View.INVISIBLE);
                    updateTitle();
                    if (mInitializing) {
                        mInitializing = false;
                        bindToService();
                    }
                    break;

                case MSG_LOAD_ITEMS:
                    mListFooter.setVisibility(View.INVISIBLE);
                    switch (msg.arg1) {
                        case STATUS_LOAD_ITEMS_SUCCESS:
                            updateTitle();
                            mIsLoading = false;
                            mListFooter.setVisibility(View.INVISIBLE);
                            ((SimpleCursorAdapter) getListAdapter()).changeCursor(mCursor);
                            // setProgressBarIndeterminateVisibility(false);
                            break;
                        case STATUS_LOAD_ITEMS_FAILURE:
                            break;
                    }
                    break;

                case MSG_UPDATED_TITLE:
                    JSONObject status = (JSONObject) msg.obj;
                    try {
                        if (status != null) {
                            TweetListActivity.super.updateTitle(status.getInt("remaining_hits")
                                    + "/" + status.getInt("hourly_limit"));
                        } else {
                            setTitle("(msg is null)");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;

                case MSG_CONNECTION_TIMEOUT_EXCEPTION:
                    switch (msg.arg1) {
                        case MSG_MANUAL_RELOAD:
                            try {
                                dismissDialog(DIALOG_TIMELINE_LOADING);
                            } catch (IllegalArgumentException e) {
                                AndTweetService.d(TAG, "", e);
                            }
                            break;
                        case MSG_UPDATE_STATUS:
                            try {
                                dismissDialog(DIALOG_SENDING_MESSAGE);
                            } catch (IllegalArgumentException e) {
                                AndTweetService.d(TAG, "", e);
                            }
                            break;
                    }
                    mListFooter.setVisibility(View.INVISIBLE);
                    showDialog(DIALOG_CONNECTION_TIMEOUT);
                    break;

                case MSG_STATUS_DESTROY:
                    result = (JSONObject) msg.obj;
                    if (result.optString("error").length() > 0) {
                        Toast.makeText(TweetListActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        FriendTimeline fl = new FriendTimeline(TweetListActivity.this,
                                AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
                        try {
                            fl.destroyStatus(result.getLong("id"));
                        } catch (JSONException e) {
                            Toast.makeText(TweetListActivity.this, e.toString(), Toast.LENGTH_SHORT)
                                    .show();
                        }
                        Toast.makeText(TweetListActivity.this, R.string.status_destroyed,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
                    }
                    try {
                        dismissDialog(DIALOG_EXECUTING_COMMAND);
                    } catch (IllegalArgumentException e) {
                        AndTweetService.d(TAG, "", e);
                    }
                    break;

                case MSG_FAVORITE_CREATE:
                    result = (JSONObject) msg.obj;
                    if (result.optString("error").length() > 0) {
                        Toast.makeText(TweetListActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        try {
                            Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI,
                                    result.getLong("id"));
                            Cursor c = getContentResolver().query(uri, new String[] {
                                    Tweets._ID, Tweets.AUTHOR_ID, Tweets.TWEET_TYPE
                            }, null, null, null);
                            try {
                                c.moveToFirst();
                                FriendTimeline fl = new FriendTimeline(TweetListActivity.this,
                                        c.getInt(c.getColumnIndex(Tweets.TWEET_TYPE)));
                                fl.insertFromJSONObject(result, true);
                            } catch (Exception e) {
                                Log.e(TAG, "handleMessage: " + e.toString());
                            } finally {
                                if (c != null && !c.isClosed())
                                    c.close();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(TweetListActivity.this, e.toString(), Toast.LENGTH_SHORT)
                                    .show();
                        }
                        Toast.makeText(TweetListActivity.this, R.string.favorite_created,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
                    }
                    try {
                        dismissDialog(DIALOG_EXECUTING_COMMAND);
                    } catch (IllegalArgumentException e) {
                        AndTweetService.d(TAG, "", e);
                    }
                    break;

                case MSG_FAVORITE_DESTROY:
                    result = (JSONObject) msg.obj;
                    if (result.optString("error").length() > 0) {
                        Toast.makeText(TweetListActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        try {
                            Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI,
                                    result.getLong("id"));
                            Cursor c = getContentResolver().query(uri, new String[] {
                                    Tweets._ID, Tweets.AUTHOR_ID, Tweets.TWEET_TYPE
                            }, null, null, null);
                            try {
                                c.moveToFirst();
                                FriendTimeline fl = new FriendTimeline(TweetListActivity.this,
                                        c.getInt(c.getColumnIndex(Tweets.TWEET_TYPE)));
                                fl.insertFromJSONObject(result, true);
                            } catch (Exception e) {
                                Log.e(TAG, "handleMessage: " + e.toString());
                            } finally {
                                if (c != null && !c.isClosed())
                                    c.close();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(TweetListActivity.this, e.toString(), Toast.LENGTH_SHORT)
                                    .show();
                        }
                        Toast.makeText(TweetListActivity.this, R.string.favorite_destroyed,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
                    }
                    try {
                        dismissDialog(DIALOG_EXECUTING_COMMAND);
                    } catch (IllegalArgumentException e) {
                        AndTweetService.d(TAG, "", e);
                    }
                    break;

                case MSG_CONNECTION_EXCEPTION:
                    switch (msg.arg1) {
                        case MSG_FAVORITE_CREATE:
                        case MSG_FAVORITE_DESTROY:
                        case MSG_STATUS_DESTROY:
                            try {
                                dismissDialog(DIALOG_EXECUTING_COMMAND);
                            } catch (IllegalArgumentException e) {
                                AndTweetService.d(TAG, "", e);
                            }
                            break;
                    }
                    Toast.makeText(TweetListActivity.this, R.string.error_connection_error,
                            Toast.LENGTH_SHORT).show();
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    };

    /**
     * Handles threaded sending of the message, typed in the mEditText text box.
     * Currently queued message sending is not supported (if initial sending
     * failed for some reason). In a case of an error the message remains intact
     * in the mEditText text box and User has to send the same message again
     * manually (e.g. by pressing "Send" button),
     */
    protected Runnable mSendUpdate = new Runnable() {
        public void run() {
            String message = mEditText.getText().toString();
            TwitterUser tu = TwitterUser.getTwitterUser(TweetListActivity.this);
            JSONObject result = new JSONObject();
            try {
                result = tu.getConnection().updateStatus(message.trim(), mReplyId);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.toString());
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_UPDATE_STATUS, 0));
            } catch (ConnectionException e) {
                Log.e(TAG, "mSendUpdate Connection Exception: " + e.toString());
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_UPDATE_STATUS, 0));
                return;
            } catch (ConnectionAuthenticationException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTHENTICATION_ERROR,
                        MSG_UPDATE_STATUS, 0));
                return;
            } catch (ConnectionUnavailableException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR,
                        MSG_UPDATE_STATUS, 0));
                return;
            } catch (SocketTimeoutException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_UPDATE_STATUS, 0));
                return;
            } catch (Exception e) {
                e.printStackTrace();
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_UPDATE_STATUS, 0));
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
            int aNewTweets = 0;
            int aReplyCount = 0;
            try {
                FriendTimeline fl = new FriendTimeline(TweetListActivity.this,
                        AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS);
                fl.loadTimeline(mInitializing);
                aReplyCount = fl.replyCount();

                fl = new FriendTimeline(TweetListActivity.this,
                        AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS);
                fl.loadTimeline(mInitializing);
                aNewTweets = fl.newCount();
                aReplyCount += fl.replyCount();
            } catch (ConnectionException e) {
                Log.e(TAG, "mManualReload Connection Exception: " + e.toString());
                return;
            } catch (SQLiteConstraintException e) {
                Log.e(TAG, "mManualReload database exception: " + e.toString());
                return;
            } catch (JSONException e) {
                Log.e(TAG, "mManualReload JSON exception: " + e.toString());
                return;
            } catch (ConnectionAuthenticationException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTHENTICATION_ERROR,
                        MSG_MANUAL_RELOAD, 0));
                return;
            } catch (ConnectionUnavailableException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR,
                        MSG_MANUAL_RELOAD, 0));
                return;
            } catch (SocketTimeoutException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_MANUAL_RELOAD, 0));
                return;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_MANUAL_RELOAD, aNewTweets, aReplyCount));
        }
    };

    /**
     * Load more items from the database into the list This procedure doesn't
     * download any new tweets from the Internet
     */
    protected Runnable mLoadListItems = new Runnable() {
        public void run() {
            doSearchQuery(TweetListActivity.this.getIntent(), true, true);
            mHandler.sendMessageDelayed(
                    mHandler.obtainMessage(MSG_LOAD_ITEMS, STATUS_LOAD_ITEMS_SUCCESS, 0), 400);
        }
    };

    /**
     * Update the title with the number of remaining API calls.
     */
    protected Runnable mUpdateTitle = new Runnable() {
        public void run() {
            try {
                JSONObject status = TwitterUser.getTwitterUser(TweetListActivity.this)
                        .getConnection().rateLimitStatus();
                mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATED_TITLE, status));
            } catch (JSONException e) {
            } catch (ConnectionException e) {
            } catch (ConnectionAuthenticationException e) {
            } catch (ConnectionUnavailableException e) {
            } catch (SocketTimeoutException e) {
            }
        }
    };

    /**
     * Handles threaded removal of statuses.
     */
    protected Runnable mDestroyStatus = new Runnable() {
        public void run() {
            JSONObject result = new JSONObject();
            try {
                result = TwitterUser.getTwitterUser(TweetListActivity.this).getConnection()
                        .destroyStatus(mCurrentId);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.toString());
            } catch (ConnectionException e) {
                Log.e(TAG, "mDestroyStatus Connection Exception: " + e.toString());
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_EXCEPTION,
                        MSG_STATUS_DESTROY, Integer.parseInt(e.toString())));
                return;
            } catch (ConnectionAuthenticationException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTHENTICATION_ERROR,
                        MSG_STATUS_DESTROY, 0));
                return;
            } catch (ConnectionUnavailableException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR,
                        MSG_STATUS_DESTROY, 0));
                return;
            } catch (SocketTimeoutException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_STATUS_DESTROY, 0));
                return;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STATUS_DESTROY, result));
        }
    };

    /**
     * Handles threaded creation of favorites
     */
    protected Runnable mCreateFavorite = new Runnable() {
        public void run() {
            JSONObject result = new JSONObject();
            try {
                result = TwitterUser.getTwitterUser(TweetListActivity.this).getConnection()
                        .createFavorite(mCurrentId);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.toString());
            } catch (ConnectionException e) {
                Log.e(TAG, "mCreateFavorite Connection Exception: " + e.toString());
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_EXCEPTION,
                        MSG_FAVORITE_CREATE, Integer.parseInt(e.toString())));
                return;
            } catch (ConnectionAuthenticationException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTHENTICATION_ERROR,
                        MSG_FAVORITE_CREATE, 0));
                return;
            } catch (ConnectionUnavailableException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR,
                        MSG_FAVORITE_CREATE, 0));
                return;
            } catch (SocketTimeoutException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_FAVORITE_CREATE, 0));
                return;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_FAVORITE_CREATE, result));
        }
    };

    /**
     * Handles threaded creation of favorites
     */
    protected Runnable mDestroyFavorite = new Runnable() {
        public void run() {
            JSONObject result = new JSONObject();
            try {
                result = TwitterUser.getTwitterUser(TweetListActivity.this).getConnection()
                        .destroyFavorite(mCurrentId);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.toString());
            } catch (ConnectionException e) {
                Log.e(TAG, "mDestroyFavorite Connection Exception: " + e.toString());
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_EXCEPTION,
                        MSG_FAVORITE_DESTROY, Integer.parseInt(e.toString())));
                return;
            } catch (ConnectionAuthenticationException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AUTHENTICATION_ERROR,
                        MSG_FAVORITE_DESTROY, 0));
                return;
            } catch (ConnectionUnavailableException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR,
                        MSG_FAVORITE_DESTROY, 0));
                return;
            } catch (SocketTimeoutException e) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_TIMEOUT_EXCEPTION,
                        MSG_FAVORITE_DESTROY, 0));
                return;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_FAVORITE_DESTROY, result));
        }
    };
}
