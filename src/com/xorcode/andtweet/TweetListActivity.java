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

import org.json.JSONObject;

import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.SearchRecentSuggestions;
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

import com.xorcode.andtweet.AndTweetService.CommandData;
import com.xorcode.andtweet.AndTweetService.CommandEnum;
import com.xorcode.andtweet.TwitterUser.CredentialsVerified;
import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.data.MyPreferences;
import com.xorcode.andtweet.data.PagedCursorAdapter;
import com.xorcode.andtweet.data.TimelineSearchSuggestionProvider;
import com.xorcode.andtweet.data.TweetBinder;
import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.util.MyLog;
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

    private boolean mInitializing = false;

    // Table columns to use for the tweets data
    private static final String[] PROJECTION = new String[] {
            Tweets._ID, Tweets.AUTHOR_ID, Tweets.MESSAGE, Tweets.IN_REPLY_TO_AUTHOR_ID,
            Tweets.FAVORITED, Tweets.SENT_DATE
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

        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onCreate");
        }

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
        queryListData(newIntent, false, false);
    }

    /**
     * Prepare query to the ContentProvider (to the database) and load List of Tweets with data
     * @param queryIntent
     * @param otherThread This method is being accessed from other thread
     * @param loadOneMorePage load one more page of tweets
     */
    protected void queryListData(Intent queryIntent, boolean otherThread, boolean loadOneMorePage) {
        // The search query is provided as an "extra" string in the query intent
        // TODO maybe use mQueryString here...
        String queryString = queryIntent.getStringExtra(SearchManager.QUERY);
        Intent intent = getIntent();

        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "doSearchQuery; queryString=\"" + queryString + "\"; TimelineType="
                    + mTimelineType);
        }

        Uri contentUri = Tweets.CONTENT_URI;

        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = Tweets.DEFAULT_SORT_ORDER;
        // Id of the last (oldest) tweet to retrieve 
        long lastItemId = -1;
        
        if (queryString != null && queryString.length() > 0) {
            // Record the query string in the recent queries suggestions
            // provider
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    TimelineSearchSuggestionProvider.AUTHORITY,
                    TimelineSearchSuggestionProvider.MODE);
            suggestions.saveRecentQuery(queryString, null);

            contentUri = Tweets.SEARCH_URI;
            contentUri = Uri.withAppendedPath(contentUri, Uri.encode(queryString));
        }
        intent.putExtra(SearchManager.QUERY, queryString);

        if (!contentUri.equals(intent.getData())) {
            intent.setData(contentUri);
        }

        if (sa.nArgs == 0) {
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
            if (mTimelineType == Tweets.TIMELINE_TYPE_MENTIONS) {
                sa.addSelection(Tweets.MESSAGE + " LIKE ?", new String[] {
                        "%@" + TwitterUser.getTwitterUser().getUsername() + "%"
                    });
            }
        }

        if (!positionRestored) {
            // We have to ensure that saved position will be
            // loaded from database into the list
            lastItemId = getSavedPosition(true);
        }

        int nTweets = 0;
        if (mCursor != null && !mCursor.isClosed()) {
            if (positionRestored) {
                // If position is NOT loaded - this cursor is from other
                // timeline/search
                // and we shouldn't care how much rows are there.
                nTweets = mCursor.getCount();
            }
            if (!otherThread) {
                mCursor.close();
            }
        }

        if (lastItemId > 0) {
            if (sa.nArgs == 0) {
                sa.addSelection(
                        "AndTweetDatabase.Tweets.TWEET_TYPE" + " IN (?, ?)" + ")",
                        new String[] {
                                String.valueOf(Tweets.TIMELINE_TYPE_FRIENDS),
                                String.valueOf(Tweets.TIMELINE_TYPE_MENTIONS)
                        });
            }
            sa.addSelection(Tweets._ID + " >= ?", new String[] {
                String.valueOf(lastItemId)
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
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onStart");
        }
        Intent intent = getIntent();
        queryListData(intent, false, false);

        if (mTweetEditor.isVisible()) {
            // This is done to request focus (if we need this...)
            mTweetEditor.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onResume");
        }
        
        if (TwitterUser.getTwitterUser().getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
            if (!TwitterUser.getTwitterUser().getSharedPreferences().getBoolean("loadedOnce", false)) {
                TwitterUser.getTwitterUser().getSharedPreferences().edit().putBoolean("loadedOnce", true).commit();
                // One-time "manually" load tweets from the Internet for the new TwitterUser
                manualReload();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
        //disconnectService();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(BUNDLE_KEY_IS_LOADING, mIsLoading);
        super.onSaveInstanceState(outState);
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
            if (MyPreferences.getDefaultSharedPreferences().getString(MyPreferences.KEY_TWITTER_USERNAME, null).equals(
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

        switch (item.getItemId()) {
            case CONTEXT_MENU_ITEM_REPLY:
                uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, info.id);
                c = getContentResolver().query(uri, new String[] {
                        Tweets._ID, Tweets.AUTHOR_ID
                }, null, null, null);
                try {
                    c.moveToFirst();
                    String reply = "@" + c.getString(c.getColumnIndex(Tweets.AUTHOR_ID)) + " ";
                    long replyId = c.getLong(c.getColumnIndex(Tweets._ID));
                    mTweetEditor.startEditing(reply, replyId);
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
                    mTweetEditor.startEditing(message.toString(), 0);
                } catch (Exception e) {
                    Log.e(TAG, "onContextItemSelected: " + e.toString());
                    return false;
                } finally {
                    if (c != null && !c.isClosed())
                        c.close();
                }
                return true;

            case CONTEXT_MENU_ITEM_DESTROY_STATUS:
                sendCommand( new CommandData(CommandEnum.DESTROY_STATUS, mCurrentId));
                return true;

            case CONTEXT_MENU_ITEM_FAVORITE:
                sendCommand( new CommandData(CommandEnum.CREATE_FAVORITE, mCurrentId));
                return true;

            case CONTEXT_MENU_ITEM_DESTROY_FAVORITE:
                sendCommand( new CommandData(CommandEnum.DESTROY_FAVORITE, mCurrentId));
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
     * Create adapters
     */
    private void createAdapters() {
        int listItemId = R.layout.tweetlist_item;
        if (MyPreferences.getDefaultSharedPreferences().getBoolean("appearance_use_avatars", false)) {
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
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onItemClick, id=" + id);
        }
        if (id <= 0) {
            return;
        }
        Uri uri = ContentUris.withAppendedId(Tweets.CONTENT_URI, id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            if (MyLog.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onItemClick, setData=" + uri);
            }
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            if (MyLog.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onItemClick, startActivity=" + uri);
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        mTotalItemCount = totalItemCount;

        if (positionRestored && !mIsLoading) {
            // Idea from
            // http://stackoverflow.com/questions/1080811/android-endless-list
            boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                    && (firstVisibleItem + visibleItemCount >= totalItemCount);
            if (loadMore) {
                mIsLoading = true;
                Log.d(TAG, "Start Loading more items, total=" + totalItemCount);
                // setProgressBarIndeterminateVisibility(true);
                mListFooter.setVisibility(View.VISIBLE);
                Thread thread = new Thread(mLoadListItems);
                thread.start();
            }
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
        sendCommand(new CommandData(CommandEnum.RATE_LIMIT_STATUS));
    }

    {
    /**
     * Message handler for messages from threads.
     */
    mHandler = new Handler() {
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
                    mIsLoading = (msg.arg1 == 1) ? true : false;
                    if (!mIsLoading) {
                        Toast.makeText(TweetListActivity.this, R.string.timeline_reloaded,
                                Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(TweetListActivity.this, R.string.message_sent,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;

                case MSG_AUTHENTICATION_ERROR:
                    mListFooter.setVisibility(View.INVISIBLE);
                    showDialog(DIALOG_AUTHENTICATION_FAILED);
                    break;

                case MSG_SERVICE_UNAVAILABLE_ERROR:
                    mListFooter.setVisibility(View.INVISIBLE);
                    showDialog(DIALOG_SERVICE_UNAVAILABLE);
                    break;

                case MSG_MANUAL_RELOAD:
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
                            mListFooter.setVisibility(View.INVISIBLE);
                            if (positionRestored) {
                                // This will prevent continuous loading...
                                if (mCursor.getCount() > getListAdapter().getCount()) {
                                    ((SimpleCursorAdapter) getListAdapter()).changeCursor(mCursor);
                                }
                            }
                            mIsLoading = false;
                            // setProgressBarIndeterminateVisibility(false);
                            break;
                        case STATUS_LOAD_ITEMS_FAILURE:
                            break;
                    }
                    break;

                case MSG_UPDATED_TITLE:
                    if (msg.arg1 > 0) {
                        TweetListActivity.super.updateTitle(msg.arg1 + "/" + msg.arg2);
                    }
                    break;

                case MSG_CONNECTION_TIMEOUT_EXCEPTION:
                    mListFooter.setVisibility(View.INVISIBLE);
                    showDialog(DIALOG_CONNECTION_TIMEOUT);
                    break;

                case MSG_STATUS_DESTROY:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TweetListActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TweetListActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(TweetListActivity.this, R.string.status_destroyed,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
                    }
                    break;

                case MSG_FAVORITE_CREATE:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TweetListActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TweetListActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(TweetListActivity.this, R.string.favorite_created,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
                    }
                    break;

                case MSG_FAVORITE_DESTROY:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TweetListActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TweetListActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(TweetListActivity.this, R.string.favorite_destroyed,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
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
                                MyLog.d(TAG, "", e);
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
    }

    /**
     * Load more items from the database into the list. This procedure doesn't
     * download any new tweets from the Internet
     */
    protected Runnable mLoadListItems = new Runnable() {
        public void run() {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "mLoadListItems run");
            }
            queryListData(TweetListActivity.this.getIntent(), true, true);
            mHandler.sendMessageDelayed(
                    mHandler.obtainMessage(MSG_LOAD_ITEMS, STATUS_LOAD_ITEMS_SUCCESS, 0), 400);
        }
    };
}

