/* 
 * Copyright (C) 2008 Torgny Bjers
 * Copyright (c) 2011 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.andstatus.app.MyService.CommandData;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.Tweets;
import org.andstatus.app.util.MyLog;

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

    public static final int DIALOG_EXTERNAL_STORAGE = 4;

    public static final int DIALOG_EXTERNAL_STORAGE_MISSING = 6;

    public static final int DIALOG_CONNECTION_TIMEOUT = 7;

    public static final int DIALOG_EXECUTING_COMMAND = 8;

    // Intent bundle result keys
    public static final String INTENT_RESULT_KEY_AUTHENTICATION = "authentication";

    // Bundle identifier keys
    public static final String BUNDLE_KEY_CURRENT_PAGE = "currentPage";

    public static final String BUNDLE_KEY_IS_LOADING = "isLoading";

    /**
     * Key prefix for the stored list position
     */
    protected static final String LAST_POS_KEY = "last_position";

    public static final int MILLISECONDS = 1000;

    /**
     * List footer for loading messages, appears at the bottom of the list of
     * tweets In fact, it is not visible but it is used to find out when User
     * wants to see items that were not loaded into the list...
     */
    protected LinearLayout mListFooter;

    protected Cursor mCursor;

    protected NotificationManager mNM;

    protected ProgressDialog mProgressDialog;

    protected Handler mHandler;

    /**
     * Tweets are being loaded into the list starting from one page. More Tweets
     * are being loaded in a case User scrolls down to the end of list.
     */
    protected final static int PAGE_SIZE = 20;

    /**
     * Is saved position restored (or some default positions set)?
     */
    protected boolean positionRestored = false;

    /**
     * Number of items (Tweets) in the list. It is used to find out when we need
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
    protected boolean mIsLoading;

    /**
     * We are going to finish/restart onResume this Activity
     */
    protected boolean mIsFinishingOnResume = false;
  
    /**
     * TODO: enum from
     * org.andstatus.app.data.MyDatabase.Tweets.TIMELINE_TYPE_...
     */
    protected int mTimelineType = Tweets.TIMELINE_TYPE_NONE;

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
     * This method is the first of the whole application to be called 
     * when the application starts for the very first time.
     * So we may put some Application initialization code here. 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MyPreferences.initialize(this, this);
        preferencesChangeTime = MyPreferences.getDefaultSharedPreferences().getLong(MyPreferences.KEY_PREFERENCES_CHANGE_TIME, 0);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            MyLog.d(TAG, "onCreate, preferencesChangeTime=" + preferencesChangeTime);
        }

        if (!MyPreferences.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, MODE_PRIVATE).getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
            Log.i(TAG, "We are running the Application for the very first time?");
            startActivity(new Intent(this, SplashActivity.class));
            finish();
        }
        if (TwitterUser.getTwitterUser().isTemporal()) {
            Log.i(TAG, "Account '" + TwitterUser.getTwitterUser().getUsername() + "' is temporal?!");
            startActivity(new Intent(this, SplashActivity.class));
            finish();
        }

        setTimelineType(getIntent());

        // Request window features before loading the content view
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

        loadTheme();
        setContentView(R.layout.tweetlist);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.timeline_title);

        mTweetEditor = new TweetEditor(this);
        // TODO: Maybe this should be a parameter
        mTweetEditor.hide();

        if (savedInstanceState != null) {
            mTweetEditor.loadState(savedInstanceState);
            if (savedInstanceState.containsKey(BUNDLE_KEY_IS_LOADING)) {
                mIsLoading = savedInstanceState.getBoolean(BUNDLE_KEY_IS_LOADING);
            }
            if (savedInstanceState.containsKey(MyService.EXTRA_TWEETID)) {
                mCurrentId = savedInstanceState.getLong(MyService.EXTRA_TWEETID);
            }
        }
        
        /*
         * if (mSP.getBoolean("storage_use_external", false)) { if
         * (!Environment.
         * getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
         * showDialog(DIALOG_EXTERNAL_STORAGE_MISSING); } if
         * (Environment.getExternalStorageState
         * ().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
         * Toast.makeText(this,
         * "External storage mounted read-only. Cannot write to database. Please re-mount your storage and try again."
         * , Toast.LENGTH_LONG).show(); destroyService(); finish(); } } if
         * (Environment
         * .getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) { if
         * (!mSP.getBoolean("confirmed_external_storage_use", false)) {
         * showDialog(DIALOG_EXTERNAL_STORAGE); } }
         */

        // Set up notification manager
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyLog.v(TAG, "onResume");
        if (TwitterUser.getTwitterUser().isTemporal()) {
            MyLog.v(TAG, "Finishing this Activity because user is temporal");
            mIsFinishingOnResume = true;
            finish();
        } else if (MyPreferences.getDefaultSharedPreferences().getLong(MyPreferences.KEY_PREFERENCES_CHANGE_TIME, 0) > preferencesChangeTime) {
            mIsFinishingOnResume = true;

            MyLog.v(TAG, "Restarting the activity to apply any new changes");
            finish();
            switchTimelineActivity(mTimelineType);
        }
        if (!mIsFinishingOnResume) {
            bindToService();
            updateTitle();
            restorePosition();
        }
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
            TwitterUser tu = TwitterUser.getTwitterUser();
            
            // Variant 2 is overkill... but let's try...
            // I have a feeling that saving preferences while finishing activity sometimes doesn't work...
            //  - Maybe this was fixed introducing MyPreferences class?! 
            boolean saveSync = true;
            boolean saveAsync = false;
            if (saveSync) {
                // 1. Synchronous saving
                tu.getSharedPreferences().edit().putLong(positionKey(false), firstItemId)
                .putLong(positionKey(true), lastItemId).commit();
                if (mSearchMode) {
                    // Remember query string for which the position was saved
                    tu.getSharedPreferences().edit().putString(positionQueryStringKey(), mQueryString)
                            .commit();
                }
            }
            if (saveAsync) {
                // 2. Asynchronous saving of user's preferences
                sendCommand(new CommandData(positionKey(false), firstItemId, tu.getUsername()));
                sendCommand(new CommandData(positionKey(true), lastItemId, tu.getUsername()));
                if (mSearchMode) {
                    // Remember query string for which the position was saved
                    sendCommand(new CommandData(positionQueryStringKey(), mQueryString, tu.getUsername()));
                }
            } 
            
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Saved position " + tu.getUsername() + "; " + positionKey(false) + "="
                        + firstItemId + "; index=" + firstScrollPos + "; lastId="
                        + lastItemId + "; index=" + lastScrollPos);
            }
        }
    }

    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    private void restorePosition() {
        TwitterUser tu = TwitterUser.getTwitterUser();
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
            Editor ed = tu.getSharedPreferences().edit();
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
        TwitterUser tu = TwitterUser.getTwitterUser();
        long savedItemId = -3;
        if (!mSearchMode
                || (mQueryString.compareTo(tu.getSharedPreferences().getString(
                        positionQueryStringKey(), "")) == 0)) {
            // Load saved position in Search mode only if that position was
            // saved for the same query string
            savedItemId = tu.getSharedPreferences().getLong(positionKey(lastRow), -1);
        }
        return savedItemId;
    }

    /**
     * Two rows are store for each position:
     * @param lastRow Key for First visible row (false) or Last row that will be retrieved (true)
     * @return Key to store position (tweet id of the first visible row)
     */
    private String positionKey(boolean lastRow) {
        return LAST_POS_KEY + mTimelineType + (mSearchMode ? "_search" : "") + (lastRow ? "_last" : "");
    }

    /**
     * @return Key to store query string for this position
     */
    private String positionQueryStringKey() {
        return LAST_POS_KEY + mTimelineType + "_querystring";
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
            Log.v(TAG, "onPause");
        }
        // The activity just lost its focus,
        // so we have to start notifying the User about new events after his
        // moment.

        if (!mIsFinishingOnResume) {
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
            mNM.cancel(MyService.CommandEnum.NOTIFY_REPLIES.ordinal());
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
        super.onDestroy();
        disconnectService();
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

            case DIALOG_EXTERNAL_STORAGE:
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_info)
                        .setTitle(R.string.dialog_title_external_storage).setMessage(
                                R.string.dialog_summary_external_storage).setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                        SharedPreferences.Editor editor = MyPreferences.getDefaultSharedPreferences().edit();
                                        editor.putBoolean("confirmed_external_storage_use", true);
                                        editor.putBoolean("storage_use_external", true);
                                        editor.commit();
                                        destroyService();
                                        finish();
                                        Intent intent = new Intent(TimelineActivity.this,
                                                TweetListActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    }
                                }).setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                        SharedPreferences.Editor editor = MyPreferences.getDefaultSharedPreferences().edit();
                                        editor.putBoolean("confirmed_external_storage_use", true);
                                        editor.commit();
                                    }
                                }).create();

            case DIALOG_EXTERNAL_STORAGE_MISSING:
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.dialog_title_external_storage_missing).setMessage(
                                R.string.dialog_summary_external_storage_missing)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                        SharedPreferences.Editor editor = MyPreferences.getDefaultSharedPreferences().edit();
                                        editor.putBoolean("confirmed_external_storage_use", true);
                                        editor.putBoolean("storage_use_external", false);
                                        editor.commit();
                                        destroyService();
                                        finish();
                                        Intent intent = new Intent(TimelineActivity.this,
                                                TweetListActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    }
                                }).setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                        destroyService();
                                        finish();
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
                TweetListActivity.class), null, intent, 0, null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.preferences_menu_id:
                startPreferencesActivity();
                break;

            case R.id.favorites_timeline_menu_id:
                switchTimelineActivity(Tweets.TIMELINE_TYPE_FAVORITES);
                break;

            case R.id.home_timeline_menu_id:
                switchTimelineActivity(Tweets.TIMELINE_TYPE_HOME);
                break;

            case R.id.direct_messages_menu_id:
                switchTimelineActivity(Tweets.TIMELINE_TYPE_MESSAGES);
                break;

            case R.id.search_menu_id:
                onSearchRequested();
                break;

            case R.id.mentions_menu_id:
                switchTimelineActivity(Tweets.TIMELINE_TYPE_MENTIONS);
                break;
                
            case R.id.reload_menu_item:
                manualReload();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
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
     * Load the theme for preferences.
     */
    public void loadTheme() {
        boolean light = MyPreferences.getDefaultSharedPreferences().getBoolean("appearance_light_theme", false);
        StringBuilder themeName = new StringBuilder();
        String name = MyPreferences.getDefaultSharedPreferences().getString("theme", "AndStatus");
        if (name.indexOf("Theme.") > -1) {
            name = name.substring(name.indexOf("Theme."));
        }
        themeName.append("Theme.");
        if (light) {
            themeName.append("Light.");
        }
        themeName.append(name);
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "loadTheme; theme=\"" + themeName.toString() + "\"");
        }
        setTheme((int) getResources().getIdentifier(themeName.toString(), "style",
                "org.andstatus.app"));
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
            case Tweets.TIMELINE_TYPE_FAVORITES:
                timelinename = getString(R.string.activity_title_favorites);
                break;
            case Tweets.TIMELINE_TYPE_HOME:
                timelinename = getString(R.string.activity_title_timeline);
                break;
            case Tweets.TIMELINE_TYPE_MENTIONS:
                timelinename = getString(R.string.activity_title_mentions);
                break;
            case Tweets.TIMELINE_TYPE_MESSAGES:
                timelinename = getString(R.string.activity_title_direct_messages);
                break;
        }
        String username = MyPreferences.getDefaultSharedPreferences().getString(MyPreferences.KEY_TWITTER_USERNAME, null);
        String leftText = getString(R.string.activity_title_format, new Object[] {
                timelinename, username + (mSearchMode ? " *" : "")
        }); 
        TextView leftTitle = (TextView) findViewById(R.id.custom_title_left_text);
        leftTitle.setText(leftText);
        TextView rightTitle = (TextView) findViewById(R.id.custom_title_right_text);
        rightTitle.setText(rightText);

        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        if (mTimelineType != Tweets.TIMELINE_TYPE_MESSAGES) {
            createMessageButton.setText(getString(R.string.button_create_tweet));
        } else {
            createMessageButton.setVisibility(View.GONE);
        }
    }

    public void updateTitle() {
        updateTitle("");
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

        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        createMessageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mTweetEditor.toggleVisibility();
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
                MyServiceManager.startAndStatusService(this, new CommandData(CommandEnum.EMPTY));
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
         * Tweets changed callback method
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
         * dataLoading callback method
         * 
         * @param value
         * @throws RemoteException
         */
        public void dataLoading(int value) throws RemoteException {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "dataLoading value=" + value);
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
            Log.v(TAG, "onNewIntent");
        }
        setTimelineType(intent);
    }

    private void setTimelineType(Intent intentNew) {
        int timelineType_new  = intentNew.getIntExtra(MyService.EXTRA_TIMELINE_TYPE,
                Tweets.TIMELINE_TYPE_NONE);
        if (timelineType_new != Tweets.TIMELINE_TYPE_NONE) {
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

        if (mTimelineType == Tweets.TIMELINE_TYPE_NONE) {
            mTimelineType = Tweets.TIMELINE_TYPE_HOME;
            // For some reason Android remembers last Query and adds it even if
            // the Activity was started from the Widget...
            Intent intent = getIntent();
            intent.removeExtra(SearchManager.QUERY);
            intent.removeExtra(SearchManager.APP_DATA);
            intent.putExtra(MyService.EXTRA_TIMELINE_TYPE, mTimelineType);
            intent.setData(MyDatabase.Tweets.CONTENT_URI);
       }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "setTimelineType; type=\"" + mTimelineType + "\"");
        }
    }

    protected void manualReload() {
        // Only newer tweets (newer that last loaded) are being loaded
        // from the Internet,
        // old tweets are not being reloaded.

        // Show something to the user...
        mListFooter.setVisibility(View.VISIBLE);
        
        // Ask service to load data for this mTimelineType
        MyService.CommandEnum command = CommandEnum.FETCH_TIMELINE;
        switch (mTimelineType) {
            case Tweets.TIMELINE_TYPE_MESSAGES:
                command = CommandEnum.FETCH_MESSAGES;
        }
        sendCommand( new CommandData(command));
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
    protected void switchTimelineActivity(int timelineType) {
        Intent intent;
        switch (timelineType) {
            case Tweets.TIMELINE_TYPE_MESSAGES:
                intent = new Intent(this, MessageListActivity.class);
                Bundle appDataBundle = new Bundle();
                appDataBundle.putParcelable("content_uri",
                        MyDatabase.DirectMessages.CONTENT_URI);
                intent.putExtra(SearchManager.APP_DATA, appDataBundle);
                break;
            default:
                timelineType = Tweets.TIMELINE_TYPE_HOME;
            case Tweets.TIMELINE_TYPE_MENTIONS:
            case Tweets.TIMELINE_TYPE_FAVORITES:
            case Tweets.TIMELINE_TYPE_HOME:
                intent = new Intent(this, TweetListActivity.class);
                break;

        }

        intent.removeExtra(SearchManager.QUERY);
        intent.putExtra(MyService.EXTRA_TIMELINE_TYPE, timelineType);
        // We don't use the Action anywhere, so there is no need it setting it.
        // - we're analyzing query instead!
        // intent.setAction(Intent.ACTION_SEARCH);
        startActivity(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mTweetEditor.saveState(outState);
        outState.putLong(MyService.EXTRA_TWEETID, mCurrentId);

        super.onSaveInstanceState(outState);
    }
}
