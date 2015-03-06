/* 
 * Copyright (c) 2011-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineSearchSuggestionsProvider;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.TimelineViewBinder;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.widget.MySimpleCursorAdapter;
import org.andstatus.app.widget.MySwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivity extends ListActivity implements MyServiceListener,
        OnScrollListener, OnItemClickListener, ActionableMessageList, 
        LoaderCallbacks<Cursor> {
    private static final int DIALOG_ID_TIMELINE_TYPE = 9;
    private static final int LOADER_ID = 1;
    private static final String ACTIVITY_PERSISTENCE_NAME = TimelineActivity.class.getSimpleName();

    /**
     * Visibility of the layout indicates whether Messages are being loaded into the list (asynchronously...)
     * The layout appears at the bottom of the list of messages 
     * when new items are being loaded into the list 
     */
    private LinearLayout mLoadingLayout;
    private MySwipeRefreshLayout mSwipeRefreshLayout = null;

    /** Parameters of currently shown Timeline */
    private TimelineListParameters mListParameters;
    private TimelineListParameters mListParametersNew;

    /**
     * Is saved position restored (or some default positions set)?
     */
    private boolean mPositionRestored = false;

    /**
     * The is no more items in the query, so don't try to load more pages
     */
    private boolean mNoMoreItems = false;

    /**
     * For testing purposes
     */
    private long mInstanceId = 0;
    MyServiceReceiver mServiceConnector;

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    private volatile boolean mFinishing = false;

    private boolean mShowSyncIndicatorOnTimeline = false;
    private View mSyncIndicator = null;
    private boolean mIsLoading = false;

    /**
     * Time when shared preferences where changed
     */
    private long mPreferencesChangeTime = 0;

    private MessageContextMenu mContextMenu;
    private MessageEditor mMessageEditor;
    private Menu mOptionsMenu = null;

    private String mTextToShareViaThisApp = "";
    private Uri mMediaToShareViaThisApp = Uri.EMPTY;

    private String mRateLimitText = "";

    DrawerLayout mDrawerLayout;
    ActionBarDrawerToggle mDrawerToggle;

    /**
     * This method is the first of the whole application to be called 
     * when the application starts for the very first time.
     * So we may put some Application initialization code here. 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mListParameters = new TimelineListParameters(this);
        mListParametersNew = new TimelineListParameters(this);
        if (mInstanceId == 0) {
            mInstanceId = InstanceId.next();
        } else {
            MyLog.d(this, "onCreate reusing the same instanceId=" + mInstanceId);
        }

        mPreferencesChangeTime = MyContextHolder.initialize(this, this);
        mShowSyncIndicatorOnTimeline = MyPreferences.getBoolean(
                MyPreferences.KEY_SYNC_INDICATOR_ON_TIMELINE, true);

        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            MyLog.d(this, "onCreate instanceId=" + mInstanceId 
                    + " , preferencesChangeTime=" + mPreferencesChangeTime
                    + (MyContextHolder.get().isReady() ? "" : ", MyContext is not ready")
                    );
        }
        if (HelpActivity.startFromActivity(this)) {
            return;
        }

        mListParametersNew.myAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        mServiceConnector = new MyServiceReceiver(this);

        MyPreferences.setThemedContentView(this, R.layout.timeline);

        mSyncIndicator = findViewById(R.id.sync_indicator);
        mContextMenu = new MessageContextMenu(this);
        mMessageEditor = new MessageEditor(this);
        initializeSwipeRefresh();

        restoreActivityState();
        
        LayoutInflater inflater = LayoutInflater.from(this);
        // Create list footer to show the progress of message loading
        mLoadingLayout = (LinearLayout) inflater.inflate(R.layout.item_loading, null);
        getListView().addFooterView(mLoadingLayout);

        createListAdapter(getEmptyCursor());

        // Attach listeners to the message list
        getListView().setOnScrollListener(this);
        getListView().setOnCreateContextMenuListener(mContextMenu);
        getListView().setOnItemClickListener(this);

        initializeDrawer();

        getLoaderManager().initLoader(LOADER_ID, null, this);

        if (savedInstanceState == null) {
            parseNewIntent(getIntent());
        }
        updateScreen();
        queryListData(false);
    }

    private void initializeSwipeRefresh() {
        mSwipeRefreshLayout = (MySwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                manualReload(false, true);
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    private void initializeDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.string.drawer_open, 
                R.string.drawer_close 
                ) {
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
    }

    private Cursor getEmptyCursor() {
        return new MatrixCursor(TimelineSql.getTimelineProjection());
    }

    private boolean restoreActivityState() {
        SharedPreferences activityState = MyPreferences.getSharedPreferences(ACTIVITY_PERSISTENCE_NAME);
        boolean stateRestored = false;
        if (activityState != null) {
            stateRestored = mListParametersNew.restoreState(activityState);
            if (stateRestored) {
                mMessageEditor.loadState(activityState);
                mContextMenu.loadState(activityState);
            }
        }
        return stateRestored;
    }

    /**
     * View.OnClickListener
     */
    public boolean onCombinedTimelineToggleClick(View item) {
        closeDrawer();
        boolean on = !isTimelineCombined();
        MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, on).commit();
        mContextMenu.switchTimelineActivity(mListParametersNew.getTimelineType(), on, mListParametersNew.myAccountUserId);
        return true;
    }

    private void closeDrawer() {
        ViewGroup mDrawerList = (ViewGroup) findViewById(R.id.navigation_drawer);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    /**
     * View.OnClickListener
     */
    public boolean onTimelineTypeButtonClick(View item) {
        showDialog(DIALOG_ID_TIMELINE_TYPE);
        closeDrawer();
        return true;
    }

    /**
     * View.OnClickListener
     */
    public boolean onSelectAccountButtonClick(View item) {
        if (MyContextHolder.get().persistentAccounts().size() > 1) {
            AccountSelector.selectAccount(TimelineActivity.this, 0, ActivityRequestCode.SELECT_ACCOUNT);
        }
        closeDrawer();
        return true;
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/search/search-dialog.html">Creating 
     * a Search Interface</a>
     */
    @Override
    public boolean onSearchRequested() {
        return onSearchRequested(false);
    }

    private boolean onSearchRequested(boolean appGlobalSearch) {
        final String method = "onSearchRequested";
        Bundle appSearchData = new Bundle();
        appSearchData.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                appGlobalSearch ? TimelineTypeEnum.EVERYTHING.save() : mListParametersNew.getTimelineType().save());
        appSearchData.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mListParametersNew.isTimelineCombined());
        appSearchData.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mListParametersNew.mSelectedUserId);
        appSearchData.putBoolean(IntentExtra.EXTRA_GLOBAL_SEARCH.key, appGlobalSearch);
        MyLog.v(this, method  + ": " + appSearchData);
        startSearch(null, false, appSearchData, false);
        return true;
    }

    @Override
    protected void onResume() {
        String method = "onResume";
        super.onResume();
        MyLog.v(this, method + ", instanceId=" + mInstanceId);
        if (!mFinishing) {
            if (MyContextHolder.get().persistentAccounts().getCurrentAccount().isValid()) {
                long preferencesChangeTimeNew = MyContextHolder.initialize(this, this);
                if (preferencesChangeTimeNew != mPreferencesChangeTime) {
                    MyLog.v(this, method + "; Restarting this Activity to apply all new changes of preferences");
                    finish();
                    mContextMenu.switchTimelineActivity(mListParametersNew.getTimelineType(), mListParametersNew.isTimelineCombined(), mListParametersNew.mSelectedUserId);
                }
            } else { 
                MyLog.v(this, method + "; Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        if (!mFinishing) {
            MyContextHolder.get().setInForeground(true);
            mServiceConnector.registerReceiver(this);
        }
    }

    private void saveListPosition() {
        new TimelineListPositionStorage(getListAdapter(), getListView(), mListParameters).save();
    }

    @Override
    public void onContentChanged() {
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            MyLog.d(this, "onContentChanged started");
        }
        super.onContentChanged();
    }

    @Override
    protected void onPause() {
        final String method = "onPause";
        super.onPause();
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method + "; instanceId=" + mInstanceId);
        }
        mServiceConnector.unregisterReceiver(this);
        setSyncIndicator(method, false);

        if (mPositionRestored) {
            setFastScrollThumb(false);
            if (!isLoading()) {
                saveListPosition();
            }
            mPositionRestored = false;
        }
        saveActivityState();
        MyContextHolder.get().setInForeground(false);
    }

    private void setSyncIndicator(String source, boolean isVisible) {
        if (isVisible ? (mSyncIndicator.getVisibility() != View.VISIBLE) : ((mSyncIndicator.getVisibility() == View.VISIBLE))) {
            MyLog.v(this, source + " set Sync indicator to " + isVisible);
            mSyncIndicator.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
    }
    
    private void setFastScrollThumb(boolean isVisible) {
        getListView().setFastScrollEnabled(isVisible);
    }

    /**
     * Cancel notifications of loading timeline, which were set during Timeline downloading
     */
    private void clearNotifications() {
        MyContextHolder.get().clearNotification(getTimelineType());
        MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.NOTIFY_CLEAR,
                MyContextHolder.get().persistentAccounts()
                        .fromUserId(mListParametersNew.myAccountUserId).getAccountName()));
    }

    @Override
    public void onDestroy() {
        MyLog.v(this,"onDestroy, instanceId=" + mInstanceId);
        if (mServiceConnector != null) {
            mServiceConnector.unregisterReceiver(this);
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        MyLog.v(this, "Finish requested" + (mFinishing ? ", already finishing" : "") 
                + ", instanceId=" + mInstanceId);
        if (!mFinishing) {
            mFinishing = true;
        }
        runOnUiThread( new Runnable() {
            @Override 
            public void run() {
                if (mPositionRestored) {
                    saveListPosition();
                }
            }
        });
        super.finish();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ID_TIMELINE_TYPE:
                return newTimelinetypeSelector();
            default:
                break;
        }
        return super.onCreateDialog(id);
    }

    private AlertDialog newTimelinetypeSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_select_timeline);
        final TimelineTypeSelector selector = new TimelineTypeSelector(this);
        builder.setItems(selector.getTitles(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The 'which' argument contains the index position of the
                // selected item
                TimelineTypeEnum type = selector.positionToType(which);
                if (type != TimelineTypeEnum.UNKNOWN) {
                    mContextMenu.switchTimelineActivity(type,
                            mListParametersNew.isTimelineCombined(), mListParametersNew.myAccountUserId);
                }
            }
        });
        return builder.create();                
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        mContextMenu.onContextItemSelected(item);
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.timeline, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MyAccount ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        boolean enableReload = isTimelineCombined() || ma.isValidAndVerified();
        MenuItem item = menu.findItem(R.id.reload_menu_item);
        item.setEnabled(enableReload);
        item.setVisible(enableReload);

        prepareDrawer();

        if (mContextMenu != null) {
            mContextMenu.setAccountUserIdToActAs(0);
        }

        if (mMessageEditor != null) {
            mMessageEditor.onPrepareOptionsMenu(menu);
        }

        boolean enableGlobalSearch = MyContextHolder.get().persistentAccounts()
                .isGlobalSearchSupported(ma, isTimelineCombined());
        item = menu.findItem(R.id.global_search_menu_id);
        item.setEnabled(enableGlobalSearch);
        item.setVisible(enableGlobalSearch);

        return super.onPrepareOptionsMenu(menu);
    }

    private void prepareDrawer() {
        ViewGroup mDrawerList = (ViewGroup) findViewById(R.id.navigation_drawer);
        if (mDrawerList == null) {
            return;
        }
        TextView item = (TextView) mDrawerList.findViewById(R.id.timelineTypeButton);
        item.setText(timelineTypeButtonText());
        prepareCombinedTimelineToggle(mDrawerList);
        updateAccountButtonText(mDrawerList);
    }

    private void prepareCombinedTimelineToggle(ViewGroup list) {
        CheckBox combinedTimelineToggle = (CheckBox) list.findViewById(R.id.combinedTimelineToggle);
        combinedTimelineToggle.setChecked(isTimelineCombined());
        if (mListParametersNew.mSelectedUserId != 0 && mListParametersNew.mSelectedUserId != mListParametersNew.myAccountUserId) {
            combinedTimelineToggle.setVisibility(View.GONE);
        } else {
            // Show the "Combined" toggle even for one account to see messages, 
            // which are not on the timeline.
            // E.g. messages by users, downloaded on demand.
            combinedTimelineToggle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.global_search_menu_id:
                onSearchRequested(true);
                break;
            case R.id.search_menu_id:
                onSearchRequested();
                break;
            case R.id.reload_menu_item:
                manualReload(false, true);
                break;
            case R.id.commands_queue_id:
                startActivity(new Intent(getActivity(), QueueViewer.class));
                break;
            case R.id.preferences_menu_id:
                startMyPreferenceActivity();
                break;
            case R.id.help_menu_id:
                onHelp();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onHelp() {
        Intent intent = new Intent(this, HelpActivity.class);
        intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, HelpActivity.PAGE_INDEX_USER_GUIDE);
        startActivity(intent);
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
    public void onItemClick(AdapterView<?> adapterView, final View view, final int position, final long id) {
        if (id <= 0) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, "onItemClick, position=" + position + "; id=" + id + "; view=" + view);
            }
            return;
        }

        new AsyncTask<Void, Void, Uri>() {

            @Override
            protected Uri doInBackground(Void... params) {
                long linkedUserId = getLinkedUserIdFromCursor(position);
                MyAccount ma = MyContextHolder.get().persistentAccounts().getAccountWhichMayBeLinkedToThisMessage(id, linkedUserId,
                        mListParametersNew.myAccountUserId);
                if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                    MyLog.v(this,
                            "onItemClick, position=" + position + "; id=" + id + "; view=" + view
                            + "; linkedUserId=" + linkedUserId 
                            + " account=" + ma.getAccountName());
                }
                return MyProvider.getTimelineMsgUri(ma.getUserId(), mListParametersNew.getTimelineType(), true, id);
            }

            @Override
            protected void onPostExecute(Uri uri) {
                String action = getIntent().getAction();
                if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                    if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                        MyLog.d(this, "onItemClick, setData=" + uri);
                    }
                    setResult(RESULT_OK, new Intent().setData(uri));
                } else {
                    if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                        MyLog.d(this, "onItemClick, startActivity=" + uri);
                    }
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
            }

        }.execute();
    }

    /**
     * @param position Of current item in the underlying Cursor
     * @return id of the User linked to this message. This link reflects the User's timeline 
     * or an Account which was used to retrieved the message
     */
    @Override
    public long getLinkedUserIdFromCursor(int position) {
        long userId = 0;
        try {
            Cursor cursor = null;
            if (getListAdapter() != null) {
                cursor = ((CursorAdapter) getListAdapter()).getCursor();
            }
            if (cursor != null && !cursor.isClosed()) {
                cursor.moveToPosition(position);
                int columnIndex = cursor.getColumnIndex(User.LINKED_USER_ID);
                if (columnIndex > -1) {
                    userId = cursor.getLong(columnIndex);
                }
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return userId;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        if (!mNoMoreItems && mPositionRestored && !isLoading()) {
            // Idea from http://stackoverflow.com/questions/1080811/android-endless-list
            boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                    && (firstVisibleItem + visibleItemCount >= totalItemCount);
            if (loadMore) {
                MyLog.d(this, "Start Loading more items, rows=" + totalItemCount);
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
                setFastScrollThumb(true);
                break;
            default:
                break;
        }
    }

    private String timelineTypeButtonText() {
        CharSequence timelinename = mListParametersNew.getTimelineType().getTitle(this);
        return timelinename + (TextUtils.isEmpty(mListParametersNew.mSearchQuery) ? "" : " *");
    }

    private void updateAccountButtonText(ViewGroup mDrawerList) {
        TextView selectAccountButton = (TextView) mDrawerList.findViewById(R.id.selectAccountButton);
        String accountButtonText = buildAccountButtonText(mListParametersNew.myAccountUserId);
        selectAccountButton.setText(accountButtonText);
    }

    public static String buildAccountButtonText(long myAccountUserId) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(myAccountUserId);
        String accountButtonText = ma.shortestUniqueAccountName();
        if (!ma.isValidAndVerified()) {
            accountButtonText = "(" + accountButtonText + ")";
        }
        return accountButtonText;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "onNewIntent, instanceId=" + mInstanceId
                    + (mFinishing ? ", Is finishing" : "")
                    );
        }
        if (mFinishing) {
            finish();
            return;
        }
        super.onNewIntent(intent);
        MyContextHolder.initialize(this, this);
        parseNewIntent(intent);
        updateScreen();
        queryListData(false);
    }

    private void parseNewIntent(Intent intentNew) {
        mRateLimitText = "";
        mListParametersNew.setTimelineType(TimelineTypeEnum.UNKNOWN);
        mListParametersNew.myAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        mListParametersNew.mSelectedUserId = 0;
        parseAppSearchData(intentNew);
        if (mListParametersNew.getTimelineType() == TimelineTypeEnum.UNKNOWN) {
            mListParametersNew.parseIntentData(intentNew);
        }
        if (mListParametersNew.getTimelineType() == TimelineTypeEnum.UNKNOWN) {
            /* Set default values */
            mListParametersNew.setTimelineType(TimelineTypeEnum.HOME);
            mListParametersNew.mSearchQuery = "";
        }
        if (mListParametersNew.getTimelineType() == TimelineTypeEnum.USER) {
            if (mListParametersNew.mSelectedUserId == 0) {
                mListParametersNew.mSelectedUserId = mListParametersNew.myAccountUserId;
            }
        } else {
            mListParametersNew.mSelectedUserId = 0;
        }

        if (Intent.ACTION_SEND.equals(intentNew.getAction())) {
            shareViaThisApplication(intentNew.getStringExtra(Intent.EXTRA_SUBJECT), 
                    intentNew.getStringExtra(Intent.EXTRA_TEXT),
                    (Uri) intentNew.getParcelableExtra(Intent.EXTRA_STREAM));
        }
    }

    private void parseAppSearchData(Intent intentNew) {
        Bundle appSearchData = intentNew.getBundleExtra(SearchManager.APP_DATA);
        if (appSearchData != null) {
            // We use other packaging of the same parameters in onSearchRequested
            mListParametersNew.setTimelineType(TimelineTypeEnum.load(appSearchData
                    .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key)));
            if (mListParametersNew.getTimelineType() != TimelineTypeEnum.UNKNOWN) {
                mListParametersNew.setTimelineCombined(appSearchData.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mListParametersNew.isTimelineCombined()));
                /* The query itself is still from the Intent */
                mListParametersNew.mSearchQuery = TimelineListParameters.notNullString(intentNew.getStringExtra(SearchManager.QUERY));
                mListParametersNew.mSelectedUserId = appSearchData.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mListParametersNew.mSelectedUserId);
                if (!TextUtils.isEmpty(mListParametersNew.mSearchQuery)
                        && appSearchData.getBoolean(IntentExtra.EXTRA_GLOBAL_SEARCH.key, false)) {
                    setSyncing("Global search: " + mListParametersNew.mSearchQuery, true);
                    MyServiceManager.sendForegroundCommand(
                            CommandData.searchCommand(
                                    isTimelineCombined()
                                    ? ""
                                            : MyContextHolder.get().persistentAccounts()
                                            .getCurrentAccountName(),
                                            mListParametersNew.mSearchQuery));
                }
            }
        }
    }

    private void shareViaThisApplication(String subject, String text, Uri mediaUri) {
        if (TextUtils.isEmpty(subject) && TextUtils.isEmpty(text) && UriUtils.isEmpty(mediaUri)) {
            return;
        }
        mTextToShareViaThisApp = "";
        mMediaToShareViaThisApp = mediaUri;
        if (subjectHasAdditionalContent(subject, text)) {
            mTextToShareViaThisApp += subject;
        }
        if (!TextUtils.isEmpty(text)) {
            if (!TextUtils.isEmpty(mTextToShareViaThisApp)) {
                mTextToShareViaThisApp += " ";
            }
            mTextToShareViaThisApp += text;
        }
        MyLog.v(this, "Share via this app " 
                + (!TextUtils.isEmpty(mTextToShareViaThisApp) ? "; text:'" + mTextToShareViaThisApp +"'" : "") 
                + (!UriUtils.isEmpty(mMediaToShareViaThisApp) ? "; media:" + mMediaToShareViaThisApp.toString() : ""));
        AccountSelector.selectAccount(this, 0, ActivityRequestCode.SELECT_ACCOUNT_TO_SHARE_VIA);
    }

    static boolean subjectHasAdditionalContent(String subject, String text) {
        if (TextUtils.isEmpty(subject)) {
            return false;
        }
        if (TextUtils.isEmpty(text)) {
            return true;
        }
        return !text.startsWith(stripEllipsis(stripBeginning(subject)));
    }

    /**
     * Strips e.g. "Message - " or "Message:"
     */
    static String stripBeginning(String textIn) {
        if (TextUtils.isEmpty(textIn)) {
            return "";
        }
        int ind = textIn.indexOf("-");
        if (ind < 0) {
            ind = textIn.indexOf(":");
        }
        if (ind < 0) {
            return textIn;
        }
        String beginningSeparators = "-:;,.[] ";
        while ((ind < textIn.length()) && beginningSeparators.contains(String.valueOf(textIn.charAt(ind)))) {
            ind++;
        }
        if (ind >= textIn.length()) {
            return textIn;
        }
        return textIn.substring(ind);
    }

    static String stripEllipsis(String textIn) {
        if (TextUtils.isEmpty(textIn)) {
            return "";
        }
        int ind = textIn.length() - 1;
        String ellipsis = "… .";
        while (ind >= 0 && ellipsis.contains(String.valueOf(textIn.charAt(ind)))) {
            ind--;
        }
        if (ind < -1) {
            return "";
        }
        return textIn.substring(0, ind+1);
    }

    private void updateScreen() {
        MyServiceManager.setServiceAvailable();
        invalidateOptionsMenu();
        mMessageEditor.updateScreen();
        updateTitle();
    }

    private void updateTitle() {
        new TimelineTitle(mListParameters.getTimelineType() != TimelineTypeEnum.UNKNOWN ?
                mListParameters : mListParametersNew
                , mRateLimitText).updateTitle(this);
    }

    static class TimelineTitle {
        StringBuilder title = new StringBuilder();
        StringBuilder subTitle = new StringBuilder();

        public TimelineTitle(TimelineListParameters ta, String additionalTitleText) {
            buildTitle(ta); 
            buildSubtitle(ta, additionalTitleText);
        }

        private void buildTitle(TimelineListParameters ta) {
            I18n.appendWithSpace(title, ta.getTimelineType().getTitle(ta.mContext));
            if (!TextUtils.isEmpty(ta.mSearchQuery)) {
                I18n.appendWithSpace(title, "'" + ta.mSearchQuery + "'");
            }
            if (ta.getTimelineType() == TimelineTypeEnum.USER
                    && !(ta.isTimelineCombined()
                            && MyContextHolder.get().persistentAccounts()
                            .fromUserId(ta.getSelectedUserId()).isValid())) {
                I18n.appendWithSpace(title, MyProvider.userIdToWebfingerId(ta.getSelectedUserId()));
            }
            if (ta.isTimelineCombined()) {
                I18n.appendWithSpace(title, ta.mContext.getText(R.string.combined_timeline_on));
            }
        }

        private void buildSubtitle(TimelineListParameters ta, String additionalTitleText) {
            if (!ta.isTimelineCombined()) {
                I18n.appendWithSpace(subTitle, ta.getTimelineType()
                        .getPrepositionForNotCombinedTimeline(ta.mContext));
                if (ta.getTimelineType().atOrigin()) {
                    I18n.appendWithSpace(subTitle, MyContextHolder.get().persistentAccounts()
                            .fromUserId(ta.getMyAccountUserId()).getOrigin().getName()
                            + ";");
                }
            }
            I18n.appendWithSpace(subTitle, buildAccountButtonText(ta.getMyAccountUserId()));
            I18n.appendWithSpace(subTitle, additionalTitleText);
        }

        private void updateTitle(Activity activity) {
            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setSubtitle(subTitle);
            }
            if (MyLog.isLoggable(activity, MyLog.VERBOSE)) {
                MyLog.v(activity, "Title: " + toString());
            }
        }

        @Override
        public String toString() {
            return title + "; " + subTitle;
        }
    }

    /**
     * Prepare a query to the ContentProvider (to the database) and load the visible List of
     * messages with this data
     * This is done asynchronously.
     * This method should be called from UI thread only.
     * 
     * @param loadOneMorePage true - load one more page of messages, false - reload the same page
     */
    protected void queryListData(boolean loadOneMorePage) {
        final String method = "queryListData";
        if (!loadOneMorePage) {
            mNoMoreItems = false;
        }
        MyLog.v(this, method + (loadOneMorePage ? "loadOneMorePage" : ""));
        Bundle args = new Bundle();
        args.putBoolean(IntentExtra.EXTRA_LOAD_ONE_MORE_PAGE.key, loadOneMorePage);
        args.putInt(IntentExtra.EXTRA_ROWS_LIMIT.key, calcRowsLimit(loadOneMorePage));
        getLoaderManager().restartLoader(LOADER_ID, args, this);
        setLoading(method, true);
    }

    private int calcRowsLimit(boolean loadOneMorePage) {
        int nMessages = 0;
        if (getListAdapter() != null) {
            Cursor cursor = ((CursorAdapter) getListAdapter()).getCursor();
            if (cursor != null && !cursor.isClosed()) {
                nMessages = cursor.getCount();
            }
        }
        if (loadOneMorePage) {
            nMessages += TimelineListParameters.PAGE_SIZE;
        } else if (nMessages < TimelineListParameters.PAGE_SIZE) {
            nMessages = TimelineListParameters.PAGE_SIZE;
        }
        return nMessages;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle argsIn) {
        final String method = "onCreateLoader";
        Bundle args = argsIn==null ? new Bundle() : argsIn;
        MyLog.v(this, method + " #" + id);
        args.putBoolean(IntentExtra.EXTRA_POSITION_RESTORED.key, mPositionRestored
                && (getListAdapter() != null));

        TimelineListParameters params = TimelineListParameters.clone(mListParametersNew, args);
        Intent intent = getIntent();
        if (!params.mContentUri.equals(intent.getData())) {
            intent.setData(params.mContentUri);
        }
        saveSearchQuery();
        return new TimelineCursorLoader1(params);
    }

    private void saveSearchQuery() {
        if (!TextUtils.isEmpty(mListParametersNew.mSearchQuery)) {
            // Record the query string in the recent queries
            // of the Suggestion Provider
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    TimelineSearchSuggestionsProvider.AUTHORITY,
                    TimelineSearchSuggestionsProvider.MODE);
            suggestions.saveRecentQuery(mListParametersNew.mSearchQuery, null);

        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        final String method = "onLoaderReset"; 
        MyLog.v(this, method + " ; " + loader);
        if (getListAdapter() != null) {
            ((CursorAdapter) getListAdapter()).swapCursor(null);
        }
        setLoading(method, false);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        final String method = "onLoadFinished"; 
        MyLog.v(this, method);
        boolean doChangeListContent = loader.isStarted() && cursor != null && !mFinishing;
        if (doChangeListContent && !(loader instanceof TimelineCursorLoader1)) {
            MyLog.e(this, method + "; Wrong type of loader: " + MyLog.objTagToString(loader));
            doChangeListContent = false;
        }
        TimelineCursorLoader1 myLoader = null;
        if (doChangeListContent) {
            myLoader = (TimelineCursorLoader1) loader;
            doChangeListContent = !myLoader.getParams().cancelled;
        }
        if (doChangeListContent) {
            changeListContent(myLoader, cursor);
        } else {
            setLoading(method, false);
            updateScreen();
            clearNotifications();
        }
    }

    // Parameters are not null
    private void changeListContent(final TimelineCursorLoader1 myLoader, final Cursor cursor) {
        final String method = "changeListContent1"; 

        // This check will prevent continuous loading...
        mNoMoreItems = myLoader.getParams().mIncrementallyLoadingPages &&
                cursor.getCount() <= getListAdapter().getCount();
        saveListPosition();


        // This is possible from main thread only, otherwise we are getting an exception
        // The hack is to avoid the Main thread freeze: https://github.com/andstatus/andstatus/issues/183
        MySimpleCursorAdapter.beforeSwapCursor();
        ((CursorAdapter) getListAdapter()).swapCursor(cursor);
        MyLog.v(this, method + "; After changing Cursor");
        MySimpleCursorAdapter.afterSwapCursor();

        mListParameters = myLoader.getParams();
        mPositionRestored = new TimelineListPositionStorage(getListAdapter(), getListView(), mListParameters).restoreListPosition();

        boolean requestNextPage = false;
        if (!myLoader.getParams().mLoadOneMorePage && myLoader.getParams().mLastItemSentDate > 0
                && cursor != null && cursor.getCount() < TimelineListParameters.PAGE_SIZE) {
            MyLog.v(this, method + "; Requesting next page...");
            requestNextPage = true;
        }
        if (requestNextPage) {
            queryListData(true);
        } else {
            launchReloadIfNeeded(myLoader.getParams().timelineToReload);
        }
        setLoading(method, false);
        updateScreen();
        clearNotifications();
    }

    private void launchReloadIfNeeded(TimelineTypeEnum timelineToReload) {
        switch (timelineToReload) {
            case ALL:
                manualReload(true, false);
                break;
            case UNKNOWN:
                break;
            default:
                manualReload(false, false);
                break;
        }
    }

    /**
     * Ask a service to load data from the Internet for the selected TimelineType
     * Only newer messages (newer than last loaded) are being loaded from the
     * Internet, older ones are not being reloaded.
     */
    protected void manualReload(boolean allTimelineTypes, boolean manuallyLauched) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mListParametersNew.myAccountUserId);
        TimelineTypeEnum timelineTypeForReload = TimelineTypeEnum.HOME;
        long userId = 0;
        switch (mListParametersNew.getTimelineType()) {
            case DIRECT:
            case MENTIONS:
            case PUBLIC:
            case EVERYTHING:
                timelineTypeForReload = mListParametersNew.getTimelineType();
                break;
            case USER:
            case FOLLOWING_USER:
                timelineTypeForReload = mListParametersNew.getTimelineType();
                userId = mListParametersNew.mSelectedUserId;
                break;
            default:
                break;
        }
        boolean allAccounts = mListParametersNew.isTimelineCombined();
        if (userId != 0) {
            allAccounts = false;
            long originId = MyProvider.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, userId);
            if (originId == 0) {
                MyLog.e(this, "Unknown origin for userId=" + userId);
                return;
            }
            if (!ma.isValid() || ma.getOriginId() != originId) {
                ma = MyContextHolder.get().persistentAccounts().fromUserId(userId);
                if (!ma.isValid()) {
                    ma = MyContextHolder.get().persistentAccounts().findFirstSucceededMyAccountByOriginId(originId);
                }
            }
        }
        if (!allAccounts && !ma.isValid()) {
            return;
        }

        setSyncing("manualReload", true);
        MyServiceManager.sendForegroundCommand(
                (new CommandData(CommandEnum.FETCH_TIMELINE,
                        allAccounts ? "" : ma.getAccountName(), timelineTypeForReload, userId)).setManuallyLaunched(manuallyLauched)
                );

        if (allTimelineTypes && ma.isValid()) {
            ma.requestSync();
        }
    }

    protected void startMyPreferenceActivity() {
        finish();
        startActivity(new Intent(this, MySettingsActivity.class));
    }

    protected void saveActivityState() {
        SharedPreferences.Editor outState = MyPreferences.getSharedPreferences(ACTIVITY_PERSISTENCE_NAME).edit();
        mListParametersNew.saveState(outState);
        mMessageEditor.saveState(outState);
        mContextMenu.saveState(outState);
        outState.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT:
                accountSelected(data);
                break;
            case SELECT_ACCOUNT_TO_ACT_AS:
                accountToActAsSelected(data);
                break;
            case SELECT_ACCOUNT_TO_SHARE_VIA:
                accountToShareViaSelected(data);
                break;
            case ATTACH:
                attachmentSelected(data);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void accountSelected(Intent data) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
        if (ma.isValid()) {
            MyLog.v(this, "Restarting the activity for the selected account " + ma.getAccountName());
            finish();
            TimelineTypeEnum timelineTypeNew = mListParametersNew.getTimelineType();
            if (mListParametersNew.getTimelineType() == TimelineTypeEnum.USER
                    && !MyContextHolder.get().persistentAccounts()
                            .fromUserId(mListParametersNew.mSelectedUserId).isValid()) {
                /*  "Other User's timeline" vs "My User's timeline" 
                 * Actually we saw messages of the user, who is not MyAccount,
                 * so let's switch to the HOME
                 * TODO: Open "Other User's timeline" in a separate Activity?!
                 */
                timelineTypeNew = TimelineTypeEnum.HOME;
            }
            MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
            mContextMenu.switchTimelineActivity(timelineTypeNew, mListParametersNew.isTimelineCombined(), ma.getUserId());
        }
    }

    private void accountToActAsSelected(Intent data) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
        if (ma.isValid()) {
            mContextMenu.setAccountUserIdToActAs(ma.getUserId());
            mContextMenu.showContextMenu();
        }
    }

    private void accountToShareViaSelected(Intent data) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
        mMessageEditor.startEditingMessage(new MessageEditorData(ma).setMessageText(mTextToShareViaThisApp).setMediaUri(mMediaToShareViaThisApp));
    }

    private void attachmentSelected(Intent data) {
        Uri uri = UriUtils.notNull(data.getData());
        if (!UriUtils.isEmpty(uri)) {
            mMediaToShareViaThisApp = uri;
            if (mMessageEditor.isVisible()) {
                mMessageEditor.setMedia(mMediaToShareViaThisApp);
            }
        }
    }

    private void createListAdapter(Cursor cursor) {
        List<String> columnNames = new ArrayList<String>();
        List<Integer> viewIds = new ArrayList<Integer>();
        columnNames.add(MyDatabase.User.AUTHOR_NAME);
        viewIds.add(R.id.message_author);
        columnNames.add(MyDatabase.Msg.BODY);
        viewIds.add(R.id.message_body);
        columnNames.add(MyDatabase.Msg.CREATED_DATE);
        viewIds.add(R.id.message_details);
        columnNames.add(MyDatabase.MsgOfUser.FAVORITED);
        viewIds.add(R.id.message_favorited);
        columnNames.add(MyDatabase.Msg._ID);
        viewIds.add(R.id.id);
        int listItemLayoutId = R.layout.message_basic;
        if (MyPreferences.showAvatars()) {
            listItemLayoutId = R.layout.message_avatar;
            columnNames.add(MyDatabase.Download.AVATAR_FILE_NAME);
            viewIds.add(R.id.avatar_image);
        }
        if (MyPreferences.showAttachedImages()) {
            columnNames.add(MyDatabase.Download.IMAGE_ID);
            viewIds.add(R.id.attached_image);
        }
        MySimpleCursorAdapter mCursorAdapter = new MySimpleCursorAdapter(TimelineActivity.this,
                listItemLayoutId, cursor, columnNames.toArray(new String[]{}),
                toIntArray(viewIds), 0);
        mCursorAdapter.setViewBinder(new TimelineViewBinder());

        setListAdapter(mCursorAdapter);
    }

    /**
     * See http://stackoverflow.com/questions/960431/how-to-convert-listinteger-to-int-in-java
     */
    private static int[] toIntArray(List<Integer> list){
        int[] ret = new int[list.size()];
        for(int i = 0;i < ret.length;i++) {
            ret[i] = list.get(i);
        }
        return ret;
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        switch (event) {
            case BEFORE_EXECUTING_COMMAND:
                showSyncIndicator(commandData);
                break;
            case AFTER_EXECUTING_COMMAND:
                onReceiveAfterExecutingCommand(commandData);
                break;
            case ON_STOP:
                setSyncing("onReceive STOP", false);
                setSyncIndicator("onReceive STOP", false);
                break;
            default:
                break;
        }
    }
    
    private void showSyncIndicator(CommandData commandData) {
        if (!mShowSyncIndicatorOnTimeline
                || !isCommandToShowInSyncIndicator(commandData.getCommand())
                || mMessageEditor.isVisible()) {
            return;
        }
        setSyncIndicator("Before " + commandData.getCommand(), true);
        new AsyncTask<CommandData, Void, String>() {

            @Override
            protected String doInBackground(CommandData... commandData) {
                return commandData[0].toCommandSummary(MyContextHolder.get());
            }

            @Override
            protected void onPostExecute(String result) {
                String syncMessage = getText(R.string.title_preference_syncing) + ": "
                        + result;
                ((TextView) findViewById(R.id.sync_text)).setText(syncMessage);
                MyLog.v(this, syncMessage);
            }

        }.execute(commandData);
    }

    private boolean isCommandToShowInSyncIndicator(CommandEnum command) {
        switch (command) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
            case UPDATE_STATUS:
            case DESTROY_STATUS:
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case SEARCH_MESSAGE:
            case FOLLOW_USER:
            case STOP_FOLLOWING_USER:
            case REBLOG:
            case DESTROY_REBLOG:
                return true;
            default:
                return false;
        }
    }

    private void onReceiveAfterExecutingCommand(CommandData commandData) {
        switch (commandData.getCommand()) {
            case FETCH_TIMELINE:
            case SEARCH_MESSAGE:
                if (commandData.isInForeground() && !commandData.isStep()) {
                    setSyncing("After executing " + commandData.getCommand(), false);
                }
                break;
            case RATE_LIMIT_STATUS:
                if (commandData.getResult().getHourlyLimit() > 0) {
                    mRateLimitText = commandData.getResult().getRemainingHits() + "/"
                            + commandData.getResult().getHourlyLimit();
                    updateTitle();
                }
                break;
            default:
                break;
        }
        if (mShowSyncIndicatorOnTimeline
                && isCommandToShowInSyncIndicator(commandData.getCommand())) {
            ((TextView) findViewById(R.id.sync_text)).setText("");
        }
    }

    private boolean isLoading() {
        return mIsLoading;
    }

    private void setLoading(String source, boolean isLoading) {
        if (isLoading() != isLoading && !isFinishing()) {
            mIsLoading = isLoading;
            MyLog.v(this, source + " set isLoading to " + isLoading);
            mLoadingLayout.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void setSyncing(String source, boolean isSyncing) {
        if (mSwipeRefreshLayout != null 
                && mSwipeRefreshLayout.isRefreshing() != isSyncing 
                && !isFinishing()) {
            MyLog.v(this, source + " set Syncing to " + isSyncing);
             mSwipeRefreshLayout.setRefreshing(isSyncing);
        }
    }
    
    protected Menu getOptionsMenu() {
        return mOptionsMenu;
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public MessageEditor getMessageEditor() {
        return mMessageEditor;
    }

    @Override
    public void onMessageEditorVisibilityChange(boolean isVisible) {
        setSyncIndicator("onMessageEditorVisibilityChange", false);
        invalidateOptionsMenu();
    }
    
    @Override
    public long getCurrentMyAccountUserId() {
        return mListParametersNew.myAccountUserId;
    }

    @Override
    public TimelineTypeEnum getTimelineType() {
        return mListParametersNew.getTimelineType();
    }

    @Override
    public boolean isTimelineCombined() {
        return mListParametersNew.isTimelineCombined();
    }

    @Override
    public long getSelectedUserId() {
        return mListParametersNew.mSelectedUserId;
    }
}
