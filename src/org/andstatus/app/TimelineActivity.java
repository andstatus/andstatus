/* 
 * Copyright (c) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.text.TextUtils;
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
import android.widget.ToggleButton;

import org.andstatus.app.MyService.CommandData;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerified;
import org.andstatus.app.data.TimelineMsg;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.PagedCursorAdapter;
import org.andstatus.app.data.TimelineSearchSuggestionProvider;
import org.andstatus.app.data.TweetBinder;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;
import org.json.JSONObject;

import java.util.Locale;

/**
 * @author yvolk, torgny.bjers
 */
public class TimelineActivity extends ListActivity implements ITimelineActivity {

    private static final String TAG = TimelineActivity.class.getSimpleName();

    // Handler message codes
    /**
     * My tweet ("What's happening?"...) is being sending
     */
    private static final int MSG_UPDATE_STATUS = 3;

    private static final int MSG_AUTHENTICATION_ERROR = 5;

    private static final int MSG_SERVICE_UNAVAILABLE_ERROR = 8;

    private static final int MSG_CONNECTION_TIMEOUT_EXCEPTION = 11;

    private static final int MSG_STATUS_DESTROY = 12;

    private static final int MSG_FAVORITE_CREATE = 13;

    private static final int MSG_FAVORITE_DESTROY = 14;

    private static final int MSG_CONNECTION_EXCEPTION = 15;

    // Handler message status codes
    public static final int STATUS_LOAD_ITEMS_FAILURE = 0;

    public static final int STATUS_LOAD_ITEMS_SUCCESS = 1;

    // Dialog identifier codes
    public static final int DIALOG_AUTHENTICATION_FAILED = 1;

    public static final int DIALOG_SERVICE_UNAVAILABLE = 3;

    public static final int DIALOG_CONNECTION_TIMEOUT = 7;

    public static final int DIALOG_EXECUTING_COMMAND = 8;

    public static final int DIALOG_TIMELINE_TYPE = 9;
    
    // Context menu items -----------------------------------------
    public static final int CONTEXT_MENU_ITEM_REPLY = Menu.FIRST + 2;

    public static final int CONTEXT_MENU_ITEM_FAVORITE = Menu.FIRST + 3;

    public static final int CONTEXT_MENU_ITEM_DIRECT_MESSAGE = Menu.FIRST + 4;

    public static final int CONTEXT_MENU_ITEM_BLOCK = Menu.FIRST + 6;

    public static final int CONTEXT_MENU_ITEM_REBLOG = Menu.FIRST + 7;

    public static final int CONTEXT_MENU_ITEM_DESTROY_REBLOG = Menu.FIRST + 8;

    public static final int CONTEXT_MENU_ITEM_PROFILE = Menu.FIRST + 9;

    public static final int CONTEXT_MENU_ITEM_DESTROY_FAVORITE = Menu.FIRST + 10;

    public static final int CONTEXT_MENU_ITEM_DESTROY_STATUS = Menu.FIRST + 11;

    public static final int CONTEXT_MENU_ITEM_SHARE = Menu.FIRST + 12;

    public static final int CONTEXT_MENU_ITEM_SENDER_MESSAGES = Menu.FIRST + 13;

    public static final int CONTEXT_MENU_ITEM_AUTHOR_MESSAGES = Menu.FIRST + 14;

    public static final int CONTEXT_MENU_ITEM_FOLLOW_SENDER = Menu.FIRST + 15;

    public static final int CONTEXT_MENU_ITEM_STOP_FOLLOWING_SENDER = Menu.FIRST + 16;

    public static final int CONTEXT_MENU_ITEM_FOLLOW_AUTHOR = Menu.FIRST + 17;

    public static final int CONTEXT_MENU_ITEM_STOP_FOLLOWING_AUTHOR = Menu.FIRST + 18;
    
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
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    protected final static int PAGE_SIZE = 100;

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
     * Items are being loaded into the list (asynchronously...)
     */
    private boolean mIsLoading = false;
    
    /**
     * For testing purposes
     */
    private int instanceId = 0;
    protected MyHandler mHandler = new MyHandler();
    MyServiceConnector serviceConnector;

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    protected boolean mIsFinishing = false;

    /**
     * Timeline type
     */
    protected TimelineTypeEnum mTimelineType = TimelineTypeEnum.UNKNOWN;

    /**
     * Is the timeline combined? (Timeline shows messages from all accounts)
     */
    protected boolean mIsTimelineCombined = false;
    
    /**
     * UserId of the MyAccount, for which we show the activity
     */
    protected long mCurrentMyAccountUserId = 0;
    
    /**
     * Selected User for the {@link MyDatabase.TimelineTypeEnum#USER} timeline.
     * This is either User Id of current account OR user id of any other selected user.
     * So it's never == 0 for the {@link MyDatabase.TimelineTypeEnum#USER} timeline
     */
    protected long mSelectedUserId = 0;
    
    /**
     * True if this timeline is filtered using query string ("Mentions" are not
     * counted here because they have separate TimelineType)
     */
    protected boolean mIsSearchMode = false;

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
     * Id of the Message that was selected (clicked, or whose context menu item
     * was selected) TODO: clicked, restore position...
     */
    protected long mCurrentMsgId = 0;
    /**
     *  Corresponding account information ( "Reply As..." ... ) 
     *  oh whose behalf we are going to execute an action on this line in the list (message...) 
     */
    private long mMyAccountUserIdForCurrentMessage = 0;

    /** 
     * Controls of the TweetEditor
     */
    protected TweetEditor mTweetEditor;
 
    /** 
     * Table columns to use for the messages content
     */
    private static final String[] PROJECTION = new String[] {
            Msg._ID, User.AUTHOR_NAME, Msg.BODY, User.IN_REPLY_TO_NAME,
            User.RECIPIENT_NAME,
            MsgOfUser.FAVORITED, Msg.CREATED_DATE,
            User.LINKED_USER_ID
    };

    /**
     * Message handler for messages from threads and from the remote {@link MyService}.
     * TODO: Remove codes (of msg.what ) which are not used
     */
    private class MyHandler extends Handler {
        @Override
        public void handleMessage(android.os.Message msg) {
            MyLog.v(TAG, "handleMessage, what=" + msg.what + ", instanceId=" + instanceId);
            JSONObject result = null;
            switch (msg.what) {
                case MyServiceConnector.MSG_TWEETS_CHANGED:
                    int numTweets = msg.arg1;
                    if (numTweets > 0) {
                        mNM.cancelAll();
                    }
                    break;

                case MyServiceConnector.MSG_DATA_LOADING:
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

                case MyServiceConnector.MSG_UPDATED_TITLE:
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
                        mCurrentMsgId = 0;
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
                        mCurrentMsgId = 0;
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
                        mCurrentMsgId = 0;
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
        //MyLog.v(TAG, "isLoading checked " + mIsLoading + ", instance " + instanceId);
        return mIsLoading;
    }

    /**
     * @param isLoading Is loading now?
     */
    void setIsLoading(boolean isLoading) {
        MyLog.v(TAG, "isLoading set from " + mIsLoading + " to " + isLoading + ", instanceId=" + instanceId );
        mIsLoading = isLoading;
    }
    
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
            MyLog.d(TAG, "onCreate reuse the same instanceId=" + instanceId);
        }

        preferencesChangeTime = MyPreferences.initialize(this, this);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            MyLog.d(TAG, "onCreate instanceId=" + instanceId + " , preferencesChangeTime=" + preferencesChangeTime);
        }

        if (!mIsFinishing) {
            boolean helpAsFirstActivity = false;
            boolean showChangeLog = false;
            if (!MyPreferences.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, MODE_PRIVATE).getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
                Log.i(TAG, "We are running the Application for the very first time?");
                helpAsFirstActivity = true;
            } else if (MyAccount.getCurrentMyAccount() == null) {
                Log.i(TAG, "No current MyAccount");
                helpAsFirstActivity = true;
            } 
            
            // Show Change Log after update
            try {
                int versionCodeLast =  MyPreferences.getDefaultSharedPreferences().getInt(MyPreferences.KEY_VERSION_CODE_LAST, 0);
                PackageManager pm = getPackageManager();
                PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
                int versionCode =  pi.versionCode;
                if (versionCodeLast < versionCode) {
                    // Even if the User will see only the first page of the Help activity,
                    // count this as showing the Change Log
                    showChangeLog = true;
                    MyPreferences.getDefaultSharedPreferences().edit().putInt(MyPreferences.KEY_VERSION_CODE_LAST, versionCode).commit();
                }
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Unable to obtain package information", e);
            }

            if (helpAsFirstActivity || showChangeLog) {
                Intent intent = new Intent(this, HelpActivity.class);
                if (helpAsFirstActivity) {
                    intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
                } else if (showChangeLog) {
                    intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_ID, HelpActivity.HELP_PAGE_CHANGELOG);
                }
                startActivity(intent);
                if (helpAsFirstActivity) {
                    finish();
                }
            }
        }
        if (mIsFinishing) {
            return;
        }

        mCurrentMyAccountUserId = MyAccount.getCurrentMyAccountUserId();
        serviceConnector = new MyServiceConnector(instanceId);
        
        MyPreferences.loadTheme(TAG, this);

        // Request window features before loading the content view
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

        setContentView(R.layout.tweetlist);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.timeline_title);

        mTweetEditor = new TweetEditor(this);
        // TODO: Maybe this should be a parameter
        mTweetEditor.hide();

        boolean isInstanceStateRestored = false;
        if (savedInstanceState != null) {
            TimelineTypeEnum timelineType_new = TimelineTypeEnum.load(savedInstanceState
                    .getString(MyService.EXTRA_TIMELINE_TYPE));
            if (timelineType_new != TimelineTypeEnum.UNKNOWN) {
                isInstanceStateRestored = true;
                mTimelineType = timelineType_new;
                mTweetEditor.loadState(savedInstanceState);
                if (savedInstanceState.containsKey(BUNDLE_KEY_IS_LOADING)) {
                    setIsLoading(savedInstanceState.getBoolean(BUNDLE_KEY_IS_LOADING));
                }
                if (savedInstanceState.containsKey(MyService.EXTRA_ITEMID)) {
                    mCurrentMsgId = savedInstanceState.getLong(MyService.EXTRA_ITEMID);
                }
                if (savedInstanceState.containsKey(MyService.EXTRA_TIMELINE_IS_COMBINED)) {
                    mIsTimelineCombined = savedInstanceState.getBoolean(MyService.EXTRA_TIMELINE_IS_COMBINED);
                }
                if (savedInstanceState.containsKey(SearchManager.QUERY)) {
                    mQueryString = savedInstanceState.getString(SearchManager.QUERY);
                }
                if (savedInstanceState.containsKey(MyService.EXTRA_SELECTEDUSERID)) {
                    mSelectedUserId = savedInstanceState.getLong(MyService.EXTRA_SELECTEDUSERID);
                }
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
        
        // Attach listeners to the message list
        getListView().setOnScrollListener(this);
        getListView().setOnCreateContextMenuListener(this);
        getListView().setOnItemClickListener(this);

        Button accountButton = (Button) findViewById(R.id.selectAccountButton);
        accountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(TimelineActivity.this, AccountSelector.class);
                startActivityForResult(i, REQUEST_SELECT_ACCOUNT);
            }
        });
       
        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        createMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTweetEditor.isVisible()) {
                    mTweetEditor.hide();
                } else {
                    mTweetEditor.startEditingMessage("", 0, 0, MyAccount.getCurrentMyAccount().getAccountGuid(), mIsTimelineCombined);
                }
            }
        });

        if (!isInstanceStateRestored) {
            mIsTimelineCombined = MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, false);
            processNewIntent(getIntent());
        }
        updateThisOnChangedParameters();
    }

    /**
     * Switch combined timeline on/off
     * @param view combinedTimelineToggle
     */
    public void onCombinedTimelineToggle(View view) {
        boolean on = ((android.widget.ToggleButton) view).isChecked();
        MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, on).commit();
        switchTimelineActivity(mTimelineType, on, mCurrentMyAccountUserId);
    }
    
    public void onTimelineTypeButtonClick(View view) {
        showDialog(DIALOG_TIMELINE_TYPE);
    }
    
    /**
     * See <a href="http://developer.android.com/guide/topics/search/search-dialog.html">Creating 
     * a Search Interface</a>
     */
    @Override
    public boolean onSearchRequested() {
        Bundle appSearchData = new Bundle();
        appSearchData.putString(MyService.EXTRA_TIMELINE_TYPE, mTimelineType.save());
        appSearchData.putBoolean(MyService.EXTRA_TIMELINE_IS_COMBINED, mIsTimelineCombined);
        appSearchData.putLong(MyService.EXTRA_SELECTEDUSERID, mSelectedUserId);
        startSearch(null, false, appSearchData, false);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyLog.v(TAG, "onResume, instanceId=" + instanceId);
        if (!mIsFinishing) {
            if (MyAccount.getCurrentMyAccount() != null) {
                long preferencesChangeTimeNew = MyPreferences.initialize(this, this);
                if (preferencesChangeTimeNew != preferencesChangeTime) {
                    MyLog.v(TAG, "Restarting this Activity to apply any new changes of preferences");
                    finish();
                    switchTimelineActivity(mTimelineType, mIsTimelineCombined, mSelectedUserId);
                }
            } else { 
                MyLog.v(TAG, "Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        if (!mIsFinishing) {
            serviceConnector.bindToService(this, mHandler);
            updateTitle();
            if (!isLoading()) {
                restorePosition();
            }

            MyAccount ma = MyAccount.getMyAccount(mCurrentMyAccountUserId);
            if (ma.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                if (!ma.getMyAccountPreferences().getBoolean("loadedOnce", false)) {
                    ma.getMyAccountPreferences().edit()
                            .putBoolean("loadedOnce", true).commit();
                    // One-time "manually" load tweets from the Internet for the
                    // new MyAccount
                    manualReload(true);
                } 
            }
        }
    }

    /**
     * Save or forget current position per User and per TimeleneType. if save ==
     * true, the position is NOT saved (i.e. the stored position remains the
     * same) if there are no items in the list. Actually we save two Item IDs:
     * 1. firstItemId - the first visible Tweet. 2. lastItemId - the last tweet
     * we should retrieve before restoring position
     */
    private void saveOrForgetPosition(boolean save) {
        long firstItemId = 0;
        long lastItemId = 0;
        int firstScrollPos = 0;
        int lastScrollPos = -1;
        PositionStorage ps = new PositionStorage();

        if (save) {
            firstScrollPos = getListView().getFirstVisiblePosition();
            android.widget.ListAdapter la = getListView().getAdapter();
            if (la == null) {
                MyLog.v(TAG, "Position wasn't saved - no adapters yet");
                return;
            }
            if (firstScrollPos >= la.getCount() - 1) {
                // Skip footer
                firstScrollPos = la.getCount() - 2;
            }
            if (firstScrollPos >= 0) {
                // for (int ind =0; ind < la.getCount(); ind++) {
                // Log.v(TAG, "itemId[" + ind + "]=" + la.getItemId(ind));
                // }
                firstItemId = la.getItemId(firstScrollPos);
                // We will load one more "page of tweets" below (older) current
                // top
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

            if (firstItemId <= 0) {
                MyLog.v(TAG, "Position wasn't saved \"" + ps.accountGuid + "\"; " + ps.keyFirst);
                return;
            }
        }

        // Variant 2 is overkill... but let's try...
        // I have a feeling that saving preferences while finishing activity
        // sometimes doesn't work...
        // - Maybe this was fixed introducing MyPreferences class?!
        boolean saveSync = true;
        boolean saveAsync = false;
        if (saveSync) {
            // 1. Synchronous saving
            if (save) {
                ps.sp.edit().putLong(ps.keyFirst, firstItemId)
                        .putLong(ps.keyLast, lastItemId).commit();
                if (mIsSearchMode) {
                    // Remember query string for which the position was saved
                    ps.sp.edit().putString(ps.keyQueryString, mQueryString)
                            .commit();
                }
            } else {
                ps.sp.edit().remove(ps.keyFirst).remove(ps.keyLast)
                        .remove(ps.keyQueryString).commit();
            }
        }
        if (saveAsync) {
            // 2. Asynchronous saving of user's preferences
            // TODO: it's not used and should be tested when we will need it...
            serviceConnector.sendCommand(new CommandData(ps.accountGuid, ps.keyFirst,
                    firstItemId));
            serviceConnector
                    .sendCommand(new CommandData(ps.accountGuid, ps.keyLast, lastItemId));
            if (mIsSearchMode) {
                // Remember query string for which the position was saved
                serviceConnector.sendCommand(new CommandData(ps.keyQueryString,
                        mQueryString, ps.accountGuid));
            }
        }

        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            if (save) {
                Log.v(TAG, "Position saved    \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                        + firstItemId + "; index=" + firstScrollPos + "; lastId="
                        + lastItemId + "; index=" + lastScrollPos);
            } else {
                Log.v(TAG, "Position forgot   \"" + ps.accountGuid + "\"; " + ps.keyFirst);
            }
        }
    }
    
    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    private void restorePosition() {
        PositionStorage ps = new PositionStorage();
        boolean loaded = false;
        long firstItemId = -3;
        try {
            int scrollPos = -1;
            firstItemId = ps.getSavedPosition(false);
            if (firstItemId > 0) {
                scrollPos = listPosForId(firstItemId);
            }
            if (scrollPos >= 0) {
                getListView().setSelectionFromTop(scrollPos, 0);
                loaded = true;
                if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Position restored \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                            + firstItemId +"; index=" + scrollPos);
                }
            } else {
                // There is no stored position
                if (mIsSearchMode) {
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
            Log.v(TAG, "Position error    \"" + e.getLocalizedMessage());
            loaded = false;
        }
        if (!loaded) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Didn't restore position \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                        + firstItemId);
            }
            // So forget current position
            saveOrForgetPosition(false);
        }
        positionRestored = true;
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
            Log.v(TAG, "onPause, instanceId=" + instanceId);
        }
        // The activity just lost its focus,
        // so we have to start notifying the User about new events after his
        // moment.

        if (positionRestored) {
            // Get rid of the "fast scroll thumb"
            ((ListView) findViewById(android.R.id.list)).setFastScrollEnabled(false);
            clearNotifications();
            if (!isLoading()) {
                saveOrForgetPosition(true);
            }
        }        
        positionRestored = false;
        serviceConnector.disconnectService();
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

            mNM.cancel(MyService.CommandEnum.NOTIFY_HOME_TIMELINE.ordinal());
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
        MyLog.v(TAG,"onDestroy, instanceId=" + instanceId);
        super.onDestroy();
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
        if (serviceConnector != null) {
            serviceConnector.disconnectService();
        }
    }

    @Override
    public void finish() {
        MyLog.v(TAG,"Finish requested" + (mIsFinishing ? ", already finishing" : "") + ", instanceId=" + instanceId);
        if (!mIsFinishing) {
            mIsFinishing = true;
            if (mHandler == null) {
                Log.e(TAG,"Finishing. mHandler is already null, instanceId=" + instanceId);
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
                                    @Override
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                        startActivity(new Intent(TimelineActivity.this,
                                                MyPreferenceActivity.class));
                                    }
                                }).create();

            case DIALOG_SERVICE_UNAVAILABLE:
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.dialog_title_service_unavailable).setMessage(
                                R.string.dialog_summary_service_unavailable).setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                    }
                                }).create();

            case DIALOG_CONNECTION_TIMEOUT:
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.dialog_title_connection_timeout).setMessage(
                                R.string.dialog_summary_connection_timeout).setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                    }
                                }).create();

            case DIALOG_EXECUTING_COMMAND:
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setTitle(R.string.dialog_title_executing_command);
                mProgressDialog.setMessage(getText(R.string.dialog_summary_executing_command));
                return mProgressDialog;

            case DIALOG_TIMELINE_TYPE:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.dialog_title_select_timeline);
                String[] timelines = {
                        getString(MyDatabase.TimelineTypeEnum.HOME.resId()),
                        getString(MyDatabase.TimelineTypeEnum.FAVORITES.resId()),
                        getString(MyDatabase.TimelineTypeEnum.MENTIONS.resId()),
                        getString(MyDatabase.TimelineTypeEnum.DIRECT.resId()),
                        getString(MyDatabase.TimelineTypeEnum.USER.resId()),
                        getString(MyDatabase.TimelineTypeEnum.FOLLOWING_USER.resId())
                };
                builder.setItems(timelines, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position of the selected item
                        switch (which) {
                            case 0:
                                switchTimelineActivity(MyDatabase.TimelineTypeEnum.HOME, mIsTimelineCombined, mCurrentMyAccountUserId);
                                break;

                            case 1:
                                switchTimelineActivity(MyDatabase.TimelineTypeEnum.FAVORITES, mIsTimelineCombined, mCurrentMyAccountUserId);
                                break;

                            case 2:
                                switchTimelineActivity(MyDatabase.TimelineTypeEnum.MENTIONS, mIsTimelineCombined, mCurrentMyAccountUserId);
                                break;

                            case 3:
                                switchTimelineActivity(MyDatabase.TimelineTypeEnum.DIRECT, mIsTimelineCombined, mCurrentMyAccountUserId);
                                break;

                            case 4:
                                switchTimelineActivity(MyDatabase.TimelineTypeEnum.USER, mIsTimelineCombined, mCurrentMyAccountUserId);
                                break;

                            case 5:
                                switchTimelineActivity(MyDatabase.TimelineTypeEnum.FOLLOWING_USER, mIsTimelineCombined, mCurrentMyAccountUserId);
                                break;
                        }
                    }
                });
                return builder.create();                
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
                startMyPreferenceActivity();
                break;
/**
            case R.id.favorites_timeline_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.FAVORITES, mIsTimelineCombined);
                break;

            case R.id.home_timeline_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.HOME, mIsTimelineCombined);
                break;

            case R.id.direct_messages_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.DIRECT, mIsTimelineCombined);
                break;

            case R.id.mentions_menu_id:
                switchTimelineActivity(MyDatabase.TimelineTypeEnum.MENTIONS, mIsTimelineCombined);
                break;
**/
            case R.id.search_menu_id:
                onSearchRequested();
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
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        if (id <= 0) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "onItemClick, id=" + id);
            }
            return;
        }
        long linkedUserId = getLinkedUserIdFromCursor(position);
        MyAccount ma = MyAccount.getMyAccountLinkedToThisMessage(id, linkedUserId,
                mCurrentMyAccountUserId);
        if (ma == null) {
            Log.e(TAG, "Account for the message " + id + " was not found");
            return;
        }
        
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onItemClick, id=" + id + "; linkedUserId=" + linkedUserId + " account=" + ma.getAccountGuid());
        }
        Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), mTimelineType, true, id);
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

    /**
     * @param position Of current item in the {@link #mCursor}
     * @return id of the User linked to this message. This link reflects the User's timeline 
     * or an Account which was used to retrieved the message
     */
    private long getLinkedUserIdFromCursor(int position) {
        long userId = 0;
        try {
            mCursor.moveToPosition(position);
            int columnIndex = mCursor.getColumnIndex(User.LINKED_USER_ID);
            if (columnIndex > -1) {
                try {
                    userId = mCursor.getLong(columnIndex);
                } catch (Exception e) {}
            }
        } catch (Exception e) {}
        return (userId);
    }
    
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        mTotalItemCount = totalItemCount;

        if (positionRestored && !isLoading()) {
            // Idea from
            // http://stackoverflow.com/questions/1080811/android-endless-list
            boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                    && (firstVisibleItem + visibleItemCount >= totalItemCount);
            if (loadMore) {
                MyLog.d(TAG, "Start Loading more items, rows=" + totalItemCount);
                saveOrForgetPosition(true);
                // setProgressBarIndeterminateVisibility(true);
                mListFooter.setVisibility(View.VISIBLE);
                queryListData(true);
            }
        }
    }

    @Override
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
        String timelinename = getString(mTimelineType.resId());
        Button timelineTypeButton = (Button) findViewById(R.id.timelineTypeButton);
        timelineTypeButton.setText(timelinename + (mIsSearchMode ? " *" : ""));
        
        // Show current account info on the left button
        String accountName = "";
        if (MyAccount.moreThanOneOriginatingSystem()) {
            accountName = MyAccount.getCurrentMyAccount().getAccountGuid();
        } else {
            accountName = MyAccount.getCurrentMyAccount().getUsername();
        }
        Button selectAccountButton = (Button) findViewById(R.id.selectAccountButton);
        selectAccountButton.setText(accountName);
        
        TextView rightTitle = (TextView) findViewById(R.id.custom_title_right_text);
        rightTitle.setText(rightText);

        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        if (mTimelineType != TimelineTypeEnum.DIRECT) {
            createMessageButton.setText(getString(MyAccount.getCurrentMyAccount().alternativeTermResourceId(R.string.button_create_message)));
            createMessageButton.setVisibility(View.VISIBLE);
        } else {
            createMessageButton.setVisibility(View.GONE);
        }
    }

    /**
     * Updates the activity title.
     */
   public void updateTitle() {
        // First set less detailed title
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onNewIntent, instanceId=" + instanceId);
        }
        processNewIntent(intent);
        updateThisOnChangedParameters();
    }

    /**
     * Change the Activity according to the new intent. This procedure is done
     * both {@link #onCreate(Bundle)} and {@link #onNewIntent(Intent)}
     * 
     * @param intentNew
     */
    private void processNewIntent(Intent intentNew) {
        TimelineTypeEnum timelineType_new = TimelineTypeEnum.load(intentNew
                .getStringExtra(MyService.EXTRA_TIMELINE_TYPE));
        if (timelineType_new != TimelineTypeEnum.UNKNOWN) {
            mTimelineType = timelineType_new;
            mIsTimelineCombined = intentNew.getBooleanExtra(MyService.EXTRA_TIMELINE_IS_COMBINED, mIsTimelineCombined);
            mQueryString = intentNew.getStringExtra(SearchManager.QUERY);
            mSelectedUserId = intentNew.getLongExtra(MyService.EXTRA_SELECTEDUSERID, mSelectedUserId);
        } else {
            Bundle appSearchData = intentNew.getBundleExtra(SearchManager.APP_DATA);
            if (appSearchData != null) {
                // We use other packaging of the same parameters in onSearchRequested
                timelineType_new = TimelineTypeEnum.load(appSearchData
                        .getString(MyService.EXTRA_TIMELINE_TYPE));
                if (timelineType_new != TimelineTypeEnum.UNKNOWN) {
                    mTimelineType = timelineType_new;
                    mIsTimelineCombined = appSearchData.getBoolean(MyService.EXTRA_TIMELINE_IS_COMBINED, mIsTimelineCombined);
                    /* The query itself is still from the Intent */
                    mQueryString = intentNew.getStringExtra(SearchManager.QUERY);
                    mSelectedUserId = appSearchData.getLong(MyService.EXTRA_SELECTEDUSERID, mSelectedUserId);
                }
            }
        }
        if (mTimelineType == TimelineTypeEnum.UNKNOWN) {
            /* Set default values */
            mTimelineType = TimelineTypeEnum.HOME;
            mIsTimelineCombined = (MyAccount.countOfAuthenticatedUsers() > 1);
            mQueryString = "";
            mSelectedUserId = 0;
        }
        mCurrentMyAccountUserId = MyAccount.getCurrentMyAccountUserId();
        if (mSelectedUserId == 0 && mTimelineType == TimelineTypeEnum.USER) {
            mSelectedUserId = mCurrentMyAccountUserId;
        }

        // Are we supposed to send a tweet?
        if (Intent.ACTION_SEND.equals(intentNew.getAction())) {
            String textInitial = "";
            // This is Title of the page is Sharing Web page
            String subject = intentNew.getStringExtra(Intent.EXTRA_SUBJECT);
            // This is URL of the page if sharing web page
            String text = intentNew.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(subject)) {
                textInitial += subject;
            }
            if (!TextUtils.isEmpty(text)) {
                if (!TextUtils.isEmpty(textInitial)) {
                    textInitial += " ";
                }
                textInitial += text;
            }
            MyLog.v(TAG, "Intent.ACTION_SEND '" + textInitial +"'");
            mTweetEditor.startEditingMessage(textInitial, 0, 0, MyAccount.getCurrentMyAccount().getAccountGuid(), mIsTimelineCombined);
        }

        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "processNewIntent; type=\"" + mTimelineType.save() + "\"");
        }
    }

    private void updateThisOnChangedParameters() {
        mIsSearchMode = !TextUtils.isEmpty(mQueryString);
        if (mIsSearchMode) {
            // Let's check if last time we saved position for the same query
            // string

        } else {
            mQueryString = "";
        }

        TextView selecteUserText = (TextView) findViewById(R.id.selected_user_title_text);
        ToggleButton combinedTimelineToggle = (ToggleButton) findViewById(R.id.combinedTimelineToggle);
        combinedTimelineToggle.setChecked(mIsTimelineCombined);
        if (mSelectedUserId != 0 && mSelectedUserId != mCurrentMyAccountUserId) {
            combinedTimelineToggle.setVisibility(View.GONE);
            selecteUserText.setText(MyProvider.userIdToName(mSelectedUserId));
            selecteUserText.setVisibility(View.VISIBLE);
        } else {
            selecteUserText.setVisibility(View.GONE);
            // Show the "Combined" toggle only if we have more than one account
            if (MyAccount.countOfAuthenticatedUsers() > 1) {
                combinedTimelineToggle.setVisibility(View.VISIBLE);
            } else {
                combinedTimelineToggle.setVisibility(View.GONE);
                mIsTimelineCombined = false;
            }
        }

        queryListData(false);

        switch (mTimelineType) {
            case USER:
            case FOLLOWING_USER:
                // This timeline doesn't update automatically so let's do it now if necessary
                TimelineMsg lmi = new TimelineMsg(mTimelineType, mSelectedUserId);
                if (lmi.isTimeToAutoUpdate()) {
                    manualReload(false);
                }
        }
        
        if (mTweetEditor.isStateLoaded()) {
            mTweetEditor.continueEditingLoadedState();
        } else if (mTweetEditor.isVisible()) {
            // This is done to request focus (if we need this...)
            mTweetEditor.show();
        }
    }
    
    /**
     * This is to prevent parallel requests to query data
     */
    private boolean queryListDataInProgress = false;
    /**
     * Clean after successful or failed operation
     */
    private void queryListDataEnded(boolean doRestorePosition) {
        if (!mIsFinishing) {
            mListFooter.setVisibility(View.INVISIBLE);
            // setProgressBarIndeterminateVisibility(false);
            if (doRestorePosition) {
                restorePosition();
            }
            // Do this after restoring position to avoid repeated loading from onScroll event
            setIsLoading(false);
        }
        queryListDataInProgress = false;
    }
    /**
     * Prepare query to the ContentProvider (to the database) and load the visible List of
     * messages with this data
     * This is done asynchronously.
     * This method should be called from UI thread only.
     * 
     * @param otherThread This method is being accessed from other thread
     * @param loadOneMorePage true - load one more page of messages, false - reload the same page
     */
    protected void queryListData(boolean loadOneMorePage_in) {
        final boolean loadOneMorePage = loadOneMorePage_in;

        /**
         * Here we do all the work 
         * @author yvolk
         */
        class AsyncQueryListData extends AsyncTask<Void, Void, Void>{
            long startTime = System.nanoTime();
            Uri contentUri = MyProvider.getTimelineUri(mCurrentMyAccountUserId, mTimelineType,
                    mIsTimelineCombined);

            SelectionAndArgs sa = new SelectionAndArgs();
            String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
            
            Cursor cursor;

            @Override
            protected void onPreExecute() {
                Intent intent = getIntent();

                if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "queryListData; queryString=\"" + mQueryString + "\"; TimelineType="
                            + mTimelineType.save()
                            + "; isCombined=" + (mIsTimelineCombined ? "yes" : "no"));
                }

                // Id of the last (oldest) tweet to retrieve
                long lastItemId = -1;

                if (!TextUtils.isEmpty(mQueryString)) {
                    // Record the query string in the recent queries
                    // of the Suggestion Provider
                    SearchRecentSuggestions suggestions = new SearchRecentSuggestions(TimelineActivity.this,
                            TimelineSearchSuggestionProvider.AUTHORITY,
                            TimelineSearchSuggestionProvider.MODE);
                    suggestions.saveRecentQuery(mQueryString, null);

                    contentUri = MyProvider.getTimelineSearchUri(mCurrentMyAccountUserId, mTimelineType,
                            mIsTimelineCombined, mQueryString);
                }

                if (!contentUri.equals(intent.getData())) {
                    intent.setData(contentUri);
                }

                if (sa.nArgs == 0) {
                    // In fact this is needed every time you want to load
                    // next page of messages

                    /* TODO: Other conditions... */
                    sa.clear();

                    // TODO: Move this selections to the {@link MyProvider} ?!
                    switch (mTimelineType) {
                        case HOME:
                            // In the Home of the combined timeline we see ALL loaded
                            // messages, even those that we downloaded
                            // not as Home timeline of any Account
                            if (!mIsTimelineCombined) {
                                sa.addSelection(MyDatabase.MsgOfUser.SUBSCRIBED + " = ?", new String[] {
                                        "1"
                                });
                            }
                            break;
                        case MENTIONS:
                            sa.addSelection(MyDatabase.MsgOfUser.MENTIONED + " = ?", new String[] {
                                    "1"
                            });
                            /*
                             * We already figured this out and set {@link MyDatabase.MsgOfUser.MENTIONED}:
                             * sa.addSelection(MyDatabase.Msg.BODY + " LIKE ?" ...
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
                        case USER:
                            // Reblogs are included also
                            sa.addSelection(MyDatabase.Msg.AUTHOR_ID + " = ? OR "
                                    + MyDatabase.Msg.SENDER_ID + " = ? OR "
                                    + "("
                                    + User.LINKED_USER_ID + " = ? AND "
                                    + MyDatabase.MsgOfUser.REBLOGGED + " = 1"
                                    + ")",
                                    new String[] {
                                            Long.toString(mSelectedUserId), Long.toString(mSelectedUserId),
                                            Long.toString(mSelectedUserId)
                                    });
                            break;
                    }
                }

                if (!positionRestored) {
                    // We have to ensure that saved position will be
                    // loaded from database into the list
                    lastItemId = new PositionStorage().getSavedPosition(true);
                }

                int nMessages = 0;
                if (mCursor != null && !mCursor.isClosed()) {
                    if (positionRestored) {
                        // If position is NOT loaded - this cursor is from other
                        // timeline/search
                        // and we shouldn't care how much rows are there.
                        nMessages = mCursor.getCount();
                    }
                }

                if (lastItemId > 0) {
                    sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.SENT_DATE + " >= ?",
                            new String[] {
                                String.valueOf(MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.SENT_DATE, lastItemId))
                            });
                } else {
                    if (loadOneMorePage) {
                        nMessages += PAGE_SIZE;
                    } else if (nMessages < PAGE_SIZE) {
                        nMessages = PAGE_SIZE;
                    }
                    sortOrder += " LIMIT 0," + nMessages;
                }

                // This is for testing pruneOldRecords
                // try {
                // TimelineDownloader fl = new TimelineDownloader(TimelineActivity.this,
                // TimelineActivity.TIMELINE_TYPE_HOME);
                // fl.pruneOldRecords();
                //
                // } catch (Exception e) {
                // e.printStackTrace();
                // }
            }

            @Override
            protected Void doInBackground(Void... params) {
                cursor = getContentResolver().query(contentUri, PROJECTION, sa.selection,
                        sa.selectionArgs, sortOrder);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                boolean doRestorePosition = true;
                if (!mIsFinishing) {
                    boolean cursorSet = false;
                    if (positionRestored && (getListAdapter() != null)) {
                        if (loadOneMorePage) {
                            // This will prevent continuous loading...
                            if (cursor.getCount() > getListAdapter().getCount()) {
                                MyLog.v(TAG, "On changing Cursor");
                                ((SimpleCursorAdapter) getListAdapter()).changeCursor(cursor);
                                mCursor = cursor;
                            } else {
                                doRestorePosition = false;
                                // We don't need this cursor: assuming it is the same as existing
                                cursor.close();
                            }
                            cursorSet = true; 
                        }
                    }
                    if (!cursorSet) {
                        if (mCursor != null && !mCursor.isClosed()) {
                            mCursor.close();
                        }
                        mCursor = cursor;
                        createAdapters();
                    }
                }
                
                if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                    String cursorInfo = "cursor - ??";
                    if (mCursor == null) {
                        cursorInfo = "cursor is null";
                    } else if (mCursor.isClosed()) {
                        cursorInfo = "cursor is Closed";
                    } else {
                        cursorInfo = mCursor.getCount() + " rows";
                    }
                    Log.v(TAG, "queryListData; ended, " + cursorInfo + ", " + Double.valueOf((System.nanoTime() - startTime)/1.0E6).longValue() + " ms");
                }
                
                queryListDataEnded(doRestorePosition);
            }
        }
        
        if (queryListDataInProgress) {
            MyLog.v(TAG, "queryListData is already in progress, skipping this request");
            return;
        }

        try {
            queryListDataInProgress = true;
            setIsLoading(true);
            new AsyncQueryListData().execute();
        } catch (Exception e) {
            Log.e(TAG, "Error during AsyncQueryListData" + e.getLocalizedMessage());
            queryListDataEnded(!loadOneMorePage);
        }
    }
    
    /**
     * Ask a service to load data from the Internet for the selected TimelineType
     * Only newer messages (newer than last loaded) are being loaded from the
     * Internet, older ones are not being reloaded.
     */
    protected void manualReload(boolean allTimelineTypes) {

        // Show something to the user...
        setIsLoading(true);
        mListFooter.setVisibility(View.VISIBLE);
        //TimelineActivity.this.findViewById(R.id.item_loading).setVisibility(View.VISIBLE);

        MyDatabase.TimelineTypeEnum timelineType = TimelineTypeEnum.HOME;
        long userId = 0;
        switch (mTimelineType) {
            case DIRECT:
            case MENTIONS:
                timelineType = mTimelineType;
                break;
            case USER:
            case FOLLOWING_USER:
                timelineType = mTimelineType;
                userId = mSelectedUserId;
                break;
        }

        String accountGuid = MyAccount.getMyAccount(mCurrentMyAccountUserId).getAccountGuid();
        MyService.CommandData cd = new CommandData(CommandEnum.FETCH_TIMELINE,
                    mIsTimelineCombined ? "" : accountGuid, timelineType, userId);
        serviceConnector.sendCommand(cd);

        if (allTimelineTypes) {
            serviceConnector.sendCommand(new CommandData(CommandEnum.FETCH_TIMELINE, accountGuid, TimelineTypeEnum.ALL, 0));
        }
    }
    
    protected void startMyPreferenceActivity() {
        // We need to restart this Activity after exiting MyPreferenceActivity
        // So let's set the flag:
        //MyPreferences.getDefaultSharedPreferences().edit()
        //        .putBoolean(MyPreferenceActivity.KEY_PREFERENCES_CHANGE_TIME, true).commit();
        startActivity(new Intent(this, MyPreferenceActivity.class));
    }

    /**
     * Switch type of presented timeline
     */
    protected void switchTimelineActivity(TimelineTypeEnum timelineType, boolean isTimelineCombined, long selectedUserId) {
        Intent intent;
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "switchTimelineActivity; type=\"" + timelineType.save() + "\"; isCombined=" + (isTimelineCombined ? "yes" : "no"));
        }
        switch (timelineType) {
            default:
                timelineType = MyDatabase.TimelineTypeEnum.HOME;
                // Actually we use one Activity for all timelines...
            case MENTIONS:
            case FAVORITES:
            case HOME:
            case DIRECT:
            case USER:
            case FOLLOWING_USER:
                intent = new Intent(this, TimelineActivity.class);
                break;

        }

        intent.removeExtra(SearchManager.QUERY);
        intent.putExtra(MyService.EXTRA_TIMELINE_TYPE, timelineType.save());
        intent.putExtra(MyService.EXTRA_TIMELINE_IS_COMBINED, isTimelineCombined);
        intent.putExtra(MyService.EXTRA_SELECTEDUSERID, selectedUserId);
        // We don't use the Action anywhere, so there is no need it setting it.
        // - we're analyzing query instead!
        // intent.setAction(Intent.ACTION_SEARCH);
        startActivity(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(BUNDLE_KEY_IS_LOADING, isLoading());

        mTweetEditor.saveState(outState);
        outState.putString(MyService.EXTRA_TIMELINE_TYPE, mTimelineType.save());
        outState.putLong(MyService.EXTRA_ITEMID, mCurrentMsgId);
        outState.putBoolean(MyService.EXTRA_TIMELINE_IS_COMBINED, mIsTimelineCombined);
        outState.putString(SearchManager.QUERY, mQueryString);
        outState.putLong(MyService.EXTRA_SELECTEDUSERID, mSelectedUserId);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    MyLog.v(TAG, "Restarting the activity for the selected account");
                    finish();
                    TimelineTypeEnum timelineTypeNew = mTimelineType;
                    if (mTimelineType == TimelineTypeEnum.USER && mSelectedUserId != mCurrentMyAccountUserId) {
                        /*  "Other User's timeline" vs "My User's timeline" 
                         * Actually we saw messages of other user, not of (previous) MyAccount,
                         * so let's switch to the HOME
                         * TODO: Open "Other User timeline" in a separate Activity
                         */
                        timelineTypeNew = TimelineTypeEnum.HOME;
                    }
                    switchTimelineActivity(timelineTypeNew, mIsTimelineCombined, MyAccount.getCurrentMyAccountUserId());
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

        int menuItemId = 0;
        mCurrentMsgId = info.id;
        mMyAccountUserIdForCurrentMessage = 0;
        long linkedUserId = getLinkedUserIdFromCursor(info.position);
        MessageDataForContextMenu md = new MessageDataForContextMenu(this, mCurrentMyAccountUserId, mTimelineType, mCurrentMsgId,
                linkedUserId);
        if (md.ma == null) {
            return;
        }
        if (md.canUseCurrentAccountInsteadOfLinked) {
            // Yes, use current Account!
            md = new MessageDataForContextMenu(this, mCurrentMyAccountUserId, mTimelineType, mCurrentMsgId, 0);
        }
        mMyAccountUserIdForCurrentMessage = md.ma.getUserId();

        // Create the Context menu
        try {
            menu.setHeaderTitle((mIsTimelineCombined ? md.ma.getAccountGuid() + ": " : "")
                    + md.body);

            // Add menu items
            if (!md.isDirect) {
                menu.add(0, CONTEXT_MENU_ITEM_REPLY, menuItemId++, R.string.menu_item_reply);
            }
            menu.add(0, CONTEXT_MENU_ITEM_SHARE, menuItemId++, R.string.menu_item_share);

            // TODO: Only if he follows me?
            menu.add(0, CONTEXT_MENU_ITEM_DIRECT_MESSAGE, menuItemId++,
                    R.string.menu_item_direct_message);

            // menu.add(0, CONTEXT_MENU_ITEM_UNFOLLOW, m++,
            // R.string.menu_item_unfollow);
            // menu.add(0, CONTEXT_MENU_ITEM_BLOCK, m++,
            // R.string.menu_item_block);
            // menu.add(0, CONTEXT_MENU_ITEM_PROFILE, m++,
            // R.string.menu_item_view_profile);

            if (!md.isDirect) {
                if (md.favorited) {
                    menu.add(0, CONTEXT_MENU_ITEM_DESTROY_FAVORITE, menuItemId++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    menu.add(0, CONTEXT_MENU_ITEM_FAVORITE, menuItemId++,
                            R.string.menu_item_favorite);
                }
                if (md.reblogged) {
                    menu.add(0, CONTEXT_MENU_ITEM_DESTROY_REBLOG, menuItemId++,
                            md.ma.alternativeTermResourceId(R.string.menu_item_destroy_reblog));
                } else {
                    // Don't allow a User to reblog himself
                    if (mMyAccountUserIdForCurrentMessage != md.senderId) {
                        menu.add(0, CONTEXT_MENU_ITEM_REBLOG, menuItemId++,
                                md.ma.alternativeTermResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (mSelectedUserId != md.senderId) {
                /*
                 * Messages by the Sender of this message ("User timeline" of
                 * that user)
                 */
                menu.add(0, CONTEXT_MENU_ITEM_SENDER_MESSAGES, menuItemId++,
                        String.format(Locale.getDefault(),
                                getText(R.string.menu_item_user_messages).toString(),
                                MyProvider.userIdToName(md.senderId)));
            }

            if (mSelectedUserId != md.authorId && md.senderId != md.authorId) {
                /*
                 * Messages by the Author of this message ("User timeline" of
                 * that user)
                 */
                menu.add(0, CONTEXT_MENU_ITEM_AUTHOR_MESSAGES, menuItemId++,
                        String.format(Locale.getDefault(),
                                getText(R.string.menu_item_user_messages).toString(),
                                MyProvider.userIdToName(md.authorId)));
            }

            if (md.isSender) {
                // This message is by current User, hence we may delete it.
                if (md.isDirect) {
                    // This is a Direct Message
                    // TODO: Delete Direct message
                } else if (!md.reblogged) {
                    menu.add(0, CONTEXT_MENU_ITEM_DESTROY_STATUS, menuItemId++,
                            R.string.menu_item_destroy_status);
                }
            }

            if (!md.isSender) {
                if (md.senderFollowed) {
                    menu.add(0, CONTEXT_MENU_ITEM_STOP_FOLLOWING_SENDER, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToName(md.senderId)));
                } else {
                    menu.add(0, CONTEXT_MENU_ITEM_FOLLOW_SENDER, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToName(md.senderId)));
                }
            }
            if (!md.isAuthor && (md.authorId != md.senderId)) {
                if (md.authorFollowed) {
                    menu.add(0, CONTEXT_MENU_ITEM_STOP_FOLLOWING_AUTHOR, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToName(md.authorId)));
                } else {
                    menu.add(0, CONTEXT_MENU_ITEM_FOLLOW_AUTHOR, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToName(md.authorId)));
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "onCreateContextMenu: " + e.toString());
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

        mCurrentMsgId = info.id;
        MyAccount ma = MyAccount.getMyAccount(mMyAccountUserIdForCurrentMessage);
        if (ma != null) {
            long authorId;
            long senderId;
            switch (item.getItemId()) {
                case CONTEXT_MENU_ITEM_REPLY:
                    mTweetEditor.startEditingMessage("", mCurrentMsgId, 0, ma.getAccountGuid(), mIsTimelineCombined);
                    return true;

                case CONTEXT_MENU_ITEM_DIRECT_MESSAGE:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    if (authorId != 0) {
                        mTweetEditor.startEditingMessage("", mCurrentMsgId, authorId, ma.getAccountGuid(), mIsTimelineCombined);
                        return true;
                    }
                    break;

                case CONTEXT_MENU_ITEM_REBLOG:
                    serviceConnector.sendCommand( new CommandData(CommandEnum.REBLOG, ma.getAccountGuid(), mCurrentMsgId));
                    return true;

                case CONTEXT_MENU_ITEM_DESTROY_REBLOG:
                    serviceConnector.sendCommand( new CommandData(CommandEnum.DESTROY_REBLOG, ma.getAccountGuid(), mCurrentMsgId));
                    return true;

                case CONTEXT_MENU_ITEM_DESTROY_STATUS:
                    serviceConnector.sendCommand( new CommandData(CommandEnum.DESTROY_STATUS, ma.getAccountGuid(), mCurrentMsgId));
                    return true;

                case CONTEXT_MENU_ITEM_FAVORITE:
                    serviceConnector.sendCommand( new CommandData(CommandEnum.CREATE_FAVORITE, ma.getAccountGuid(), mCurrentMsgId));
                    return true;

                case CONTEXT_MENU_ITEM_DESTROY_FAVORITE:
                    serviceConnector.sendCommand( new CommandData(CommandEnum.DESTROY_FAVORITE, ma.getAccountGuid(), mCurrentMsgId));
                    return true;

                case CONTEXT_MENU_ITEM_SHARE:
                    String userName = MyProvider.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), mTimelineType, true, info.id);
                    Cursor c = getContentResolver().query(uri, new String[] {
                            MyDatabase.Msg.MSG_OID, MyDatabase.Msg.BODY
                    }, null, null, null);
                    try {
                        if (c != null && c.getCount() > 0) {
                            c.moveToFirst();
        
                            StringBuilder subject = new StringBuilder();
                            StringBuilder text = new StringBuilder();
                            String msgBody = c.getString(c.getColumnIndex(MyDatabase.Msg.BODY));
        
                            subject.append(getText(ma.alternativeTermResourceId(R.string.message)));
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
                            text.append("\n URL: " + ma.messagePermalink(userName, 
                                    c.getString(c.getColumnIndex(MyDatabase.Msg.MSG_OID))));
                            
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

                case CONTEXT_MENU_ITEM_SENDER_MESSAGES:
                {
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    if (senderId != 0) {
                        /**
                         * We better switch to the account selected for this message in order not to
                         * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                         */
                        MyAccount.setCurrentMyAccountGuid(ma.getAccountGuid());
                        switchTimelineActivity(TimelineTypeEnum.USER, mIsTimelineCombined, senderId);
                        return true;
                    }
                }
                    break;

                case CONTEXT_MENU_ITEM_AUTHOR_MESSAGES:
                {
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    if (authorId != 0) {
                        /**
                         * We better switch to the account selected for this message in order not to
                         * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                         */
                        MyAccount.setCurrentMyAccountGuid(ma.getAccountGuid());
                        switchTimelineActivity(TimelineTypeEnum.USER, mIsTimelineCombined, authorId);
                        return true;
                    }
                }
                    break;

                case CONTEXT_MENU_ITEM_FOLLOW_SENDER:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    serviceConnector.sendCommand( new CommandData(CommandEnum.FOLLOW_USER, ma.getAccountGuid(), senderId));
                    return true;
                case CONTEXT_MENU_ITEM_STOP_FOLLOWING_SENDER:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    serviceConnector.sendCommand( new CommandData(CommandEnum.STOP_FOLLOWING_USER, ma.getAccountGuid(), senderId));
                    return true;
                case CONTEXT_MENU_ITEM_FOLLOW_AUTHOR:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    serviceConnector.sendCommand( new CommandData(CommandEnum.FOLLOW_USER, ma.getAccountGuid(), authorId));
                    return true;
                case CONTEXT_MENU_ITEM_STOP_FOLLOWING_AUTHOR:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    serviceConnector.sendCommand( new CommandData(CommandEnum.STOP_FOLLOWING_USER, ma.getAccountGuid(), authorId));
                    return true;
                    
                case CONTEXT_MENU_ITEM_BLOCK:
                case CONTEXT_MENU_ITEM_PROFILE:
                    Toast.makeText(this, R.string.unimplemented, Toast.LENGTH_SHORT).show();
                    return true;
            }
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
                        R.id.message_author, R.id.message_body, R.id.message_details,
                        R.id.tweet_favorite
                }, getIntent().getData(), PROJECTION, MyDatabase.Msg.DEFAULT_SORT_ORDER);
        tweetsAdapter.setViewBinder(new TweetBinder());

        setListAdapter(tweetsAdapter);
    }

    /**
     * Determines where to save / retrieve position in the list
     * Two rows are always stored for each position hence two keys. Plus Query string is being stored for the search results.
     * @author yvolk
     */
    private class PositionStorage {
        /**
         * MyAccount for SharedPreferences ( =="" for DefaultSharedPreferences) 
         */
        public String accountGuid = "";
        /**
         * SharePreferences to use for storage 
         */
        public SharedPreferences sp = null;
        /**
         * Key name for the first visible item
         */
        public String keyFirst = "";
        /**
         * Key for the last item we should retrieve before restoring position
         */
        public String keyLast = "";
        /**
         * Key for the Query string
         */
        public String keyQueryString = "";
        
        public PositionStorage() {
            if ((mTimelineType != TimelineTypeEnum.USER) && !mIsTimelineCombined) {
                MyAccount ma = MyAccount.getMyAccount(mCurrentMyAccountUserId);
                if (ma != null) {
                    sp = ma.getMyAccountPreferences();
                    accountGuid = ma.getAccountGuid();
                } else {
                    Log.e(TAG, "No accoount for IserId=" + mCurrentMyAccountUserId);
                }
            }
            if (sp == null) {
                sp = MyPreferences.getDefaultSharedPreferences();
            }
            
            keyFirst = LAST_POS_KEY
                    + mTimelineType.save()
                    + (mTimelineType == TimelineTypeEnum.USER ? "_user"
                            + Long.toString(mSelectedUserId) : "") + (mIsSearchMode ? "_search" : "");
            keyLast = keyFirst + "_last";
            keyQueryString = LAST_POS_KEY + mTimelineType.save() + "_querystring";
        }
        
        /**
         * @param ps
         * @param lastRow Key for First visible row (false) or Last row that will be retrieved (true)
         * @return Saved Tweet id or -1 or -4 if none was found.
         */
        protected long getSavedPosition(boolean lastRow) {
            long savedItemId = -4;
            if (!mIsSearchMode
                    || (mQueryString.compareTo(sp.getString(
                            keyQueryString, "")) == 0)) {
                // Load saved position in Search mode only if that position was
                // saved for the same query string
                if (lastRow) {
                    savedItemId = sp.getLong(keyLast, -1);
                } else {
                    savedItemId = sp.getLong(keyFirst, -1);
                }
            }
            return savedItemId;
        }
        
    }
}
