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
import android.provider.SearchRecentSuggestions;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
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
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ToggleButton;

import static org.andstatus.app.ContextMenuItem.*;

import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.LatestTimelineItem;
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

import java.util.Locale;

/**
 * @author yvolk@yurivolkov.com, torgny.bjers
 */
public class TimelineActivity extends ListActivity implements MyServiceListener, OnScrollListener, OnItemClickListener {
    private static final String TAG = TimelineActivity.class.getSimpleName();

    private static final int DIALOG_ID_TIMELINE_TYPE = 9;

    private static final String KEY_IS_LOADING = "isLoading";
    private static final String KEY_LAST_POSITION = "last_position_";

    /**
     * Visibility of the layout indicates whether Messages are being loaded into the list (asynchronously...)
     * The layout appears at the bottom of the list of messages 
     * when new items are being loaded into the list 
     */
    private LinearLayout loadingLayout;

    private Cursor mCursor;

    private NotificationManager mNM;
    
    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    private final static int PAGE_SIZE = 100;

    /**
     * Is saved position restored (or some default positions set)?
     */
    private boolean positionRestored = false;
    
    /**
     * The is no more items in the query, so don't try to load more pages
     */
    private boolean noMoreItems = false;
    
    /**
     * For testing purposes
     */
    private int instanceId = 0;
    MyServiceReceiver serviceConnector;

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    private boolean mIsFinishing = false;

    /**
     * Timeline type
     */
    private TimelineTypeEnum mTimelineType = TimelineTypeEnum.UNKNOWN;

    /**
     * Is the timeline combined? (Timeline shows messages from all accounts)
     */
    private boolean mIsTimelineCombined = false;
    
    /**
     * UserId of the MyAccount, for which we show the activity
     */
    private long mCurrentMyAccountUserId = 0;
    
    /**
     * Selected User for the {@link MyDatabase.TimelineTypeEnum#USER} timeline.
     * This is either User Id of current account OR user id of any other selected user.
     * So it's never == 0 for the {@link MyDatabase.TimelineTypeEnum#USER} timeline
     */
    private long mSelectedUserId = 0;
    
    private long accountUserIdToActAs = 0; 
    
    /**
     * True if this timeline is filtered using query string ("Mentions" are not
     * counted here because they have separate TimelineType)
     */
    private boolean mIsSearchMode = false;

    /**
     * The string is not empty if this timeline is filtered using query string
     * ("Mentions" are not counted here because they have separate TimelineType)
     */
    private String mQueryString = "";

    /**
     * Time when shared preferences where changed
     */
    private long preferencesChangeTime = 0;

    /**
     * Id of the Message that was selected (clicked, or whose context menu item
     * was selected) TODO: clicked, restore position...
     */
    private long mCurrentMsgId = 0;
    /**
     *  Corresponding account information ( "Reply As..." ... ) 
     *  oh whose behalf we are going to execute an action on this line in the list (message...) 
     */
    private long actorUserIdForCurrentMessage = 0;

    /** 
     * Controls of the TweetEditor
     */
    private TweetEditor mTweetEditor;
 
    /** 
     * Table columns to use for the messages content
     */
    private static final String[] PROJECTION = new String[] {
            Msg._ID, User.AUTHOR_NAME, Msg.BODY, Msg.IN_REPLY_TO_MSG_ID, User.IN_REPLY_TO_NAME,
            User.RECIPIENT_NAME,
            MsgOfUser.FAVORITED, Msg.CREATED_DATE,
            User.LINKED_USER_ID
    };

    boolean isLoading() {
        //MyLog.v(TAG, "isLoading checked " + mIsLoading + ", instance " + instanceId);
        return (loadingLayout.getVisibility() == View.VISIBLE);
    }
    
    void setIsLoading(boolean isLoadingNew) {
        if (isLoading() != isLoadingNew) {
            MyLog.v(TAG, "isLoading set to " + isLoadingNew + ", instanceId=" + instanceId );
            if (isLoadingNew) {
                loadingLayout.setVisibility(View.VISIBLE);
            } else {
                loadingLayout.setVisibility(View.INVISIBLE);
            }
        }
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
            if (MyPreferences.isUpgrading()) {
                Log.i(TAG, "Upgrade is in progress");
                helpAsFirstActivity = true;
                showChangeLog = true;
            } else if (MyPreferences.shouldSetDefaultValues()) {
                Log.i(TAG, "We are running the Application for the very first time?");
                helpAsFirstActivity = true;
            } else if (MyAccount.getCurrentAccount() == null) {
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

        mCurrentMyAccountUserId = MyAccount.getCurrentAccountUserId();
        serviceConnector = new MyServiceReceiver(this);
        
        MyPreferences.loadTheme(TAG, this);

        // Request window features before loading the content view
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

        setContentView(R.layout.tweetlist);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.timeline_title);

        mTweetEditor = new TweetEditor(this);
        // TODO: Maybe this should be a parameter
        mTweetEditor.hide();

        boolean isInstanceStateRestored = false;
        boolean isLoadingNew = false;
        if (savedInstanceState != null) {
            TimelineTypeEnum timelineType_new = TimelineTypeEnum.load(savedInstanceState
                    .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
            if (timelineType_new != TimelineTypeEnum.UNKNOWN) {
                isInstanceStateRestored = true;
                mTimelineType = timelineType_new;
                mTweetEditor.loadState(savedInstanceState);
                if (savedInstanceState.containsKey(KEY_IS_LOADING)) {
                    isLoadingNew = savedInstanceState.getBoolean(KEY_IS_LOADING);
                }
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_ITEMID.key)) {
                    mCurrentMsgId = savedInstanceState.getLong(IntentExtra.EXTRA_ITEMID.key);
                }
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key)) {
                    mIsTimelineCombined = savedInstanceState.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key);
                }
                if (savedInstanceState.containsKey(SearchManager.QUERY)) {
                    mQueryString = savedInstanceState.getString(SearchManager.QUERY);
                }
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_SELECTEDUSERID.key)) {
                    mSelectedUserId = savedInstanceState.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key);
                }
            }
        }

        // Set up notification manager
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        // Create list footer to show the progress of message loading
        // We use "this" as a context, otherwise custom styles are not recognized...
        LayoutInflater inflater = LayoutInflater.from(this);
        loadingLayout = (LinearLayout) inflater.inflate(R.layout.item_loading, null);
        getListView().addFooterView(loadingLayout);
        setIsLoading(isLoadingNew);

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
                startActivityForResult(i, ActivityRequestCode.SELECT_ACCOUNT.id);
            }
        });
       
        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        createMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTweetEditor.isVisible()) {
                    mTweetEditor.hide();
                } else if (MyAccount.getCurrentAccount() != null) {
                    mTweetEditor.startEditingMessage("", 0, 0, MyAccount.getCurrentAccount(), mIsTimelineCombined);
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
        showDialog(DIALOG_ID_TIMELINE_TYPE);
    }
    
    /**
     * See <a href="http://developer.android.com/guide/topics/search/search-dialog.html">Creating 
     * a Search Interface</a>
     */
    @Override
    public boolean onSearchRequested() {
        Bundle appSearchData = new Bundle();
        appSearchData.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, mTimelineType.save());
        appSearchData.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
        appSearchData.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
        startSearch(null, false, appSearchData, false);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyLog.v(TAG, "onResume, instanceId=" + instanceId);
        if (!mIsFinishing) {
            if (MyAccount.getCurrentAccount() != null) {
                long preferencesChangeTimeNew = MyPreferences.initialize(this, this);
                if (preferencesChangeTimeNew != preferencesChangeTime) {
                    MyLog.v(TAG, "Restarting this Activity to apply all new changes of preferences");
                    finish();
                    switchTimelineActivity(mTimelineType, mIsTimelineCombined, mSelectedUserId);
                }
            } else { 
                MyLog.v(TAG, "Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        if (!mIsFinishing) {
            serviceConnector.registerReceiver(this);
            updateTitle();
            if (!isLoading()) {
                restoreListPosition();
            }
        }
    }

    /**
     * Save current position per User and per TimeleneType. 
     * The position is NOT saved (i.e. the stored position remains the same),
     * if there are no items in the list. Actually we save two Item IDs.
     * 1. The first visible item. 
     * 2. The last item we should retrieve before restoring the position.
     */
    private void saveListPosition() {
        long firstVisibleItemId = 0;
        long lastRetrievedItemId = 0;
        int firstScrollPos = 0;
        int lastScrollPos = -1;
        PositionStorage ps = new PositionStorage();

        firstScrollPos = getListView().getFirstVisiblePosition();
        android.widget.ListAdapter la = getListView().getAdapter();
        if (la == null) {
            MyLog.v(TAG, "Position wasn't saved - no adapters yet");
            return;
        }
        if (firstScrollPos > la.getCount() - 2) {
            // Skip footer
            firstScrollPos = la.getCount() - 2;
        }
        if (firstScrollPos >= 0) {
            firstVisibleItemId = la.getItemId(firstScrollPos);
            // We will load one more "page of tweets" below (older) current top item
            lastScrollPos = firstScrollPos + PAGE_SIZE;
            if (lastScrollPos > la.getCount() - 2) {
                if (firstScrollPos > PAGE_SIZE - 2) {
                    lastScrollPos = la.getCount() - 2;
                } else {
                    lastScrollPos = -1;
                }
            }
            // Log.v(TAG, "lastScrollPos=" + lastScrollPos);
            if (lastScrollPos >= 0) {
                lastRetrievedItemId = la.getItemId(lastScrollPos);
            }
        }

        if (firstVisibleItemId <= 0) {
            MyLog.v(TAG, "Position wasn't saved \"" + ps.accountGuid + "\"; " + ps.keyFirst);
            return;
        }

        ps.sp.edit().putLong(ps.keyFirst, firstVisibleItemId)
                .putLong(ps.keyLast, lastRetrievedItemId).commit();
        if (mIsSearchMode) {
            // Remember query string for which the position was saved
            ps.sp.edit().putString(ps.keyQueryString, mQueryString)
                    .commit();
        }

        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Position saved    \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                    + firstVisibleItemId + "; index=" + firstScrollPos + "; lastId="
                    + lastRetrievedItemId + "; index=" + lastScrollPos);
        }
    }

    private void forgetListPosition() {
        PositionStorage ps = new PositionStorage();
        ps.sp.edit().remove(ps.keyFirst).remove(ps.keyLast)
                .remove(ps.keyQueryString).commit();
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Position forgot   \"" + ps.accountGuid + "\"; " + ps.keyFirst);
        }
    }
    
    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    private void restoreListPosition() {
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
            forgetListPosition();
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
        serviceConnector.unregisterReceiver(this);

        if (positionRestored) {
            // Get rid of the "fast scroll thumb"
            ((ListView) findViewById(android.R.id.list)).setFastScrollEnabled(false);
            clearNotifications();
            if (!isLoading()) {
                saveListPosition();
            }
        }        
        positionRestored = false;
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
            intent.putExtra(IntentExtra.EXTRA_MSGTYPE.key, MyService.CommandEnum.NOTIFY_CLEAR.save());
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
            serviceConnector.unregisterReceiver(this);
        }
    }

    @Override
    public void finish() {
        MyLog.v(TAG,"Finish requested" + (mIsFinishing ? ", already finishing" : "") + ", instanceId=" + instanceId);
        if (!mIsFinishing) {
            mIsFinishing = true;
        }
        super.finish();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ID_TIMELINE_TYPE:
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
        MyAccount ma = MyAccount.getCurrentAccount();
        if (ma != null && ma.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
            MenuItem item = menu.findItem(R.id.reload_menu_item);
            item.setEnabled(false);
            item.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.preferences_menu_id:
                startMyPreferenceActivity();
                break;
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
        MyAccount ma = MyAccount.getAccountWhichMayBeLinkedToThisMessage(id, linkedUserId,
                mCurrentMyAccountUserId);
        if (ma == null) {
            Log.e(TAG, "Account for the message " + id + " was not found");
            return;
        }
        
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onItemClick, id=" + id + "; linkedUserId=" + linkedUserId + " account=" + ma.getAccountName());
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

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        if (!noMoreItems && positionRestored && !isLoading()) {
            // Idea from http://stackoverflow.com/questions/1080811/android-endless-list
            boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                    && (firstVisibleItem + visibleItemCount >= totalItemCount);
            if (loadMore) {
                MyLog.d(TAG, "Start Loading more items, rows=" + totalItemCount);
                saveListPosition();
                setIsLoading(true);
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
        Button selectAccountButton = (Button) findViewById(R.id.selectAccountButton);
        MyAccount ma = MyAccount.getCurrentAccount();
        String accountName = ma.shortestUniqueAccountName();
        if (ma.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
            accountName = "(" + accountName + ")";
        }
        selectAccountButton.setText(accountName);
        
        TextView rightTitle = (TextView) findViewById(R.id.custom_title_right_text);
        rightTitle.setText(rightText);

        Button createMessageButton = (Button) findViewById(R.id.createMessageButton);
        if (mTimelineType != TimelineTypeEnum.DIRECT) {
            createMessageButton.setText(getString(MyAccount.getCurrentAccount().alternativeTermForResourceId(R.string.button_create_message)));
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
        MyPreferences.initialize(this, this);
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
                .getStringExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key));
        if (timelineType_new != TimelineTypeEnum.UNKNOWN) {
            mTimelineType = timelineType_new;
            mIsTimelineCombined = intentNew.getBooleanExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
            mQueryString = intentNew.getStringExtra(SearchManager.QUERY);
            mSelectedUserId = intentNew.getLongExtra(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
        } else {
            Bundle appSearchData = intentNew.getBundleExtra(SearchManager.APP_DATA);
            if (appSearchData != null) {
                // We use other packaging of the same parameters in onSearchRequested
                timelineType_new = TimelineTypeEnum.load(appSearchData
                        .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
                if (timelineType_new != TimelineTypeEnum.UNKNOWN) {
                    mTimelineType = timelineType_new;
                    mIsTimelineCombined = appSearchData.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
                    /* The query itself is still from the Intent */
                    mQueryString = intentNew.getStringExtra(SearchManager.QUERY);
                    mSelectedUserId = appSearchData.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
                }
            }
        }
        if (mTimelineType == TimelineTypeEnum.UNKNOWN) {
            /* Set default values */
            mTimelineType = TimelineTypeEnum.HOME;
            mIsTimelineCombined = (MyAccount.numberOfPersistentAccounts() > 1);
            mQueryString = "";
            mSelectedUserId = 0;
        }
        mCurrentMyAccountUserId = MyAccount.getCurrentAccountUserId();
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
            mTweetEditor.startEditingMessage(textInitial, 0, 0, MyAccount.getCurrentAccount(), mIsTimelineCombined);
        }

        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "processNewIntent; type=\"" + mTimelineType.save() + "\"");
        }
    }

    private void updateThisOnChangedParameters() {
        MyServiceManager.setServiceAvailable();
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
            // Show the "Combined" toggle even for one account to see messages, 
            // which are not on the timeline.
            // E.g. messages by users, downloaded on demand.
            combinedTimelineToggle.setVisibility(View.VISIBLE);
        }
        noMoreItems = false;
        accountUserIdToActAs = 0;

        queryListData(false);
        
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
            if (doRestorePosition) {
                restoreListPosition();
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
         * @author yvolk@yurivolkov.com
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

                    // TODO: Move these selections to the {@link MyProvider} ?!
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
                        default:
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
                for (int attempt=0; attempt<3; attempt++) {
                    try {
                        cursor = getContentResolver().query(contentUri, PROJECTION, sa.selection,
                                sa.selectionArgs, sortOrder);
                        break;
                    } catch (IllegalStateException e) {
                        Log.d(TAG, "Attempt " + attempt + " to prepare cursor: " + e.getMessage());
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e1) {
                            Log.d(TAG, "Attempt " + attempt + " to prepare cursor was interrupted");
                            break;
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                boolean doRestorePosition = true;
                if (cursor != null && !mIsFinishing) {
                    boolean cursorSet = false;
                    if (positionRestored && (getListAdapter() != null)) {
                        if (loadOneMorePage) {
                            // This will prevent continuous loading...
                            if (cursor.getCount() > getListAdapter().getCount()) {
                                MyLog.v(TAG, "On changing Cursor");
                                ((SimpleCursorAdapter) getListAdapter()).changeCursor(cursor);
                                mCursor = cursor;
                            } else {
                                noMoreItems = true;
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
                    if (cursor == null) {
                        cursorInfo = "new cursor is null";
                    } else if (mCursor == null) {
                        cursorInfo = "cursor is null";
                    } else if (mCursor.isClosed()) {
                        cursorInfo = "cursor is Closed";
                    } else {
                        cursorInfo = mCursor.getCount() + " rows";
                    }
                    Log.v(TAG, "queryListData; ended, " + cursorInfo + ", " + Double.valueOf((System.nanoTime() - startTime)/1.0E6).longValue() + " ms");
                }
                
                queryListDataEnded(doRestorePosition);
                
                if (!loadOneMorePage) {
                    switch (mTimelineType) {
                        case USER:
                        case FOLLOWING_USER:
                            // This timeline doesn't update automatically so let's do it now if necessary
                            LatestTimelineItem latestTimelineItem = new LatestTimelineItem(mTimelineType, mSelectedUserId);
                            if (latestTimelineItem.isTimeToAutoUpdate()) {
                                manualReload(false);
                            }
                            break;
                        default:
                            if ( MyProvider.userIdToLongColumnValue(User.HOME_TIMELINE_DATE, mCurrentMyAccountUserId) == 0) {
                                // This is supposed to be a one time task.
                                manualReload(true);
                            } 
                    }
                }
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
        MyAccount ma = MyAccount.fromUserId(mCurrentMyAccountUserId);
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
            default:
                break;
        }
        boolean allAccounts = mIsTimelineCombined;
        if (userId != 0) {
            allAccounts = false;
            long originId = MyProvider.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, userId);
            if (originId == 0) {
                Log.e(TAG, "Unknown origin for userId=" + userId);
                return;
            }
            if (ma == null || ma.getOriginId() != originId) {
                ma = MyAccount.fromUserId(userId);
                if (ma == null) {
                    ma = MyAccount.findFirstMyAccountByOriginId(originId);
                }
            }
        }
        if (!allAccounts && ma == null) {
            return;
        }

        setIsLoading(true);
        MyServiceManager.sendCommand(
                new CommandData(CommandEnum.FETCH_TIMELINE,
                        allAccounts ? "" : ma.getAccountName(), timelineType, userId)
                );

        if (allTimelineTypes && ma != null) {
            ma.requestSync();
        }
    }
    
    protected void startMyPreferenceActivity() {
        finish();
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
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key, timelineType.save());
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, isTimelineCombined);
        intent.putExtra(IntentExtra.EXTRA_SELECTEDUSERID.key, selectedUserId);
        // We don't use the Action anywhere, so there is no need it setting it.
        // - we're analyzing query instead!
        // intent.setAction(Intent.ACTION_SEARCH);
        startActivity(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_IS_LOADING, isLoading());

        mTweetEditor.saveState(outState);
        outState.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, mTimelineType.save());
        outState.putLong(IntentExtra.EXTRA_ITEMID.key, mCurrentMsgId);
        outState.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
        outState.putString(SearchManager.QUERY, mQueryString);
        outState.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    MyAccount ma = MyAccount.fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                    if (ma != null) {
                        MyLog.v(TAG, "Restarting the activity for the selected account " + ma.getAccountName());
                        finish();
                        TimelineTypeEnum timelineTypeNew = mTimelineType;
                        if (mTimelineType == TimelineTypeEnum.USER && mSelectedUserId != ma.getUserId()) {
                            /*  "Other User's timeline" vs "My User's timeline" 
                             * Actually we saw messages of other user, not of (previous) MyAccount,
                             * so let's switch to the HOME
                             * TODO: Open "Other User timeline" in a separate Activity
                             */
                            timelineTypeNew = TimelineTypeEnum.HOME;
                        }
                        MyAccount.setCurrentAccount(ma);
                        switchTimelineActivity(timelineTypeNew, mIsTimelineCombined, ma.getUserId());
                    }
                }
                break;
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount ma = MyAccount.fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                    if (ma != null) {
                        accountUserIdToActAs = ma.getUserId();
                        getListView().showContextMenu();
                    }
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

        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        int menuItemId = 0;
        mCurrentMsgId = info.id;
        actorUserIdForCurrentMessage = 0;
        long userIdForThisMessage = ( accountUserIdToActAs==0 ? getLinkedUserIdFromCursor(info.position) : accountUserIdToActAs);
        MessageDataForContextMenu md = new MessageDataForContextMenu(this, userIdForThisMessage, mCurrentMyAccountUserId, mTimelineType, mCurrentMsgId
                );
        if (md.ma == null) {
            return;
        }
        if (accountUserIdToActAs==0 && md.canUseSecondAccountInsteadOfFirst) {
            // Yes, use current Account!
            md = new MessageDataForContextMenu(this, mCurrentMyAccountUserId, 0, mTimelineType, mCurrentMsgId);
        }
        actorUserIdForCurrentMessage = md.ma.getUserId();
        accountUserIdToActAs = 0;

        // Create the Context menu
        try {
            menu.setHeaderTitle((mIsTimelineCombined ? md.ma.getAccountName() + ": " : "")
                    + md.body);

            // Add menu items
            if (!md.isDirect) {
                REPLY.addTo(menu, menuItemId++, R.string.menu_item_reply);
            }
            SHARE.addTo(menu, menuItemId++, R.string.menu_item_share);

            // TODO: Only if he follows me?
            DIRECT_MESSAGE.addTo(menu, menuItemId++,
                    R.string.menu_item_direct_message);

            if (!md.isDirect) {
                if (md.favorited) {
                    DESTROY_FAVORITE.addTo(menu, menuItemId++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    FAVORITE.addTo(menu, menuItemId++,
                            R.string.menu_item_favorite);
                }
                if (md.reblogged) {
                    DESTROY_REBLOG.addTo(menu, menuItemId++,
                            md.ma.alternativeTermForResourceId(R.string.menu_item_destroy_reblog));
                } else {
                    // Don't allow a User to reblog himself
                    if (actorUserIdForCurrentMessage != md.senderId) {
                        REBLOG.addTo(menu, menuItemId++,
                                md.ma.alternativeTermForResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (mSelectedUserId != md.senderId) {
                /*
                 * Messages by the Sender of this message ("User timeline" of
                 * that user)
                 */
                SENDER_MESSAGES.addTo(menu, menuItemId++,
                        String.format(Locale.getDefault(),
                                getText(R.string.menu_item_user_messages).toString(),
                                MyProvider.userIdToName(md.senderId)));
            }

            if (mSelectedUserId != md.authorId && md.senderId != md.authorId) {
                /*
                 * Messages by the Author of this message ("User timeline" of
                 * that user)
                 */
                AUTHOR_MESSAGES.addTo(menu, menuItemId++,
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
                    DESTROY_STATUS.addTo(menu, menuItemId++,
                            R.string.menu_item_destroy_status);
                }
            }

            if (!md.isSender) {
                if (md.senderFollowed) {
                    STOP_FOLLOWING_SENDER.addTo(menu, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToName(md.senderId)));
                } else {
                    FOLLOW_SENDER.addTo(menu, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToName(md.senderId)));
                }
            }
            if (!md.isAuthor && (md.authorId != md.senderId)) {
                if (md.authorFollowed) {
                    STOP_FOLLOWING_AUTHOR.addTo(menu, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToName(md.authorId)));
                } else {
                    FOLLOW_AUTHOR.addTo(menu, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToName(md.authorId)));
                }
            }
            switch (md.ma.accountsOfThisOrigin()) {
                case 2:
                    ACT_AS_USER.addTo(menu, menuItemId++,
                            String.format(Locale.getDefault(),
                                    getText(R.string.menu_item_act_as_user).toString(),
                                    md.ma.firstOtherAccountOfThisOrigin().shortestUniqueAccountName()));
                    break;
                default:
                    ACT_AS.addTo(menu, menuItemId++,
                            R.string.menu_item_act_as);
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
        MyAccount ma = MyAccount.fromUserId(actorUserIdForCurrentMessage);
        if (ma != null) {
            long authorId;
            long senderId;
            ContextMenuItem contextMenuItem = ContextMenuItem.fromId(item.getItemId());
            MyLog.v(TAG, "onContextItemSelected: " + contextMenuItem + "; actor=" + ma.getAccountName());
            switch (contextMenuItem) {
                case REPLY:
                    mTweetEditor.startEditingMessage("", mCurrentMsgId, 0, ma, mIsTimelineCombined);
                    return true;
                case DIRECT_MESSAGE:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    if (authorId != 0) {
                        mTweetEditor.startEditingMessage("", mCurrentMsgId, authorId, ma, mIsTimelineCombined);
                        return true;
                    }
                    break;
                case REBLOG:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.REBLOG, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case DESTROY_REBLOG:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.DESTROY_REBLOG, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case DESTROY_STATUS:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.DESTROY_STATUS, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case FAVORITE:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.CREATE_FAVORITE, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case DESTROY_FAVORITE:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.DESTROY_FAVORITE, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case SHARE:
                    String userName = MyProvider.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), mTimelineType, true, info.id);
                    Cursor c = getContentResolver().query(uri, new String[] {
                            MyDatabase.Msg.MSG_ID, MyDatabase.Msg.BODY
                    }, null, null, null);
                    try {
                        if (c != null && c.getCount() > 0) {
                            c.moveToFirst();
        
                            StringBuilder subject = new StringBuilder();
                            StringBuilder text = new StringBuilder();
                            String msgBody = c.getString(c.getColumnIndex(MyDatabase.Msg.BODY));
        
                            subject.append(getText(ma.alternativeTermForResourceId(R.string.message)));
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
                                    c.getLong(c.getColumnIndex(MyDatabase.Msg.MSG_ID))));
                            
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
                case SENDER_MESSAGES:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    if (senderId != 0) {
                        /**
                         * We better switch to the account selected for this message in order not to
                         * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                         */
                        MyAccount.setCurrentAccount(ma);
                        switchTimelineActivity(TimelineTypeEnum.USER, mIsTimelineCombined, senderId);
                        return true;
                    }
                    break;
                case AUTHOR_MESSAGES:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    if (authorId != 0) {
                        /**
                         * We better switch to the account selected for this message in order not to
                         * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                         */
                        MyAccount.setCurrentAccount(ma);
                        switchTimelineActivity(TimelineTypeEnum.USER, mIsTimelineCombined, authorId);
                        return true;
                    }
                    break;
                case FOLLOW_SENDER:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.FOLLOW_USER, ma.getAccountName(), senderId));
                    return true;
                case STOP_FOLLOWING_SENDER:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.STOP_FOLLOWING_USER, ma.getAccountName(), senderId));
                    return true;
                case FOLLOW_AUTHOR:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.FOLLOW_USER, ma.getAccountName(), authorId));
                    return true;
                case STOP_FOLLOWING_AUTHOR:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.STOP_FOLLOWING_USER, ma.getAccountName(), authorId));
                    return true;
                case ACT_AS:
                    Intent i = new Intent(this, AccountSelector.class);
                    i.putExtra(IntentExtra.ORIGIN_ID.key, ma.getOriginId());
                    startActivityForResult(i, ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS.id);
                    return true;
                case ACT_AS_USER:
                    accountUserIdToActAs = ma.firstOtherAccountOfThisOrigin().getUserId();
                    getListView().post(new Runnable() {

                        @Override
                        public void run() {
                            getListView().showContextMenu();
                        }
                    });                    
                    return true;
                default:
                    return false;
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
     * @author yvolk@yurivolkov.com
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
                MyAccount ma = MyAccount.fromUserId(mCurrentMyAccountUserId);
                if (ma != null) {
                    sp = ma.getAccountPreferences();
                    accountGuid = ma.getAccountName();
                } else {
                    Log.e(TAG, "No accoount for IserId=" + mCurrentMyAccountUserId);
                }
            }
            if (sp == null) {
                sp = MyPreferences.getDefaultSharedPreferences();
            }
            
            keyFirst = KEY_LAST_POSITION
                    + mTimelineType.save()
                    + (mTimelineType == TimelineTypeEnum.USER ? "_user"
                            + Long.toString(mSelectedUserId) : "") + (mIsSearchMode ? "_search" : "");
            keyLast = keyFirst + "_last";
            keyQueryString = KEY_LAST_POSITION + mTimelineType.save() + "_querystring";
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

    @Override
    public void onReceive(CommandData commandData) {
        switch (commandData.command) {
            case FETCH_TIMELINE:
                if (commandData.timelineType == mTimelineType) {
                    setIsLoading(false);
                }
                break;
            case RATE_LIMIT_STATUS:
                if (commandData.commandResult.hourly_limit > 0) {
                    updateTitle(commandData.commandResult.remaining_hits + "/"
                            + commandData.commandResult.hourly_limit);
                }
            default:
                break;
        }
    }
}
