/* 
 * Copyright (c) 2011-2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.andstatus.app.MyService.CommandData;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerified;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.PagedCursorAdapter;
import org.andstatus.app.data.TimelineSearchSuggestionProvider;
import org.andstatus.app.data.TweetBinder;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;
import org.json.JSONObject;

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

    public static final int DIALOG_SERVICE_UNAVAILABLE = 3;

    public static final int DIALOG_CONNECTION_TIMEOUT = 7;

    public static final int DIALOG_EXECUTING_COMMAND = 8;

    // Context menu items -----------------------------------------
    public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 2;

    public static final int CONTEXT_MENU_ITEM_FAVORITE = Menu.FIRST + 3;

    public static final int CONTEXT_MENU_ITEM_DIRECT_MESSAGE = Menu.FIRST + 4;

    public static final int CONTEXT_MENU_ITEM_UNFOLLOW = Menu.FIRST + 5;

    public static final int CONTEXT_MENU_ITEM_BLOCK = Menu.FIRST + 6;

    public static final int CONTEXT_MENU_ITEM_RETWEET = Menu.FIRST + 7;

    public static final int CONTEXT_MENU_ITEM_DESTROY_RETWEET = Menu.FIRST + 8;

    public static final int CONTEXT_MENU_ITEM_PROFILE = Menu.FIRST + 9;

    public static final int CONTEXT_MENU_ITEM_DESTROY_FAVORITE = Menu.FIRST + 10;

    public static final int CONTEXT_MENU_ITEM_DESTROY_STATUS = Menu.FIRST + 11;

    public static final int CONTEXT_MENU_ITEM_SHARE = Menu.FIRST + 12;
    
    // Intent bundle result keys
    public static final String INTENT_RESULT_KEY_AUTHENTICATION = "authentication";

    // Bundle identifier keys
    public static final String BUNDLE_KEY_CURRENT_PAGE = "currentPage";

    public static final String BUNDLE_KEY_IS_LOADING = "isLoading";

    // Request codes for called activities
    protected static final int REQUEST_SELECT_ACCOUNT = RESULT_FIRST_USER;
    
    /**
     * Key prefix for the stored list position
     */
    protected static final String LAST_POS_KEY = "last_position_";

    public static final int MILLISECONDS = 1000;

    /**
     * List footer, appears at the bottom of the list of messages 
     * when new items are being loaded into the list 
     */
    protected LinearLayout mListFooter;

    protected Cursor mCursor;

    protected NotificationManager mNM;

    protected ProgressDialog mProgressDialog;

    /**
     * Message handler for messages from threads and from the remote {@link MyService}.
     */
    private class MyHandler extends Handler {
        @Override
        public void handleMessage(android.os.Message msg) {
            MyLog.v(TAG, "handleMessage, what=" + msg.what + ", instance " + instanceId);
            JSONObject result = null;
            switch (msg.what) {
                case MSG_TWEETS_CHANGED:
                    int numTweets = msg.arg1;
                    if (numTweets > 0) {
                        mNM.cancelAll();
                    }
                    break;

                case MSG_DATA_LOADING:
                    boolean isLoadingNew = (msg.arg2 == 1) ? true : false;
                    if (!isLoadingNew) {
                        MyLog.v(TAG, "Timeline has been loaded " + (TimelineActivity.this.isLoading() ? " (loading) " : " (not loading) ") + ", visibility=" + mListFooter.getVisibility());
                        mListFooter.setVisibility(View.INVISIBLE);
                        if (isLoading()) {
                            Toast.makeText(TimelineActivity.this, R.string.timeline_reloaded,
                                    Toast.LENGTH_SHORT).show();
                            setIsLoading(false);
                        }
                    }
                    break;

                case MSG_UPDATE_STATUS:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TimelineActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TimelineActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        // The tweet was sent successfully
                        Toast.makeText(TimelineActivity.this, R.string.message_sent,
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
                            setIsLoading(false);
                            // setProgressBarIndeterminateVisibility(false);
                            break;
                        case STATUS_LOAD_ITEMS_FAILURE:
                            break;
                    }
                    break;

                case MSG_UPDATED_TITLE:
                    if (msg.arg1 > 0) {
                        updateTitle(msg.arg1 + "/" + msg.arg2);
                    }
                    break;

                case MSG_CONNECTION_TIMEOUT_EXCEPTION:
                    mListFooter.setVisibility(View.INVISIBLE);
                    showDialog(DIALOG_CONNECTION_TIMEOUT);
                    break;

                case MSG_STATUS_DESTROY:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TimelineActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TimelineActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(TimelineActivity.this, R.string.status_destroyed,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
                    }
                    break;

                case MSG_FAVORITE_CREATE:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TimelineActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TimelineActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(TimelineActivity.this, R.string.favorite_created,
                                Toast.LENGTH_SHORT).show();
                        mCurrentId = 0;
                    }
                    break;

                case MSG_FAVORITE_DESTROY:
                    result = (JSONObject) msg.obj;
                    if (result == null) {
                        Toast.makeText(TimelineActivity.this, R.string.error_connection_error,
                                Toast.LENGTH_LONG).show();
                    } else if (result.optString("error").length() > 0) {
                        Toast.makeText(TimelineActivity.this,
                                (CharSequence) result.optString("error"), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(TimelineActivity.this, R.string.favorite_destroyed,
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
                    Toast.makeText(TimelineActivity.this, R.string.error_connection_error,
                            Toast.LENGTH_SHORT).show();
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }
    /**
     * @return the mIsLoading
     */
    boolean isLoading() {
        MyLog.v(TAG, "isLoading checked " + mIsLoading + ", instance " + instanceId);
        return mIsLoading;
    }

    /**
     * @param isLoading Is loading now?
     */
    void setIsLoading(boolean isLoading) {
        MyLog.v(TAG, "isLoading set from " + mIsLoading + " to " + isLoading + ", instance " + instanceId );
        mIsLoading = isLoading;
    }

    protected MyHandler mHandler = new MyHandler();

    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    protected final static int PAGE_SIZE = 20;

    /**
     * Is saved position restored (or some default positions set)?
     */
    protected boolean positionRestored = false;

    /**
     * Number of items (Msg) in the list. It is used to find out when we need
     * to load more items.
     */
    protected int mTotalItemCount = 0;

    /**
     * Is connected to the application service?
     */
    protected boolean mIsBound;

    /**
     * See {@link #mServiceCallback} also
     */
    protected IMyService mService;

    /**
     * Items are being loaded into the list (asynchronously...)
     */
    protected boolean mIsLoading = false;
    
    /**
     * For testing purposes
     */
    protected long instanceId = 0;

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    protected boolean mIsFinishing = false;

    /**
     * TODO: enum TypelineType
     */
    protected TimelineTypeEnum mTimelineType = TimelineTypeEnum.UNKNOWN;

    /**
     * True if this timeline is filtered using query string ("Mentions" are not
     * counted here because they have separate TimelineType)
     */
    protected boolean mSearchMode = false;

    /**
     * The string is not empty if this timeline is filtered using query string
     * ("Mentions" are not counted here because they have separate TimelineType)
     */
    protected String mQueryString = "";

    /**
     * Time when shared preferences where changed
     */
    protected long preferencesChangeTime = 0;

    /**
     * Id of the Tweet that was selected (clicked, or whose context menu item
     * was selected) TODO: clicked, restore position...
     */
    protected long mCurrentId = 0;

    /** 
     * Controls of the TweetEditor
     */
    protected TweetEditor mTweetEditor;
 
    /** 
     * Table columns to use for the messages content
     */
    private static final String[] PROJECTION = new String[] {
            MyDatabase.Msg._ID, MyDatabase.User.AUTHOR_NAME, MyDatabase.Msg.BODY, User.IN_REPLY_TO_NAME,
            User.RECIPIENT_NAME,
            MyDatabase.MsgOfUser.FAVORITED, MyDatabase.Msg.CREATED_DATE
    };
    
    /**
     * This method is the first of the whole application to be called 
     * when the application starts for the very first time.
     * So we may put some Application initialization code here. 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (instanceId == 0) {
            instanceId = MyPreferences.nextInstanceId();
        } else {
            MyLog.d(TAG, "onCreate reuse the same instance " + instanceId);
        }

        MyPreferences.initialize(this, this);
        preferencesChangeTime = MyPreferences.getDefaultSharedPreferences().getLong(MyPreferences.KEY_PREFERENCES_CHANGE_TIME, 0);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            MyLog.d(TAG, "onCreate instance " + instanceId + " , preferencesChangeTime=" + preferencesChangeTime);
        }

        if (!mIsFinishing) {
            if (!MyPreferences.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, MODE_PRIVATE).getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
                Log.i(TAG, "We are running the Application for the very first time?");
                startActivity(new Intent(this, SplashActivity.class));
                finish();
            }
        }
        if (!mIsFinishing) {
            if (!MyAccount.getCurrentMyAccount().isPersistent()) {
                Log.i(TAG, "MyAccount '" + MyAccount.getCurrentMyAccount().getAccountGuid() + "' is temporal?!");
                startActivity(new Intent(this, SplashActivity.class));
                finish();
            }
        }
        
        MyPreferences.loadTheme(TAG, this);

        setTimelineType(getIntent());

        // Request window features before loading the content view
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

        setContentView(R.layout.tweetlist);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.timeline_title);

        mTweetEditor = new TweetEditor(this);
        // TODO: Maybe this should be a parameter
        mTweetEditor.hide();

        if (savedInstanceState != null) {
            mTweetEditor.loadState(savedInstanceState);
            if (savedInstanceState.containsKey(BUNDLE_KEY_IS_LOADING)) {
                setIsLoading(savedInstanceState.getBoolean(BUNDLE_KEY_IS_LOADING));
            }
            if (savedInstanceState.containsKey(MyService.EXTRA_TWEETID)) {
                mCurrentId = savedInstanceState.getLong(MyService.EXTRA_TWEETID);
            }
        }

        // Set up notification manager
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        // Create list footer to show the progress of message loading
        // We use "this" as a context, otherwise custom styles are not recognized...
        
        LayoutInflater inflater = LayoutInflater.from(this);

        mListFooter = (LinearLayout) inflater.inflate(R.layout.item_loading, null);;
        getListView().addFooterView(mListFooter);
        mListFooter.setVisibility(View.INVISIBLE);

        /* This also works (after we've added R.layout.item_loading to the view)
           but is not needed here:
        mListFooter = (LinearLayout) findViewById(R.id.item_loading);
        */
        
        getListView().setOnScrollListener(this);

        initUI();
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/search/search-dialog.html">Creating 
     * a Search Interface</a>
     */
    @Override
    public boolean onSearchRequested() {
        Bundle appDataBundle = new Bundle();
        appDataBundle.putParcelable("content_uri", MyProvider.getCurrentTimelineSearchUri(null));
        startSearch(null, false, appDataBundle, false);
        return true;
    }


    /**
     * TODO: Maybe this code should be moved to "onResume" ???
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onStart, instance " + instanceId);
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
        MyLog.v(TAG, "onResume, instance " + instanceId);
        if (!mIsFinishing) {
            if (MyAccount.getCurrentMyAccount().isPersistent()) {
                if (MyPreferences.getDefaultSharedPreferences().getLong(MyPreferences.KEY_PREFERENCES_CHANGE_TIME, 0) > preferencesChangeTime) {
                    MyLog.v(TAG, "Restarting this Activity to apply any new changes");
                    finish();
                    switchTimelineActivity(mTimelineType);
                }
            } else { 
                MyLog.v(TAG, "Finishing this Activity because the Account is temporal");
                finish();
            }
        }
        if (!mIsFinishing) {
            bindToService();
            updateTitle();
            restorePosition();

            if (MyAccount.getCurrentMyAccount().getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                if (!MyAccount.getCurrentMyAccount().getMyAccountPreferences().getBoolean("loadedOnce", false)) {
                    MyAccount.getCurrentMyAccount().getMyAccountPreferences().edit()
                            .putBoolean("loadedOnce", true).commit();
                    // One-time "manually" load tweets from the Internet for the
                    // new MyAccount
                    manualReload(true);
                }
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
    
    /**
     * Save Position per User and per TimeleneType Actually we save two Item
     * IDs: firstItemId - the first visible Tweet lastItemId - the last tweet we
     * should retrieve before restoring position
     */
    private void savePosition() {
        long firstItemId = 0;
        long lastItemId = 0;
        int firstScrollPos = getListView().getFirstVisiblePosition();
        int lastScrollPos = -1;
        android.widget.ListAdapter la = getListView().getAdapter();
        if (firstScrollPos >= la.getCount() - 1) {
            // Skip footer
            firstScrollPos = la.getCount() - 2;
        }
        if (firstScrollPos >= 0) {
            // for (int ind =0; ind < la.getCount(); ind++) {
            // Log.v(TAG, "itemId[" + ind + "]=" + la.getItemId(ind));
            // }
            firstItemId = la.getItemId(firstScrollPos);
            // We will load one more "page of tweets" below (older) current top
            // item
            lastScrollPos = firstScrollPos + PAGE_SIZE;
            if (lastScrollPos >= la.getCount() - 1) {
                // Skip footer
                lastScrollPos = la.getCount() - 2;
            }
            // Log.v(TAG, "lastScrollPos=" + lastScrollPos);
            if (lastScrollPos >= 0) {
                lastItemId = la.getItemId(lastScrollPos);
            } else {
                lastItemId = firstItemId;
            }
        }
        if (firstItemId > 0) {
            MyAccount ma = MyAccount.getCurrentMyAccount();
            
            // Variant 2 is overkill... but let's try...
            // I have a feeling that saving preferences while finishing activity sometimes doesn't work...
            //  - Maybe this was fixed introducing MyPreferences class?! 
            boolean saveSync = true;
            boolean saveAsync = false;
            if (saveSync) {
                // 1. Synchronous saving
                ma.getMyAccountPreferences().edit().putLong(positionKey(false), firstItemId)
                .putLong(positionKey(true), lastItemId).commit();
                if (mSearchMode) {
                    // Remember query string for which the position was saved
                    ma.getMyAccountPreferences().edit().putString(positionQueryStringKey(), mQueryString)
                            .commit();
                }
            }
            if (saveAsync) {
                // 2. Asynchronous saving of user's preferences
                sendCommand(new CommandData(ma.getAccountGuid(), positionKey(false), firstItemId));
                sendCommand(new CommandData(ma.getAccountGuid(), positionKey(true), lastItemId));
                if (mSearchMode) {
                    // Remember query string for which the position was saved
                    sendCommand(new CommandData(positionQueryStringKey(), mQueryString, ma.getUsername()));
                }
            } 
            
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Saved position " + ma.getAccountGuid() + "; " + positionKey(false) + "="
                        + firstItemId + "; index=" + firstScrollPos + "; lastId="
                        + lastItemId + "; index=" + lastScrollPos);
            }
        }
    }

    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    private void restorePosition() {
        MyAccount tu = MyAccount.getCurrentMyAccount();
        boolean loaded = false;
        long firstItemId = -3;
        try {
            int scrollPos = -1;
            firstItemId = getSavedPosition(false);
            if (firstItemId > 0) {
                scrollPos = listPosForId(firstItemId);
            }
            if (scrollPos >= 0) {
                getListView().setSelectionFromTop(scrollPos, 0);
                loaded = true;
                if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Restored position " + tu.getUsername() + "; " + positionKey(false) + "="
                            + firstItemId +"; list index=" + scrollPos);
                }
            } else {
                // There is no stored position
                if (mSearchMode) {
                    // In search mode start from the most recent tweet!
                    scrollPos = 0;
                } else {
                    scrollPos = getListView().getCount() - 2;
                }
                if (scrollPos >= 0) {
                    setSelectionAtBottom(scrollPos);
                }
            }
        } catch (Exception e) {
            Editor ed = tu.getMyAccountPreferences().edit();
            ed.remove(positionKey(false));
            ed.commit();
            firstItemId = -2;
        }
        if (!loaded && MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Didn't restore position " + tu.getUsername() + "; " + positionKey(false) + "="
                    + firstItemId);
        }
        positionRestored = true;
    }

    /**
     * @param lastRow Key for First visible row (false) or Last row that will be retrieved (true)
     * @return Saved Tweet id or < 0 if none was found.
     */
    protected long getSavedPosition(boolean lastRow) {
        MyAccount tu = MyAccount.getCurrentMyAccount();
        long savedItemId = -3;
        if (!mSearchMode
                || (mQueryString.compareTo(tu.getMyAccountPreferences().getString(
                        positionQueryStringKey(), "")) == 0)) {
            // Load saved position in Search mode only if that position was
            // saved for the same query string
            savedItemId = tu.getMyAccountPreferences().getLong(positionKey(lastRow), -1);
        }
        return savedItemId;
    }

    /**
     * Two rows are store for each position:
     * @param lastRow Key for First visible row (false) or Last row that will be retrieved (true)
     * @return Key to store position (tweet id of the first visible row)
     */
    private String positionKey(boolean lastRow) {
        return LAST_POS_KEY + mTimelineType.save() + (mSearchMode ? "_search" : "") + (lastRow ? "_last" : "");
    }

    /**
     * @return Key to store query string for this position
     */
    private String positionQueryStringKey() {
        return LAST_POS_KEY + mTimelineType.save() + "_querystring";
    }

    private void setSelectionAtBottom(int scrollPos) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "setSelectionAtBottom, 1");
        }
        int viewHeight = getListView().getHeight();
        int childHeight;
        childHeight = 30;
        int y = viewHeight - childHeight;
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "set position of last item to " + y + "px");
        }
        getListView().setSelectionFromTop(scrollPos, y);
    }

    /**
     * Returns the position of the item with the given ID.
     * 
     * @param searchedId the ID of the item whose position in the list is to be
     *            returned.
     * @return the position in the list or -1 if the item was not found
     */
    private int listPosForId(long searchedId) {
        int listPos;
        boolean itemFound = false;
        ListView lv = getListView();
        int itemCount = lv.getCount();
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "item count: " + itemCount);
        }
        for (listPos = 0; (!itemFound && (listPos < itemCount)); listPos++) {
            long itemId = lv.getItemIdAtPosition(listPos);
            if (itemId == searchedId) {
                itemFound = true;
                break;
            }
        }

        if (!itemFound) {
            listPos = -1;
        }
        return listPos;
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Content changed");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onPause, instance " + instanceId);
        }
        // The activity just lost its focus,
        // so we have to start notifying the User about new events after his
        // moment.

        if (!mIsFinishing) {
            // Get rid of the "fast scroll thumb"
            ((ListView) findViewById(android.R.id.list)).setFastScrollEnabled(false);
            clearNotifications();
            savePosition();
        }        
        positionRestored = false;
        disconnectService();
    }
   
    /**
     *  Cancel notifications of loading timeline
     *  They were set in 
     *  @see org.andstatus.app.MyService.CommandExecutor#notifyNewTweets(int, CommandEnum)
     */
    private void clearNotifications() {
        try {
            // TODO: Check if there are any notifications
            // and if none than don't waist time for this:

            mNM.cancel(MyService.CommandEnum.NOTIFY_TIMELINE.ordinal());
            mNM.cancel(MyService.CommandEnum.NOTIFY_MENTIONS.ordinal());
            mNM.cancel(MyService.CommandEnum.NOTIFY_DIRECT_MESSAGE.ordinal());

            // Reset notifications on AppWidget(s)
            Intent intent = new Intent(MyService.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(MyService.EXTRA_MSGTYPE, MyService.CommandEnum.NOTIFY_CLEAR.save());
            sendBroadcast(intent);
        } finally {
            // Nothing yet...
        }
    }

    @Override
    public void onDestroy() {
        MyLog.v(TAG,"onDestroy, instance " + instanceId);
        super.onDestroy();
        disconnectService();
    }

    @Override
    public void finish() {
        MyLog.v(TAG,"Finish requested" + (mIsFinishing ? ", already finishing" : "") + ", instance " + instanceId);
        if (!mIsFinishing) {
            mIsFinishing = true;
            if (mHandler == null) {
                Log.e(TAG,"Finishing. mHandler is already null, instance " + instanceId);
            }
            mHandler = null;
        }
        // TODO Auto-generated method stub
        super.finish();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_AUTHENTICATION_FAILED:
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.dialog_title_authentication_failed).setMessage(
                                R.string.dialog_summary_authentication_failed).setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                        startActivity(new Intent(TimelineActivity.this,
                                                PreferencesActivity.class));
                                    }
                                }).create();

            case DIALOG_SERVICE_UNAVAILABLE:
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.dialog_title_service_unavailable).setMessage(
                                R.string.dialog_summary_service_unavailable).setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                    }
                                }).create();

            case DIALOG_CONNECTION_TIMEOUT:
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.dialog_title_connection_timeout).setMessage(
                                R.string.dialog_summary_connection_timeout).setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
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
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, new ComponentName(this,
                TimelineActivity.class), null, intent, 0, null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.preferences_menu_id:
                startPreferencesActivity();
                break;

            case R.id.favorites_timeline_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.FAVORITES);
                break;

            case R.id.home_timeline_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.HOME);
                break;

            case R.id.direct_messages_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.DIRECT);
                break;

            case R.id.search_menu_id:
                onSearchRequested();
                break;

            case R.id.mentions_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.MENTIONS);
                break;
                
            case R.id.reload_menu_item:
                manualReload(false);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Listener that checks for clicks on the main list view.
     * 
     * @param adapterView
     * @param view
     * @param position
     * @param id
     */
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onItemClick, id=" + id);
        }
        if (id <= 0) {
            return;
        }
        Uri uri = MyProvider.getCurrentTimelineMsgUri(id);
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

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        mTotalItemCount = totalItemCount;

        if (positionRestored && !isLoading()) {
            // Idea from
            // http://stackoverflow.com/questions/1080811/android-endless-list
            boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                    && (firstVisibleItem + visibleItemCount >= totalItemCount);
            if (loadMore) {
                setIsLoading(true);
                MyLog.d(TAG, "Start Loading more items, total=" + totalItemCount);
                // setProgressBarIndeterminateVisibility(true);
                mListFooter.setVisibility(View.VISIBLE);
                Thread thread = new Thread(mLoadListItems);
                thread.start();
            }
        }
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        switch (scrollState) {
            case OnScrollListener.SCROLL_STATE_IDLE:
                break;
            case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                break;
            case OnScrollListener.SCROLL_STATE_FLING:
                // Turn the "fast scroll thumb" on
                view.setFastScrollEnabled(true);
                break;
        }
    }

    /**
     * Updates the activity title.
     * Sets the title with a left and right title.
     * 
     * @param rightText Right title part
     */
    public void updateTitle(String rightText) {
        String timelinename = "??";
        switch (mTimelineType) {
            case FAVORITES:
                timelinename = getString(R.string.activity_title_favorites);
                break;
            case HOME:
                timelinename = getString(R.string.activity_title_timeline);
                break;
            case MENTIONS:
                timelinename = getString(R.string.activity_title_mentions);
                break;
            case DIRECT:
                timelinename = getString(R.string.activity_title_direct_messages);
                break;
        }
        
        // Show current account info on the left button
        String username = MyAccount.getCurrentMyAccount().getUsername();
        String leftText = getString(R.string.activity_title_format, new Object[] {
                timelinename, username + (mSearchMode ? " *" : "")
        }); 
        Button leftTitle = (Button) findViewById(R.id.custom_title_left_text);
        leftTitle.setText(leftText);
        
        TextView rightTitle = (TextView) findViewById(R.id.custom_title_right_text);
        rightTitle.setText(rightText);

        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        if (mTimelineType != TimelineTypeEnum.DIRECT) {
            createMessageButton.setText(getString(R.string.button_create_tweet));
            createMessageButton.setVisibility(View.VISIBLE);
        } else {
            createMessageButton.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Updates the activity title.
     */
   public void updateTitle() {
        // First set less detailed title
        updateTitle("");
        // Then start asynchronous task that will set detailed info
        sendCommand(new CommandData(CommandEnum.RATE_LIMIT_STATUS, MyAccount
                .getCurrentMyAccount().getAccountGuid()));
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

        Button accountButton = (Button) findViewById(R.id.custom_title_left_text);
        accountButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(TimelineActivity.this, AccountSelector.class);
                startActivityForResult(i, REQUEST_SELECT_ACCOUNT);
            }
        });
       
        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        createMessageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mTweetEditor.isVisible()) {
                    mTweetEditor.hide();
                } else {
                    mTweetEditor.startEditingMessage(0, 0);
                }
            }
        });
    }

    /**
     * Check to see if the system has a hardware keyboard.
     * 
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
        if (!mIsBound) {
            mIsBound = true;
            Intent serviceIntent = new Intent(IMyService.class.getName());
            if (!MyServiceManager.isStarted()) {
                // Ensure that MyService is running
                MyServiceManager.startAndStatusService(this, new CommandData(CommandEnum.EMPTY, ""));
            }
            // startService(serviceIntent);
            bindService(serviceIntent, mServiceConnection, 0);
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
                mService = null;
            }
            unbindService(mServiceConnection);
            //MyServiceManager.stopAndStatusService(this);
            mIsBound = false;
        }
    }

    /**
     * Disconnects from the service and stops it.
     */
    protected void destroyService() {
        disconnectService();
        stopService(new Intent(IMyService.class.getName()));
        mService = null;
        mIsBound = false;
    }

    /**
     * Service connection handler.
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "onServiceConnected");
            }
            mService = IMyService.Stub.asInterface(service);
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                mService.registerCallback(mServiceCallback);
                // Push the queue
                sendCommand(null);
            } catch (RemoteException e) {
                // Service has already crashed, nothing much we can do
                // except hope that it will restart.
                mService = null;
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    /**
     * Intents queue to be send to the MyService
     */
    private BlockingQueue<CommandData> mCommands = new ArrayBlockingQueue<CommandData>(100, true);
    
    /**
     * Send broadcast with the command (in the form of Intent) to the AndStatus Service after it will be
     * connected to this activity. We should wait for the connection because
     * otherwise we won't receive callback from the service
     * 
     * Plus this method restarts this Activity if command is PUT_BOOLEAN_PREFERENCE with KEY_PREFERENCES_CHANGE_TIME 
     * 
     * @param commandData Intent to send, null if we only want to push the queue
     */
    protected synchronized void sendCommand(CommandData commandData) {
        if (commandData != null) {
            if (!mCommands.contains(commandData)) {
                if (!mCommands.offer(commandData)) {
                    Log.e(TAG, "mCommands is full?");
                }
            }
        }
        if (mService != null) {
            // Service is connected, so we can send queued Intents
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Sendings " + mCommands.size() + " broadcasts");
            }
            while (true) {
                MyService.CommandData element = mCommands.poll();
                if (element == null) { break; }
                sendBroadcast(element.toIntent());
            }
        }
    }
    
    /**
     * Service callback handler.
     */
    protected IMyServiceCallback mServiceCallback = new IMyServiceCallback.Stub() {
        /**
         * Msg changed callback method
         * 
         * @param value
         * @throws RemoteException
         */
        public void tweetsChanged(int value) throws RemoteException {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "tweetsChanged value=" + value);
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_TWEETS_CHANGED, value, 0));
        }

        /**
         * dataLoading callback method.
         * 
         * @param value
         * @throws RemoteException
         */
        public void dataLoading(int value) throws RemoteException {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "dataLoading value=" + value + ", instance " + instanceId);
            }
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

        public void rateLimitStatus(int remaining_hits, int hourly_limit) throws RemoteException {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATED_TITLE, remaining_hits, hourly_limit));
        }
    };

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onNewIntent, instance " + instanceId);
        }
        setTimelineType(intent);

        // All actions are actually search actions...
        // So get and process search query here
        queryListData(intent, false, false);
    }

    private void setTimelineType(Intent intentNew) {
        TimelineTypeEnum timelineType_new  = TimelineTypeEnum.load(intentNew.getStringExtra(MyService.EXTRA_TIMELINE_TYPE));
        if (timelineType_new != TimelineTypeEnum.UNKNOWN) {
            mTimelineType = timelineType_new;
        }

        mQueryString = intentNew.getStringExtra(SearchManager.QUERY);
        mSearchMode = (mQueryString != null && mQueryString.length() > 0);
        if (mSearchMode) {
            // Let's check if last time we saved position for the same query
            // string

        } else {
            mQueryString = "";
        }

        if (mTimelineType == TimelineTypeEnum.UNKNOWN) {
            mTimelineType = TimelineTypeEnum.HOME;
            // For some reason Android remembers last Query and adds it even if
            // the Activity was started from the Widget...
            Intent intent = getIntent();
            intent.removeExtra(SearchManager.QUERY);
            intent.removeExtra(SearchManager.APP_DATA);
            intent.putExtra(MyService.EXTRA_TIMELINE_TYPE, mTimelineType.save());
            intent.setData(MyProvider.getCurrentTimelineUri());
       }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "setTimelineType; type=\"" + mTimelineType.save() + "\"");
        }
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
            Log.v(TAG, "queryListData; queryString=\"" + queryString + "\"; TimelineType="
                    + mTimelineType.save());
        }

        Uri contentUri = MyProvider.getCurrentTimelineUri();

        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        // Id of the last (oldest) tweet to retrieve 
        long lastItemId = -1;
        
        if (queryString != null && queryString.length() > 0) {
            // Record the query string in the recent queries suggestions
            // provider
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    TimelineSearchSuggestionProvider.AUTHORITY,
                    TimelineSearchSuggestionProvider.MODE);
            suggestions.saveRecentQuery(queryString, null);

            contentUri = MyProvider.getCurrentTimelineSearchUri(queryString);
        }
        intent.putExtra(SearchManager.QUERY, queryString);

        if (!contentUri.equals(intent.getData())) {
            intent.setData(contentUri);
        }

        if (sa.nArgs == 0) {
            // In fact this is needed every time you want to load next page of
            // tweets.
            // So we have to duplicate here everything we set in
            // org.andstatus.app.TimelineActivity.onOptionsItemSelected()
            
            /* TODO: Other conditions... */
            sa.clear();
            
            switch (mTimelineType) {
                case HOME:
                    sa.addSelection(MyDatabase.MsgOfUser.SUBSCRIBED + " = ?", new String[] {
                            "1"
                        });
                    break;
                case MENTIONS:
                    sa.addSelection(MyDatabase.MsgOfUser.MENTIONED + " = ?", new String[] {
                            "1"
                        });
                    /* We already figured it out!
                    sa.addSelection(MyDatabase.Msg.BODY + " LIKE ?", new String[] {
                            "%@" + MyAccount.getTwitterUser().getUsername() + "%"
                        });
                    */
                    break;
                case FAVORITES:
                    sa.addSelection(MyDatabase.MsgOfUser.FAVORITED + " = ?", new String[] {
                            "1"
                        });
                    break;
                case DIRECT:
                    sa.addSelection(MyDatabase.MsgOfUser.DIRECTED + " = ?", new String[] {
                            "1"
                        });
                    break;
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
            sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.SENT_DATE + " >= ?", new String[] {
                String.valueOf(MyProvider.msgSentDate(lastItemId))
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
//            TimelineDownloader fl = new TimelineDownloader(TimelineActivity.this,
//                    TimelineActivity.TIMELINE_TYPE_HOME);
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
    
    /**
     * Only newer tweets (newer that last loaded) are being loaded from the
     * Internet, old ones are not being reloaded.
     */
    protected void manualReload(boolean allTimelineTypes) {

        // Show something to the user...
        setIsLoading(true);
        mListFooter.setVisibility(View.VISIBLE);
        //TimelineActivity.this.findViewById(R.id.item_loading).setVisibility(View.VISIBLE);

        // Ask service to load data for this mTimelineType
        MyService.CommandEnum command;
        switch (mTimelineType) {
            case DIRECT:
                command = CommandEnum.FETCH_DIRECT_MESSAGES;
                break;
            case MENTIONS:
                command = CommandEnum.FETCH_MENTIONS;
                break;
            default:
                command = CommandEnum.FETCH_HOME;
        }
        sendCommand(new CommandData(command, MyAccount.getCurrentMyAccount().getAccountGuid()));

        if (allTimelineTypes) {
            sendCommand(new CommandData(CommandEnum.FETCH_ALL_TIMELINES, MyAccount.getCurrentMyAccount().getAccountGuid()));
        }
    }
    
    protected void startPreferencesActivity() {
        // We need to restart this Activity after exiting PreferencesActivity
        // So let's set the flag:
        //MyPreferences.getDefaultSharedPreferences().edit()
        //        .putBoolean(PreferencesActivity.KEY_PREFERENCES_CHANGE_TIME, true).commit();
        startActivity(new Intent(this, PreferencesActivity.class));
    }

    /**
     * Switch type of presented timeline
     */
    protected void switchTimelineActivity(TimelineTypeEnum timelineType) {
        Intent intent;
        switch (timelineType) {
            default:
                timelineType = MyDatabase.TimelineTypeEnum.HOME;
                // Actually we use one Activity for all timelines...
            case MENTIONS:
            case FAVORITES:
            case HOME:
            case DIRECT:
                intent = new Intent(this, TimelineActivity.class);
                break;

        }

        intent.removeExtra(SearchManager.QUERY);
        intent.putExtra(MyService.EXTRA_TIMELINE_TYPE, timelineType.save());
        // We don't use the Action anywhere, so there is no need it setting it.
        // - we're analyzing query instead!
        // intent.setAction(Intent.ACTION_SEARCH);
        startActivity(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(BUNDLE_KEY_IS_LOADING, isLoading());

        mTweetEditor.saveState(outState);
        outState.putLong(MyService.EXTRA_TWEETID, mCurrentId);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    MyLog.v(TAG, "Restarting the activity for the selected account");
                    finish();
                    switchTimelineActivity(mTimelineType);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        
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
        menu.add(0, CONTEXT_MENU_ITEM_SHARE, m++, R.string.menu_item_share);
        menu.add(0, CONTEXT_MENU_ITEM_DIRECT_MESSAGE, m++, R.string.menu_item_direct_message);
        // menu.add(0, CONTEXT_MENU_ITEM_UNFOLLOW, m++,
        // R.string.menu_item_unfollow);
        // menu.add(0, CONTEXT_MENU_ITEM_BLOCK, m++, R.string.menu_item_block);
        // menu.add(0, CONTEXT_MENU_ITEM_PROFILE, m++,
        // R.string.menu_item_view_profile);

        // Get the record for the currently selected item
        Uri uri = MyProvider.getCurrentTimelineMsgUri(info.id);
        Cursor c = getContentResolver().query(uri, new String[] {
                MyDatabase.Msg._ID, MyDatabase.Msg.BODY, MyDatabase.Msg.SENDER_ID, 
                MyDatabase.Msg.AUTHOR_ID, MyDatabase.MsgOfUser.FAVORITED, 
                MyDatabase.MsgOfUser.RETWEETED
        }, null, null, null);
        try {
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                menu.setHeaderTitle(c.getString(c.getColumnIndex(MyDatabase.Msg.BODY)));
                if (c.getInt(c.getColumnIndex(MyDatabase.MsgOfUser.FAVORITED)) == 1) {
                    menu.add(0, CONTEXT_MENU_ITEM_DESTROY_FAVORITE, m++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    menu.add(0, CONTEXT_MENU_ITEM_FAVORITE, m++, R.string.menu_item_favorite);
                }
                if (c.getInt(c.getColumnIndex(MyDatabase.MsgOfUser.RETWEETED)) == 1) {
                    // TODO:
                    //menu.add(0, CONTEXT_MENU_ITEM_DESTROY_RETWEET, m++,
                    //        R.string.menu_item_destroy_retweet);
                } else {
                    menu.add(0, CONTEXT_MENU_ITEM_RETWEET, m++, R.string.menu_item_retweet);
                }
                if (MyAccount.getCurrentMyAccount().getUserId() == c.getLong(c.getColumnIndex(MyDatabase.Msg.SENDER_ID))
                        && MyAccount.getCurrentMyAccount().getUserId() == c.getLong(c.getColumnIndex(MyDatabase.Msg.AUTHOR_ID))
                        ) {
                    menu.add(0, CONTEXT_MENU_ITEM_DESTROY_STATUS, m++,
                            R.string.menu_item_destroy_status);
                }
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
        String userName;
        Cursor c;

        switch (item.getItemId()) {
            case CONTEXT_MENU_ITEM_REPLY:
                mTweetEditor.startEditingMessage(mCurrentId, 0);
                return true;

            case CONTEXT_MENU_ITEM_DIRECT_MESSAGE:
                long authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentId);
                if (authorId != 0) {
                    mTweetEditor.startEditingMessage(mCurrentId, authorId);
                    return true;
                }
                break;

            case CONTEXT_MENU_ITEM_RETWEET:
                sendCommand( new CommandData(CommandEnum.RETWEET, MyAccount.getCurrentMyAccount().getAccountGuid(), mCurrentId));
                return true;

            case CONTEXT_MENU_ITEM_DESTROY_STATUS:
                sendCommand( new CommandData(CommandEnum.DESTROY_STATUS, MyAccount.getCurrentMyAccount().getAccountGuid(), mCurrentId));
                return true;

            case CONTEXT_MENU_ITEM_FAVORITE:
                sendCommand( new CommandData(CommandEnum.CREATE_FAVORITE, MyAccount.getCurrentMyAccount().getAccountGuid(), mCurrentId));
                return true;

            case CONTEXT_MENU_ITEM_DESTROY_FAVORITE:
                sendCommand( new CommandData(CommandEnum.DESTROY_FAVORITE, MyAccount.getCurrentMyAccount().getAccountGuid(), mCurrentId));
                return true;

            case CONTEXT_MENU_ITEM_SHARE:
                userName = MyProvider.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, mCurrentId);
                uri = MyProvider.getCurrentTimelineMsgUri(info.id);
                c = getContentResolver().query(uri, new String[] {
                        MyDatabase.Msg.MSG_OID, MyDatabase.Msg.BODY
                }, null, null, null);
                try {
                    if (c != null && c.getCount() > 0) {
                        c.moveToFirst();
    
                        StringBuilder subject = new StringBuilder();
                        StringBuilder text = new StringBuilder();
                        String msgBody = c.getString(c.getColumnIndex(MyDatabase.Msg.BODY));
    
                        subject.append(getText(R.string.button_create_tweet));
                        subject.append(" - " + msgBody);
                        int maxlength = 80;
                        if (subject.length() > maxlength) {
                            subject.setLength(maxlength);
                            // Truncate at the last space
                            subject.setLength(subject.lastIndexOf(" "));
                            subject.append("...");
                        }
    
                        text.append(msgBody);
                        text.append("\n-- \n" + userName);
                        text.append("\n URL: " + "http://twitter.com/"
                                + userName 
                                + "/status/"
                                + c.getString(c.getColumnIndex(MyDatabase.Msg.MSG_OID)));
                        
                        Intent share = new Intent(android.content.Intent.ACTION_SEND); 
                        share.setType("text/plain"); 
                        share.putExtra(Intent.EXTRA_SUBJECT, subject.toString()); 
                        share.putExtra(Intent.EXTRA_TEXT, text.toString()); 
                        startActivity(Intent.createChooser(share, getText(R.string.menu_item_share)));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onContextItemSelected: " + e.toString());
                    return false;
                } finally {
                    if (c != null && !c.isClosed())
                        c.close();
                }
                return true;
                
            case CONTEXT_MENU_ITEM_UNFOLLOW:
            case CONTEXT_MENU_ITEM_BLOCK:
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
        PagedCursorAdapter tweetsAdapter = new PagedCursorAdapter(TimelineActivity.this,
                listItemId, mCursor, new String[] {
                MyDatabase.User.AUTHOR_NAME, MyDatabase.Msg.BODY, MyDatabase.Msg.CREATED_DATE, MyDatabase.MsgOfUser.FAVORITED
                }, new int[] {
                        R.id.tweet_screen_name, R.id.tweet_message, R.id.tweet_sent,
                        R.id.tweet_favorite
                }, getIntent().getData(), PROJECTION, MyDatabase.Msg.DEFAULT_SORT_ORDER);
        tweetsAdapter.setViewBinder(new TweetBinder());

        setListAdapter(tweetsAdapter);
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
            queryListData(TimelineActivity.this.getIntent(), true, true);
            mHandler.sendMessageDelayed(
                    mHandler.obtainMessage(MSG_LOAD_ITEMS, STATUS_LOAD_ITEMS_SUCCESS, 0), 400);
        }
    };
    
}
