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

package org.andstatus.app.msg;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.MyAction;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSearchSuggestionsProvider;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.test.SelectorActivityMock;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.widget.MyBaseAdapter;
import org.andstatus.app.widget.MySwipeRefreshLayout;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivity extends LoadableListActivity implements
        ActionableMessageList, AbsListView.OnScrollListener {
    private static final int DIALOG_ID_TIMELINE_TYPE = 9;
    private static final String ACTIVITY_PERSISTENCE_NAME = TimelineActivity.class.getSimpleName();

    private MySwipeRefreshLayout mSwipeRefreshLayout = null;

    /** Parameters of currently shown Timeline */
    private TimelineListParameters mListParametersLoaded;
    private TimelineListParameters mListParametersNew;

    /**
     * For testing purposes
     */
    private long mInstanceId = 0;
    MyServiceEventsReceiver mServiceConnector;

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    private volatile boolean mFinishing = false;

    private boolean mShowSyncIndicatorOnTimeline = false;
    private View mSyncIndicator = null;

    /**
     * Time when shared preferences where changed
     */
    private long mPreferencesChangeTime = 0;

    private MessageContextMenu mContextMenu;
    private MessageEditor mMessageEditor;

    private String mTextToShareViaThisApp = "";
    private Uri mMediaToShareViaThisApp = Uri.EMPTY;

    private String mRateLimitText = "";

    DrawerLayout mDrawerLayout;
    ActionBarDrawerToggle mDrawerToggle;
    protected volatile SelectorActivityMock selectorActivityMock;

    /**
     * This method is the first of the whole application to be called 
     * when the application starts for the very first time.
     * So we may put some Application initialization code here. 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mListParametersLoaded = new TimelineListParameters(this);
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
        mLayoutId = R.layout.timeline;
        super.onCreate(savedInstanceState);

        if (HelpActivity.startFromActivity(this)) {
            return;
        }

        mListParametersNew.myAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        mServiceConnector = new MyServiceEventsReceiver(this);

        mSyncIndicator = findViewById(R.id.sync_indicator);
        mContextMenu = new MessageContextMenu(this);
        mMessageEditor = new MessageEditor(this);

        initializeSwipeRefresh();

        restoreActivityState();
        
        initializeDrawer();
        getListView().setOnScrollListener(this);

        if (savedInstanceState == null) {
            parseNewIntent(getIntent());
        }

        updateScreen();
        queryListData(WhichTimelinePage.NEW);
    }

    @Override
    public TimelineAdapter getListAdapter() {
        return (TimelineAdapter) super.getListAdapter();
    }

    @Override
    protected MyBaseAdapter newListAdapter() {
        return new TimelineAdapter(mContextMenu,
                MyPreferences.showAvatars() ? R.layout.message_avatar : R.layout.message_basic,
                getListAdapter(),
                ((TimelineLoader) getLoaded()).getPageLoaded());
    }

    private void initializeSwipeRefresh() {
        mSwipeRefreshLayout = (MySwipeRefreshLayout) findViewById(R.id.myLayoutParent);
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

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    private void restoreActivityState() {
        SharedPreferences activityState = MyPreferences.getSharedPreferences(ACTIVITY_PERSISTENCE_NAME);
        if (activityState != null) {
            if (mListParametersNew.restoreState(activityState)) {
                mContextMenu.loadState(activityState);
            }
        }
    }

    /**
     * View.OnClickListener
     */
    public void onCombinedTimelineToggleClick(View item) {
        closeDrawer();
        boolean on = !isTimelineCombined();
        MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, on).apply();
        mContextMenu.switchTimelineActivity(mListParametersNew.getTimelineType(), on, mListParametersNew.myAccountUserId);
    }

    private void closeDrawer() {
        ViewGroup mDrawerList = (ViewGroup) findViewById(R.id.navigation_drawer);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    /**
     * View.OnClickListener
     */
    public void onTimelineTypeButtonClick(View item) {
        showDialog(DIALOG_ID_TIMELINE_TYPE);
        closeDrawer();
    }

    /**
     * View.OnClickListener
     */
    public void onSelectAccountButtonClick(View item) {
        if (MyContextHolder.get().persistentAccounts().size() > 1) {
            AccountSelector.selectAccount(TimelineActivity.this, 0, ActivityRequestCode.SELECT_ACCOUNT);
        }
        closeDrawer();
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/search/search-dialog.html">Creating 
     * a Search Interface</a>
     */
    @Override
    public boolean onSearchRequested() {
        onSearchRequested(false);
        return true;
    }

    private void onSearchRequested(boolean appGlobalSearch) {
        final String method = "onSearchRequested";
        Bundle appSearchData = new Bundle();
        appSearchData.putString(IntentExtra.TIMELINE_URI.key, 
                mListParametersNew.getTimelineUri(appGlobalSearch).toString());
        appSearchData.putBoolean(IntentExtra.GLOBAL_SEARCH.key, appGlobalSearch);
        MyLog.v(this, method + ": " + appSearchData);
        startSearch(null, false, appSearchData, false);
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
            mMessageEditor.loadCurrentDraft();
        }
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
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; instanceId=" + mInstanceId);
        }
        mServiceConnector.unregisterReceiver(this);
        hideSyncIndicator(method);
        mMessageEditor.saveAsBeingEditedAndHide();
        saveActivityState();
        super.onPause();

        if (isPositionRestored()) {
            getListView().setFastScrollEnabled(false);
            if (!isLoading()) {
                saveListPosition();
            }
            getListAdapter().setPositionRestored(false);
        }

        MyContextHolder.get().setInForeground(false);
    }

    private void showLoadingIndicator() {
        final String method = "showLoading";
        if (mSyncIndicator.getVisibility() != View.VISIBLE) {
            ((TextView) findViewById(R.id.sync_text)).setText(getText(R.string.loading));
            showHideSyncIndicator(method, true);
        }
    }

    private void hideSyncIndicatorIfNotLoading(String source) {
        showHideSyncIndicator(source, isLoading());
    }

    private void hideSyncIndicator(String source) {
        showHideSyncIndicator(source, false);
    }

    private void showHideSyncIndicator(String source, boolean isVisibleIn) {
        boolean isVisible = isVisibleIn;
        if (isVisible) {
            isVisible = !getMessageEditor().isVisible();
        }
        if (isVisible ? (mSyncIndicator.getVisibility() != View.VISIBLE) : ((mSyncIndicator.getVisibility() == View.VISIBLE))) {
            MyLog.v(this, source + " set Sync indicator to " + isVisible);
            mSyncIndicator.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
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
        MyLog.v(this, "onDestroy, instanceId=" + mInstanceId);
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
        saveListPosition();
        super.finish();
    }

    /**
     * May be executed on any thread
     * That advice doesn't fit here:
     * see http://stackoverflow.com/questions/5996885/how-to-wait-for-android-runonuithread-to-be-finished
     */
    protected void saveListPosition() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (isPositionRestored()) {
                    new TimelineListPositionStorage(getListAdapter(), getListView(), mListParametersLoaded).save();
                }
            }
        };
        runOnUiThread(runnable);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ID_TIMELINE_TYPE:
                return newTimelineTypeSelector();
            default:
                break;
        }
        return super.onCreateDialog(id);
    }

    // TODO: Replace this with http://developer.android.com/reference/android/app/DialogFragment.html
    private AlertDialog newTimelineTypeSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_select_timeline);
        final TimelineTypeSelector selector = new TimelineTypeSelector(this);
        builder.setItems(selector.getTitles(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The 'which' argument contains the index position of the
                // selected item
                TimelineType type = selector.positionToType(which);
                if (type != TimelineType.UNKNOWN) {
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
        if (mMessageEditor != null) {
            mMessageEditor.onCreateOptionsMenu(menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        boolean enableReload = isTimelineCombined() || ma.isValidAndSucceeded();
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

    public void onItemClick(TimelineViewItem item) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().getAccountForThisMessage(item.originId,
                item.msgId, item.linkedUserId,
                mListParametersNew.myAccountUserId, false);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,
                    "onItemClick, " + item
                            + "; " + item
                            + " account=" + ma.getAccountName());
        }
        if (item.msgId <= 0) {
            return;
        }
        Uri uri = MatchedUri.getTimelineItemUri(ma.getUserId(),
                mListParametersNew.getTimelineType(),
                mListParametersNew.isTimelineCombined(),
                mListParametersNew.getSelectedUserId(), item.msgId);

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
            startActivity(MyAction.VIEW_CONVERSATION.getIntent(uri));
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        switch (scrollState) {
            case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
                break;
            case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                break;
            case AbsListView.OnScrollListener.SCROLL_STATE_FLING:
                getListView().setFastScrollEnabled(true);
                break;
            default:
                break;
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                         int totalItemCount) {
        // Idea from http://stackoverflow.com/questions/1080811/android-endless-list
        boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                && (firstVisibleItem + visibleItemCount >= totalItemCount);
        if (loadMore && getListAdapter().getPages().mayHaveOlderPage()) {
            MyLog.d(this, "Start Loading older items, rows=" + totalItemCount);
            queryListData(WhichTimelinePage.OLDER);
        }
    }

    private String timelineTypeButtonText() {
        CharSequence timelineName = mListParametersNew.getTimelineType().getTitle(this);
        return timelineName + (TextUtils.isEmpty(mListParametersNew.mSearchQuery) ? "" : " *");
    }

    private void updateAccountButtonText(ViewGroup mDrawerList) {
        TextView selectAccountButton = (TextView) mDrawerList.findViewById(R.id.selectAccountButton);
        String accountButtonText = buildAccountButtonText(mListParametersNew.myAccountUserId);
        selectAccountButton.setText(accountButtonText);
    }

    public static String buildAccountButtonText(long myAccountUserId) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(myAccountUserId);
        String accountButtonText = ma.shortestUniqueAccountName();
        if (!ma.isValidAndSucceeded()) {
            accountButtonText = "(" + accountButtonText + ")";
        }
        return accountButtonText;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (MyLog.isVerboseEnabled()) {
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
        queryListData(WhichTimelinePage.NEW);
    }

    private void parseNewIntent(Intent intentNew) {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "parseNewIntent:" + intentNew);
        }
        mRateLimitText = "";
        mListParametersNew.setTimelineType(TimelineType.UNKNOWN);
        mListParametersNew.myAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        mListParametersNew.mSelectedUserId = 0;
        parseAppSearchData(intentNew);
        if (mListParametersNew.getTimelineType() == TimelineType.UNKNOWN) {
            mListParametersNew.parseIntentData(intentNew);
        }
        if (mListParametersNew.getTimelineType() == TimelineType.UNKNOWN) {
            /* Set default values */
            mListParametersNew.setTimelineType(TimelineType.HOME);
            mListParametersNew.mSearchQuery = "";
        }
        if (mListParametersNew.getTimelineType() == TimelineType.USER) {
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
        if (appSearchData == null
                || !mListParametersNew.parseUri(Uri.parse(appSearchData.getString(
                        IntentExtra.TIMELINE_URI.key, "")))) {
            return;
        }
        /* The query itself is still from the Intent */
        mListParametersNew.mSearchQuery = TimelineListParameters.notNullString(intentNew.getStringExtra(SearchManager.QUERY));
        if (!TextUtils.isEmpty(mListParametersNew.mSearchQuery)
                && appSearchData.getBoolean(IntentExtra.GLOBAL_SEARCH.key, false)) {
            setRefreshing("Global search: " + mListParametersNew.mSearchQuery, true);
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.searchCommand(
                            isTimelineCombined()
                                    ? ""
                                    : MyContextHolder.get().persistentAccounts()
                                    .getCurrentAccountName(),
                            mListParametersNew.mSearchQuery));
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
        updateTitle(mRateLimitText);
    }

    @Override
    protected void updateTitle(String additionalTitleText) {
        new TimelineTitle(mListParametersLoaded.getTimelineType() == TimelineType.UNKNOWN ?
                mListParametersNew : mListParametersLoaded,
                additionalTitleText).updateTitle(this);
    }

    MessageContextMenu getContextMenu() {
        return mContextMenu;
    }

    static class TimelineTitle {
        final StringBuilder title = new StringBuilder();
        final StringBuilder subTitle = new StringBuilder();

        public TimelineTitle(TimelineListParameters ta, String additionalTitleText) {
            buildTitle(ta); 
            buildSubtitle(ta, additionalTitleText);
        }

        private void buildTitle(TimelineListParameters ta) {
            I18n.appendWithSpace(title, ta.getTimelineType().getTitle(ta.mContext));
            if (!TextUtils.isEmpty(ta.mSearchQuery)) {
                I18n.appendWithSpace(title, "'" + ta.mSearchQuery + "'");
            }
            if (ta.getTimelineType() == TimelineType.USER
                    && !(ta.isTimelineCombined()
                            && MyContextHolder.get().persistentAccounts()
                            .fromUserId(ta.getSelectedUserId()).isValid())) {
                I18n.appendWithSpace(title, MyQuery.userIdToWebfingerId(ta.getSelectedUserId()));
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

        private void updateTitle(AppCompatActivity activity) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setSubtitle(subTitle);
            }
            if (MyLog.isVerboseEnabled()) {
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
     */
    protected void queryListData(WhichTimelinePage whichPage) {
        final String method = "queryListData";
        if (!isLoading()) {
            saveListPosition();
            MyLog.v(this, method + " " + whichPage);
            showList(whichPage.save(new Bundle()));
            showLoadingIndicator();
        }
    }

    @Override
    protected SyncLoader newSyncLoader(Bundle argsIn) {
        final String method = "newSyncLoader";
        WhichTimelinePage whichPage = WhichTimelinePage.load(argsIn);
        TimelineListParameters params = TimelineListParameters.clone(
                getPrevParametersFor(whichPage), whichPage);
        if (whichPage != WhichTimelinePage.EMPTY) {
            MyLog.v(this, method + ": " + params);
            Intent intent = getIntent();
            if (!params.mContentUri.equals(intent.getData())) {
                intent.setData(params.mContentUri);
            }
            saveSearchQuery();
        }
        return new TimelineLoader(params);
    }

    private TimelineListParameters getPrevParametersFor(WhichTimelinePage whichPage) {
        TimelineAdapter adapter = getListAdapter();
        if (whichPage == WhichTimelinePage.NEW
                || whichPage == WhichTimelinePage.EMPTY
                || whichPage == WhichTimelinePage.SAME && mListParametersLoaded == null
                || adapter == null
                || adapter.getPages().getItemsCount() == 0) {
            return mListParametersNew == null ? new TimelineListParameters(this)
                    : mListParametersNew;
        }
        switch (whichPage) {
            case SAME:
                return mListParametersLoaded;
            case OLDER:
                return adapter.getPages().list.get(adapter.getPages().list.size()-1).parameters;
            case YOUNGER:
            default:
                return adapter.getPages().list.get(0).parameters;
        }
    }

    protected void restoreListPosition(TimelineListParameters mListParameters) {
        getListAdapter().setPositionRestored(
                new TimelineListPositionStorage(getListAdapter(), getListView(), mListParameters).restore());
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
    public void onLoadFinished() {
        final String method = "onLoadFinished"; 
        MyLog.v(this, method);
        super.onLoadFinished();

        TimelineLoader myLoader = (TimelineLoader) getLoaded();
        mListParametersLoaded = myLoader.getParams();
        if (!isPositionRestored()) {
            restoreListPosition(mListParametersLoaded);
        }

        if (myLoader.size() == 0) {
            WhichTimelinePage anotherPageToRequest = WhichTimelinePage.SAME;
            TimelineAdapter adapter = getListAdapter();
            if (adapter.getPages().mayHaveYoungerPage()) {
                anotherPageToRequest = WhichTimelinePage.YOUNGER;
            } else if (adapter.getPages().mayHaveOlderPage()) {
                anotherPageToRequest = WhichTimelinePage.OLDER;
            } else if (mListParametersLoaded.whichPage != WhichTimelinePage.YOUNGEST) {
                anotherPageToRequest = WhichTimelinePage.YOUNGEST;
            } else {
                if (mListParametersLoaded.rowsLoaded == 0) {
                    launchReloadIfNeeded(mListParametersLoaded.timelineToReload);
                }
            }
            if (anotherPageToRequest != WhichTimelinePage.SAME) {
                MyLog.v(this, method + "; Nothing loaded, requesting " + anotherPageToRequest + " page...");
                queryListData(anotherPageToRequest);
            }
        }

        hideSyncIndicator(method);
        updateScreen();
        clearNotifications();
    }

    private void launchReloadIfNeeded(TimelineType timelineToReload) {
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
    protected void manualReload(boolean allTimelineTypes, boolean manuallyLaunched) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mListParametersNew.myAccountUserId);
        TimelineType timelineTypeForReload = TimelineType.HOME;
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
            long originId = MyQuery.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, userId);
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

        setRefreshing("manualReload", true);
        MyServiceManager.sendForegroundCommand(
                (new CommandData(CommandEnum.FETCH_TIMELINE,
                        allAccounts ? "" : ma.getAccountName(), timelineTypeForReload, userId)).setManuallyLaunched(manuallyLaunched)
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
        mContextMenu.saveState(outState);
        outState.apply();

        final String CRASH_TEST_STRING = "Crash test 2015-04-10";
        if (MyLog.isVerboseEnabled() && mMessageEditor != null &&
                    mMessageEditor.getData().body.contains(CRASH_TEST_STRING)) {
            MyLog.e(this, "Initiating crash test exception");
            throw new NullPointerException("This is a test crash event");
        }

    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (selectorActivityMock != null) {
            selectorActivityMock.startActivityForResult(intent, requestCode);
        } else {
            super.startActivityForResult(intent, requestCode);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MyLog.v(this, "onActivityResult; request:" + requestCode + ", result:" + (resultCode == RESULT_OK ? "ok" : "fail"));
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
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        if (ma.isValid()) {
            MyLog.v(this, "Restarting the activity for the selected account " + ma.getAccountName());
            finish();
            TimelineType timelineTypeNew = mListParametersNew.getTimelineType();
            if (mListParametersNew.getTimelineType() == TimelineType.USER
                    && !MyContextHolder.get().persistentAccounts()
                            .fromUserId(mListParametersNew.mSelectedUserId).isValid()) {
                /*  "Other User's timeline" vs "My User's timeline" 
                 * Actually we saw messages of the user, who is not MyAccount,
                 * so let's switch to the HOME
                 * TODO: Open "Other User's timeline" in a separate Activity?!
                 */
                timelineTypeNew = TimelineType.HOME;
            }
            MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
            mContextMenu.switchTimelineActivity(timelineTypeNew, mListParametersNew.isTimelineCombined(), ma.getUserId());
        }
    }

    private void accountToActAsSelected(Intent data) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        if (ma.isValid()) {
            mContextMenu.setAccountUserIdToActAs(ma.getUserId());
            mContextMenu.showContextMenu();
        }
    }

    private void accountToShareViaSelected(Intent data) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        mMessageEditor.startEditingSharedData(ma, mTextToShareViaThisApp, mMediaToShareViaThisApp);
    }

    private void attachmentSelected(Intent data) {
        Uri uri = UriUtils.notNull(data.getData());
        if (!UriUtils.isEmpty(uri)) {
            UriUtils.takePersistableUriPermission(getActivity(), uri, data.getFlags());
            mMessageEditor.startEditingCurrentWithAttachedMedia(uri);
        }
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
                setRefreshing("onReceive STOP", false);
                hideSyncIndicatorIfNotLoading("onReceive STOP");
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
        showHideSyncIndicator("Before " + commandData.getCommand(), true);
        new AsyncTask<CommandData, Void, String>() {

            @Override
            protected String doInBackground(CommandData... commandData) {
                return commandData[0].toCommandSummary(MyContextHolder.get());
            }

            @Override
            protected void onPostExecute(String result) {
                String syncMessage = getText(R.string.title_preference_syncing) + ": " + result;
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

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        if (super.canSwipeRefreshChildScrollUp()) {
            return true;
        }
        if (getListAdapter().getPages().mayHaveYoungerPage()) {
            queryListData(WhichTimelinePage.YOUNGER);
            return true;
        }
        return false;
    }

    protected void onReceiveAfterExecutingCommand(CommandData commandData) {
        switch (commandData.getCommand()) {
            case FETCH_TIMELINE:
            case SEARCH_MESSAGE:
                if (commandData.isInForeground() && !commandData.isStep()) {
                    setRefreshing("After executing " + commandData.getCommand(), false);
                }
                break;
            case RATE_LIMIT_STATUS:
                if (commandData.getResult().getHourlyLimit() > 0) {
                    mRateLimitText = commandData.getResult().getRemainingHits() + "/"
                            + commandData.getResult().getHourlyLimit();
                    updateTitle(mRateLimitText);
                }
                break;
            case UPDATE_STATUS:
                mMessageEditor.loadCurrentDraft();
                break;
            default:
                break;
        }
        if (isYoungestPageRefreshNeeded(commandData)) {
            queryListData(WhichTimelinePage.YOUNGEST);
        }
        if (mShowSyncIndicatorOnTimeline
                && isCommandToShowInSyncIndicator(commandData.getCommand())) {
            ((TextView) findViewById(R.id.sync_text)).setText("");
        }
    }

    private void setRefreshing(String source, boolean isRefreshing) {
        if (mSwipeRefreshLayout != null 
                && mSwipeRefreshLayout.isRefreshing() != isRefreshing
                && !isFinishing()) {
            MyLog.v(this, source + " set Syncing to " + isRefreshing);
             mSwipeRefreshLayout.setRefreshing(isRefreshing);
        }
    }

    public boolean isYoungestPageRefreshNeeded(CommandData commandData) {
        boolean changed = false;
        switch (commandData.getCommand()) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
                if (mListParametersLoaded == null
                        || mListParametersLoaded.getTimelineType() != commandData.getTimelineType()) {
                    break;
                }
            case GET_STATUS:
            case SEARCH_MESSAGE:
                if (commandData.getResult().getDownloadedCount() > 0) {
                    changed = true;
                }
                break;
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case DESTROY_REBLOG:
            case DESTROY_STATUS:
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
            case REBLOG:
            case UPDATE_STATUS:
                if (!commandData.getResult().hasError()) {
                    changed = true;
                }
                break;
            default:
                break;
        }
        if (changed) {
            TimelineAdapter adapter = getListAdapter();
            if (adapter == null || adapter.getPages().mayHaveYoungerPage()) {
                // Show updates only if we are on the top of a timeline
                changed = false;
            }
        }
        if (changed && isLoading()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Ignoring content change while loading, " + commandData.toString());
            }
            changed = false;
        }
        if (changed && MyLog.isVerboseEnabled()) {
            MyLog.v(this, "Content changed, " + commandData.toString());
        }
        return changed;
    }

    @Override
    public LoadableListActivity getActivity() {
        return this;
    }

    @Override
    public MessageEditor getMessageEditor() {
        return mMessageEditor;
    }

    @Override
    public void onMessageEditorVisibilityChange() {
        hideSyncIndicatorIfNotLoading("onMessageEditorVisibilityChange");
        invalidateOptionsMenu();
    }
    
    @Override
    public long getCurrentMyAccountUserId() {
        return mListParametersNew.myAccountUserId;
    }

    @Override
    public TimelineType getTimelineType() {
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
