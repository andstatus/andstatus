/*
 * Copyright (c) 2011-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.FirstActivity;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyAction;
import org.andstatus.app.R;
import org.andstatus.app.SyncLoader;
import org.andstatus.app.WhichPage;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.test.SelectorActivityMock;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineList;
import org.andstatus.app.timeline.TimelineSelector;
import org.andstatus.app.timeline.TimelineTitle;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.ViewUtils;
import org.andstatus.app.widget.MyBaseAdapter;
import org.andstatus.app.widget.MySearchView;

import java.util.Collections;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivity extends MessageEditorListActivity implements
        MessageListContextMenuContainer, AbsListView.OnScrollListener {
    public static final String HORIZONTAL_ELLIPSIS = "\u2026";

    /** Parameters for the next page request, not necessarily requested already */
    private volatile TimelineListParameters paramsNew = null;
    /** Last parameters, requested to load. Thread safe. They are taken by a Loader at some time */
    private volatile TimelineListParameters paramsToLoad;
    private volatile TimelineData listData;

    private MessageContextMenu contextMenu;

    private String mTextToShareViaThisApp = "";
    private Uri mMediaToShareViaThisApp = Uri.EMPTY;

    private String mRateLimitText = "";

    DrawerLayout mDrawerLayout;
    ActionBarDrawerToggle mDrawerToggle;
    protected volatile SelectorActivityMock selectorActivityMock;
    View syncYoungerView = null;
    View syncOlderView = null;

    public static void startForTimeline(MyContext myContext, Context context, Timeline timeline,
                                        MyAccount newCurrentMyAccount, boolean clearTask) {
        Intent intent = getIntentForTimeline(myContext, timeline, newCurrentMyAccount, clearTask);
        context.startActivity(intent);
    }

    @NonNull
    private static Intent getIntentForTimeline(MyContext myContext, Timeline timeline, MyAccount newCurrentMyAccount, boolean clearTask) {
        if (newCurrentMyAccount != null && newCurrentMyAccount.isValid()) {
            myContext.persistentAccounts().setCurrentAccount(newCurrentMyAccount);
        }
        Intent intent = new Intent(myContext.context(), clearTask ? FirstActivity.class : TimelineActivity.class);
        intent.setData(MatchedUri.getTimelineUri(timeline));
        if (clearTask) {
            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    public static void goHome(Activity activity) {
        try {
            Intent intent = new Intent(activity, FirstActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            MyLog.v(TimelineActivity.class, "goHome", e);
            MyContextHolder.get().context().startActivity(
                    new Intent(MyContextHolder.get().context(), FirstActivity.class));
        }
    }

    @Override
    public void onRefresh() {
        syncWithInternet(getParamsLoaded().getTimeline(), true, true);
    }

    /**
     * This method is the first of the whole application to be called 
     * when the application starts for the very first time.
     * So we may put some Application initialization code here. 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.timeline;
        super.onCreate(savedInstanceState);
        showSyncIndicatorSetting = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SYNC_INDICATOR_ON_TIMELINE, true);
        if (isFinishing()) {
            return;
        }

        getParamsNew().setTimeline(myContext.persistentTimelines().getDefault());
        contextMenu = new MessageContextMenu(this);

        initializeDrawer();
        getListView().setOnScrollListener(this);

        View view = findViewById(R.id.my_action_bar);
        if (view != null) {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onTimelineTitleClick(v);
                }
            });
        }

        syncYoungerView = View.inflate(this, R.layout.sync_younger, null);
        syncYoungerView.findViewById(R.id.sync_younger_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncWithInternet(getParamsLoaded().getTimeline(), true, true);
            }
        });

        syncOlderView = View.inflate(this, R.layout.sync_older, null);
        syncOlderView.findViewById(R.id.sync_older_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncWithInternet(getParamsLoaded().getTimeline(), false, true);
            }
        });
        addSyncButtons();

        searchView = (MySearchView) findViewById(R.id.my_search_view);
        searchView.initialize(this);

        if (savedInstanceState != null) {
            restoreActivityState(savedInstanceState);
        } else {
            parseNewIntent(getIntent());
        }
    }

    @Override
    public TimelineAdapter getListAdapter() {
        return (TimelineAdapter) super.getListAdapter();
    }

    @Override
    protected MyBaseAdapter newListAdapter() {
        return new TimelineAdapter(contextMenu, getListData());
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

        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.setHomeAsUpIndicator(MyPreferences.getActionBarTextHomeIconResourceId());
    }

    private void restoreActivityState(@NonNull Bundle savedInstanceState) {
            if (getParamsNew().restoreState(savedInstanceState)) {
                contextMenu.loadState(savedInstanceState);
            }
            getListData().collapseDuplicates(savedInstanceState.getBoolean(
                    IntentExtra.COLLAPSE_DUPLICATES.key, MyPreferences.isCollapseDuplicates()), 0);
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "restoreActivityState; " + getParamsNew());
            }
    }

    /**
     * View.OnClickListener
     */
    public void onTimelineTitleClick(View item) {
        switch (MyPreferences.getTapOnATimelineTitleBehaviour()) {
            case SWITCH_TO_DEFAULT_TIMELINE:
                if (getParamsLoaded().isAtHome()) {
                    onTimelineTypeButtonClick(item);
                } else {
                    goHome(this);
                }
                break;
            case GO_TO_THE_TOP:
                onGoToTheTopButtonClick(item);
                break;
            case SELECT_TIMELINE:
                onTimelineTypeButtonClick(item);
                break;
            default:
                break;
        }
    }

    /**
     * View.OnClickListener
     */
    public void onSwitchToDefaultTimelineButtonClick(View item) {
        closeDrawer();
        goHome(this);
    }

    /**
     * View.OnClickListener
     */
    public void onGoToTheTopButtonClick(View item) {
        closeDrawer();
        if (getListData().mayHaveYoungerPage()) {
            showList(WhichPage.TOP);
        } else {
            TimelineListPositionStorage.setPosition(getListView(), 0);
        }
    }

    /**
     * View.OnClickListener
     */
    public void onRefreshButtonClick(View item) {
        closeDrawer();
        if (getListData().mayHaveYoungerPage() || getListView().getLastVisiblePosition() > TimelineListParameters.PAGE_SIZE / 2) {
            showList(WhichPage.CURRENT);
        } else {
            showList(WhichPage.YOUNGEST);
        }
    }

    /**
     * View.OnClickListener
     */
    public void onCombinedTimelineToggleClick(View item) {
        closeDrawer();
        switchView( getParamsLoaded().getTimeline().fromIsCombined(myContext, !getParamsLoaded().isTimelineCombined()),
                null);

    }

    private void closeDrawer() {
        ViewGroup mDrawerList = (ViewGroup) findViewById(R.id.navigation_drawer);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    public void onCollapseDuplicatesToggleClick(View view) {
        closeDrawer();
        updateList(TriState.fromBoolean(((CheckBox) view).isChecked()), 0, false);
    }

    /** View.OnClickListener */
    public void onTimelineTypeButtonClick(View item) {
        TimelineSelector.selectTimeline(this, ActivityRequestCode.SELECT_TIMELINE,
                getParamsNew().getTimeline(), getCurrentMyAccount());
        closeDrawer();
    }

    /** View.OnClickListener */
    public void onSelectAccountButtonClick(View item) {
        if (myContext.persistentAccounts().size() > 1) {
            AccountSelector.selectAccount(TimelineActivity.this, ActivityRequestCode.SELECT_ACCOUNT, 0);
        }
        closeDrawer();
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/search/search-dialog.html">Creating 
     * a Search Interface</a>
     */
    @Override
    public boolean onSearchRequested() {
        if (searchView != null) {
            searchView.showSearchView(getParamsLoaded().getTimeline());
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        String method = "onResume";
        if (!mFinishing) {
            if (getCurrentMyAccount().isValid()) {
                if (isConfigChanged()) {
                    MyLog.v(this, method + "; Restarting this Activity to apply all new changes of configuration");
                    finish();
                    MyContextHolder.setExpiredIfConfigChanged();
                    switchView(getParamsLoaded().getTimeline(), null);
                }
            } else { 
                MyLog.v(this, method + "; Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        final String method = "onPause";
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method);
        }
        hideLoading(method);
        hideSyncing(method);
        crashTest();
        saveListPosition();
        myContext.persistentTimelines().saveChanged();
        super.onPause();
    }

    /**
     * Cancel notifications of loading timeline, which were set during Timeline downloading
     */
    private void clearNotifications() {
        myContext.clearNotification(getParamsLoaded().getTimelineType());
        MyServiceManager.sendForegroundCommand(
                CommandData.newAccountCommand(CommandEnum.CLEAR_NOTIFICATIONS, getParamsNew().getMyAccount()));
    }

    /**
     * May be executed on any thread
     * That advice doesn't fit here:
     * see http://stackoverflow.com/questions/5996885/how-to-wait-for-android-runonuithread-to-be-finished
     */
    protected void saveListPosition() {
        if (getParamsLoaded().isLoaded() && isPositionRestored()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                        new TimelineListPositionStorage(getListAdapter(), getListView(),
                                getParamsLoaded()).save();
                    }
            };
            runOnUiThread(runnable);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        contextMenu.onContextItemSelected(item);
        return super.onContextItemSelected(item);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.create_message, menu);
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.timeline, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MyAccount ma = myContext.persistentAccounts().getCurrentAccount();
        boolean enableSync = (getParamsLoaded().getTimeline().isCombined() || ma.isValidAndSucceeded())
                && getParamsLoaded().getTimeline().isSynableSomehow();
        MenuItem item = menu.findItem(R.id.sync_menu_item);
        if (item != null) {
            item.setEnabled(enableSync);
            item.setVisible(enableSync);
        }

        prepareDrawer();

        if (contextMenu != null) {
            contextMenu.setMyActor(MyAccount.EMPTY);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void prepareDrawer() {
        View drawerView = findViewById(R.id.navigation_drawer);
        if (drawerView == null) {
            return;
        }
        TextView item = (TextView) drawerView.findViewById(R.id.timelineTypeButton);
        if (item !=  null) {
            item.setText(timelineTypeButtonText());
        }
        prepareCombinedTimelineToggle(drawerView);
        updateAccountButtonText(drawerView);
    }

    private void prepareCombinedTimelineToggle(View drawerView) {
        if (ViewUtils.showView(drawerView, R.id.combinedTimelineToggle,
                // Show the "Combined" toggle even for one account to see messages,
                // which are not on the timeline.
                // E.g. messages by users, downloaded on demand.
                getParamsNew().getSelectedUserId() == 0 ||
                        getParamsNew().getSelectedUserId() == getParamsNew().getMyAccount().getUserId())) {
            MyCheckBox.setEnabled(drawerView, R.id.combinedTimelineToggle,
                    getParamsLoaded().getTimeline().isCombined());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    mDrawerLayout.closeDrawer(Gravity.LEFT);
                } else {
                    mDrawerLayout.openDrawer(Gravity.LEFT);
                }
                break;
            case R.id.search_menu_id:
                onSearchRequested();
                break;
            case R.id.sync_menu_item:
                syncWithInternet(getParamsLoaded().getTimeline(), true, true);
                break;
            case R.id.commands_queue_id:
                startActivity(new Intent(getActivity(), QueueViewer.class));
                break;
            case R.id.manage_timelines:
                startActivity(new Intent(getActivity(), TimelineList.class));
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
        HelpActivity.startMe(this, false, HelpActivity.PAGE_CHANGELOG);
    }

    public void onItemClick(TimelineViewItem item) {
        MyAccount ma = myContext.persistentAccounts().getAccountForThisMessage(item.getOriginId(),
                item.getLinkedMyAccount(), getParamsNew().getMyAccount(), false);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,
                    "onItemClick, " + item
                            + "; " + item
                            + " account=" + ma.getAccountName());
        }
        if (item.getMsgId() <= 0) {
            return;
        }
        Uri uri = MatchedUri.getTimelineItemUri(
                Timeline.getTimeline(TimelineType.EVERYTHING, null, 0,
                        myContext.persistentOrigins().fromId(item.getOriginId())),
                item.getMsgId());

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
        // Empty
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                         int totalItemCount) {
        boolean up = false;
        if (firstVisibleItem == 0) {
            View v = getListView().getChildAt(0);
            int offset = (v == null) ? 0 : v.getTop();
            up = offset == 0;
            if (up && getListData().mayHaveYoungerPage()) {
                showList(WhichPage.YOUNGER);
            }
        }
        // Idea from http://stackoverflow.com/questions/1080811/android-endless-list
        if ( !up && (visibleItemCount > 0)
                && (firstVisibleItem + visibleItemCount >= totalItemCount - 1)
                && getListData().mayHaveOlderPage()) {
            MyLog.d(this, "Start Loading older items, rows=" + totalItemCount);
            showList(WhichPage.OLDER);
        }
    }

    private String timelineTypeButtonText() {
        return TimelineTitle.load(myContext, getParamsLoaded().getTimeline(), getCurrentMyAccount()).title;
    }

    private void updateAccountButtonText(View drawerView) {
        TextView textView = (TextView) drawerView.findViewById(R.id.selectAccountButton);
        if (textView == null) {
            return;
        }
        String accountButtonText = getCurrentMyAccount().toAccountButtonText(myContext);
        textView.setText(accountButtonText);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (mFinishing) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "onNewIntent, Is finishing");
            }
            finish();
            return;
        }
        if (!myContext.isReady()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "onNewIntent, context is " + myContext.state());
            }
            finish();
            this.startActivity(intent);
            return;
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "onNewIntent");
        }
        super.onNewIntent(intent);
        parseNewIntent(intent);
		if (isResumedMy() || getListData().size() > 0 || isLoading()) {
            showList(getParamsNew().whichPage);
		}
    }

    private void parseNewIntent(Intent intentNew) {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "parseNewIntent");
        }
        mRateLimitText = "";
        getParamsNew().whichPage = WhichPage.load(
                intentNew.getStringExtra(IntentExtra.WHICH_PAGE.key), WhichPage.CURRENT);
        String searchQuery = intentNew.getStringExtra(IntentExtra.SEARCH_QUERY.key);
        if (!getParamsNew().parseUri(intentNew.getData(), searchQuery)) {
            getParamsNew().setTimeline(myContext.persistentTimelines().getDefault());
        }
        setCurrentMyAccount(getParamsNew().getTimeline().getMyAccount(), getParamsNew().getTimeline().getOrigin());

        if (Intent.ACTION_SEND.equals(intentNew.getAction())) {
            shareViaThisApplication(intentNew.getStringExtra(Intent.EXTRA_SUBJECT),
                    intentNew.getStringExtra(Intent.EXTRA_TEXT),
                    (Uri) intentNew.getParcelableExtra(Intent.EXTRA_STREAM));
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
        AccountSelector.selectAccount(this, ActivityRequestCode.SELECT_ACCOUNT_TO_SHARE_VIA, 0);
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
        getMessageEditor().updateScreen();
        updateTitle(mRateLimitText);
        mDrawerToggle.setDrawerIndicatorEnabled(!getParamsLoaded().isAtHome());
        ViewUtils.showView(
                findViewById(R.id.switchToDefaultTimelineButton), !getParamsLoaded().isAtHome());
        MyCheckBox.setEnabled(this, R.id.collapseDuplicatesToggle,
                getListData().isCollapseDuplicates());
        showSyncListButtons();
    }

    @Override
    protected void updateTitle(String additionalTitleText) {
        TimelineTitle.load(myContext, getParamsLoaded().timeline, getCurrentMyAccount()).
                updateActivityTitle(this, additionalTitleText);
    }

    MessageContextMenu getContextMenu() {
        return contextMenu;
    }

    /** Parameters of currently shown Timeline */
    @NonNull
    private TimelineListParameters getParamsLoaded() {
        return getListData().params;
    }

    @Override
    @NonNull
    public TimelineData getListData() {
        if (listData == null) {
            listData = new TimelineData(null, new TimelinePage(
                    getParamsNew(), Collections.<TimelineViewItem>emptyList()
            ));
        }
        return listData;
    }

    private TimelineData setAndGetListData(TimelinePage pageLoaded) {
        TimelineData dataNew = new TimelineData(listData, pageLoaded);
        listData = dataNew;
        TimelineAdapter listAdapter = getListAdapter();
        if (listAdapter != null) {
            // Old value of listData is modified also
            listAdapter.notifyDataSetChanged();
        }
        return dataNew;
    }

    @Override
    public void showList(WhichPage whichPage) {
        showList(whichPage, TriState.FALSE);
    }

    protected void showList(WhichPage whichPage, TriState chainedRequest) {
        showList(TimelineListParameters.clone(getReferenceParametersFor(whichPage), whichPage),
                chainedRequest);
    }

    @NonNull
    private TimelineListParameters getReferenceParametersFor(WhichPage whichPage) {
        switch (whichPage) {
            case OLDER:
                if (getListData().size() > 0) {
                    return getListData().pages.get(getListData().pages.size()-1).params;
                }
                return getParamsLoaded();
            case YOUNGER:
                if (getListData().size() > 0) {
                    return getListData().pages.get(0).params;
                }
                return getParamsLoaded();
            case EMPTY:
                return new TimelineListParameters(myContext);
            default:
                return getParamsNew();
        }
    }

    /**
     * Prepare a query to the ContentProvider (to the database) and load the visible List of
     * messages with this data
     * This is done asynchronously.
     * This method should be called from UI thread only.
     */
    protected void showList(TimelineListParameters params, TriState chainedRequest) {
        final String method = "showList";
        if (params.isEmpty()) {
            MyLog.v(this, method + "; ignored empty request");
            return;
        }
        boolean isDifferentRequest = !params.equals(paramsToLoad);
        paramsToLoad = params;
        if (isLoading() && chainedRequest != TriState.TRUE) {
            if(MyLog.isVerboseEnabled()) {
                if (isDifferentRequest) {
                    MyLog.v(this, method + "; different while loading " + params.toSummary());
                } else {
                    MyLog.v(this, method + "; ignored duplicating " + params.toSummary());
                }
            }
        } else {
            MyLog.v(this, method
                    + (chainedRequest == TriState.TRUE ? "; chained" : "")
                    + "; requesting " + (isDifferentRequest ? "" : "duplicating ")
                    + params.toSummary());
            saveListPosition();
            disableHeaderSyncButton(R.string.loading);
            disableFooterButton(R.string.loading);
            showLoading(method, getText(R.string.loading) + " "
                    + paramsToLoad.toSummary() + HORIZONTAL_ELLIPSIS);
            super.showList(chainedRequest.toBundle(paramsToLoad.whichPage.toBundle(),
                    IntentExtra.CHAINED_REQUEST.key));
        }
    }

    @Override
    protected SyncLoader newSyncLoader(Bundle args) {
        final String method = "newSyncLoader";
        WhichPage whichPage = WhichPage.load(args);
        TimelineListParameters params = paramsToLoad == null
                || whichPage == WhichPage.EMPTY ?
                new TimelineListParameters(myContext) : paramsToLoad;
        if (params.whichPage != WhichPage.EMPTY) {
            MyLog.v(this, method + ": " + params);
            Intent intent = getIntent();
            if (!params.getContentUri().equals(intent.getData())) {
                intent.setData(params.getContentUri());
            }
        }
        return new TimelineLoader(params, BundleUtils.fromBundle(args, IntentExtra.INSTANCE_ID));
    }

    @Override
    public void onLoadFinished(boolean keepCurrentPosition_in) {
        final String method = "onLoadFinished";
        verboseListPositionLog(method, "started");
        TimelineData dataLoaded = setAndGetListData(((TimelineLoader) getLoaded()).getPage());
        MyLog.v(this, method + "; " + dataLoaded.params.toSummary());

        // TODO start: Move this inside superclass
        boolean keepCurrentPosition = keepCurrentPosition_in && getListData().isSameTimeline &&
                isPositionRestored() && dataLoaded.params.whichPage != WhichPage.TOP;
        super.onLoadFinished(keepCurrentPosition);
        TimelineAdapter listAdapter = getListAdapter();
        if (dataLoaded.params.whichPage == WhichPage.TOP && listAdapter != null) {
            TimelineListPositionStorage.setPosition(getListView(), 0);
            listAdapter.setPositionRestored(true);
        }
        // TODO end: Move this inside superclass

        if (!isPositionRestored()) {
            new TimelineListPositionStorage(getListAdapter(), getListView(), dataLoaded.params)
                    .restore();
        }

        TimelineListParameters otherParams = paramsToLoad;
        boolean isParamsChanged = otherParams != null && !dataLoaded.params.equals(otherParams);
        WhichPage otherPageToRequest = WhichPage.EMPTY;
        if (!isParamsChanged && getListData().size() < 10) {
            if (getListData().mayHaveYoungerPage()) {
                otherPageToRequest = WhichPage.YOUNGER;
            } else if (getListData().mayHaveOlderPage()) {
                otherPageToRequest = WhichPage.OLDER;
            } else if (!dataLoaded.params.whichPage.isYoungest()) {
                otherPageToRequest = WhichPage.YOUNGEST;
            } else if (dataLoaded.params.rowsLoaded == 0) {
                launchSyncIfNeeded(dataLoaded.params.timelineToSync);
            }
        }
        hideLoading(method);
        updateScreen();
        clearNotifications();
        if (isParamsChanged) {
            MyLog.v(this, method + "; Parameters changed, requesting " + otherParams.toSummary());
            showList(otherParams, TriState.TRUE);
        } else if (otherPageToRequest != WhichPage.EMPTY) {
            MyLog.v(this, method + "; Nothing loaded, requesting " + otherPageToRequest);
            showList(otherPageToRequest, TriState.TRUE);
        }
    }

    private void addSyncButtons() {
        final ListView listView = getListView();
        if (listView != null) {
            if (listView.getHeaderViewsCount() == 0) {
                listView.addHeaderView(syncYoungerView);
                disableHeaderSyncButton(R.string.loading);
            }
            if (listView.getFooterViewsCount() == 0) {
                listView.addFooterView(syncOlderView);
                disableFooterButton(R.string.loading);
            }
        }
    }

    private void showSyncListButtons() {
        final ListView listView = getListView();
        if (listView != null) {
            showHeaderSyncButton(listView);
            showFooterSyncButton(listView);
        }
    }

    private boolean showHeaderSyncButton(@NonNull ListView listView) {
        if (getListData().mayHaveYoungerPage()) {
            disableHeaderSyncButton(R.string.loading);
            return false;
        }
        if (!getParamsLoaded().getTimeline().isSynableSomehow()) {
            disableHeaderSyncButton(R.string.not_syncable);
            return false;
        }
        long syncSucceededDate = getParamsLoaded().getTimeline().getSyncSucceededDate();
        long youngestItemDate = getParamsLoaded().getTimeline().getYoungestItemDate();
        String format = getText(getParamsLoaded().getTimeline().isCombined() ||
                youngestItemDate == 0 ? R.string.options_menu_sync : R.string.sync_younger_messages).toString();
        MyUrlSpan.showText(syncYoungerView, R.id.sync_younger_button,
                String.format(format,
                    syncSucceededDate > 0 ? RelativeTime.getDifference(this, syncSucceededDate) : getText(R.string.never),
                    DateUtils.getRelativeTimeSpanString(this, youngestItemDate)),
                false,
                false);
        syncYoungerView.setEnabled(true);
        return true;
    }

    private void disableHeaderSyncButton(int resInfo) {
        MyUrlSpan.showText(syncYoungerView, R.id.sync_younger_button,
                getText(resInfo).toString(),
                false,
                false);
        syncYoungerView.setEnabled(false);
    }

    private void showFooterSyncButton(@NonNull ListView listView) {
        if (getListData().mayHaveOlderPage()) {
            disableFooterButton(R.string.loading);
            return;
        }
        if (!getParamsLoaded().getTimeline().isSynableSomehow()) {
            disableFooterButton(R.string.no_more_messages);
            return;
        }
        MyUrlSpan.showText(syncOlderView, R.id.sync_older_button,
                String.format(getText(getParamsLoaded().getTimeline().isCombined() ||
                        getParamsLoaded().getTimeline().getOldestItemDate() == 0 ? R.string.options_menu_sync :
                        R.string.sync_older_messages).toString(),
                        DateUtils.getRelativeTimeSpanString(this, getParamsLoaded().getTimeline().getOldestItemDate())),
                false,
                false);
        syncOlderView.setEnabled(true);
    }

    private void disableFooterButton(int resInfo) {
        MyUrlSpan.showText(syncOlderView, R.id.sync_older_button,
                getText(resInfo).toString(),
                false,
                false);
        syncOlderView.setEnabled(false);
    }

    private void launchSyncIfNeeded(Timeline timelineToSync) {
        if (!timelineToSync.isEmpty()) {
            if (timelineToSync.getTimelineType() == TimelineType.EVERYTHING) {
                syncWithInternet(getParamsLoaded().getTimeline(), true, false);
                // Sync this one timeline and then - all syncable for this account
                if (getParamsNew().getMyAccount().isValidAndSucceeded()) {
                    getParamsNew().getMyAccount().requestSync();
                }
            } else {
                syncWithInternet(timelineToSync, true, false);
            }
        }
    }

    protected void syncWithInternet(Timeline timelineToSync, boolean syncYounger, boolean manuallyLaunched) {
        if (timelineToSync.isSyncableForOrigins()) {
            syncForAllOrigins(timelineToSync, syncYounger, manuallyLaunched);
        } else if (timelineToSync.isSyncableForAccounts()) {
            syncForAllAccounts(timelineToSync, syncYounger, manuallyLaunched);
        } else if (timelineToSync.isSyncable()) {
            syncOneTimeline(timelineToSync, syncYounger, manuallyLaunched);
        } else {
            hideSyncing("SyncWithInternet");
        }
    }

    private void syncForAllOrigins(Timeline timelineToSync, boolean syncYounger, boolean manuallyLaunched) {
        for (Origin origin : myContext.persistentOrigins().originsToSync(
                timelineToSync.getMyAccount().getOrigin(), true, timelineToSync.hasSearchQuery())) {
            syncOneTimeline(timelineToSync.cloneForOrigin(myContext, origin), syncYounger, manuallyLaunched);
        }
    }

    private void syncForAllAccounts(Timeline timelineToSync, boolean syncYounger, boolean manuallyLaunched) {
        for (MyAccount ma : myContext.persistentAccounts().accountsToSync(timelineToSync.getMyAccount(), true)) {
            if (timelineToSync.getTimelineType() == TimelineType.EVERYTHING) {
                ma.requestSync();
            } else {
                syncOneTimeline(timelineToSync.cloneForAccount(myContext, ma), syncYounger, manuallyLaunched);
            }
        }
    }

    private void syncOneTimeline(Timeline timeline, boolean syncYounger, boolean manuallyLaunched) {
        final String method = "syncOneTimeline";
        if (timeline.isSyncable()) {
            setCircularSyncIndicator(method, true);
            showSyncing(method, getText(R.string.options_menu_sync));
            MyServiceManager.sendForegroundCommand(
                    CommandData.newTimelineCommand(syncYounger ? CommandEnum.GET_TIMELINE :
                            CommandEnum.GET_OLDER_TIMELINE, timeline)
                            .setManuallyLaunched(manuallyLaunched)
            );
        }
    }

    protected void startMyPreferenceActivity() {
        finish();
        startActivity(new Intent(this, MySettingsActivity.class));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        getParamsNew().saveState(outState);
        outState.putBoolean(IntentExtra.COLLAPSE_DUPLICATES.key,
                getListData().isCollapseDuplicates());
        contextMenu.saveState(outState);
    }

    protected void crashTest() {
        final String CRASH_TEST_STRING = "Crash test 2015-04-10";
        if (MyLog.isVerboseEnabled() && getMessageEditor() != null &&
                getMessageEditor().getData().body.contains(CRASH_TEST_STRING)) {
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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
            case SELECT_TIMELINE:
                Timeline timeline = myContext.persistentTimelines()
                        .fromId(data.getLongExtra(IntentExtra.TIMELINE_ID.key, 0));
                if (timeline.isValid()) {
                    switchView(timeline, null);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void accountSelected(Intent data) {
        MyAccount ma = myContext.persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        if (ma.isValid()) {
            switchView(getParamsLoaded().getTimeline().isCombined() ?
                    getParamsLoaded().getTimeline() :
                    getParamsLoaded().getTimeline().fromMyAccount(myContext, ma), ma);
        }
    }

    private void accountToActAsSelected(Intent data) {
        MyAccount ma = myContext.persistentAccounts().fromAccountName(
                data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        if (ma.isValid()) {
            contextMenu.setMyActor(ma);
            contextMenu.showContextMenu();
        }
    }

    private void accountToShareViaSelected(Intent data) {
        MyAccount ma = myContext.persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        getMessageEditor().startEditingSharedData(ma, mTextToShareViaThisApp, mMediaToShareViaThisApp);
    }

    @Override
    protected boolean isEditorVisible() {
        return getMessageEditor().isVisible();
    }

    @Override
    protected boolean isCommandToShowInSyncIndicator(CommandData commandData) {
        switch (commandData.getCommand()) {
            case GET_TIMELINE:
            case GET_OLDER_TIMELINE:
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
            case UPDATE_STATUS:
            case DESTROY_STATUS:
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
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
        if (getListData().mayHaveYoungerPage()) {
            return true;
        }
        return super.canSwipeRefreshChildScrollUp();
    }

    @Override
    protected void onReceiveAfterExecutingCommand(CommandData commandData) {
        switch (commandData.getCommand()) {
            case RATE_LIMIT_STATUS:
                if (commandData.getResult().getHourlyLimit() > 0) {
                    mRateLimitText = commandData.getResult().getRemainingHits() + "/"
                            + commandData.getResult().getHourlyLimit();
                    updateTitle(mRateLimitText);
                }
                break;
            default:
                break;
        }
        if (!TextUtils.isEmpty(syncingText)) {
            if (MyServiceManager.getServiceState() != MyServiceState.RUNNING) {
                hideSyncing("Service is not running");
            } else if (isCommandToShowInSyncIndicator(commandData)) {
                showSyncing("After executing " + commandData.getCommand(), HORIZONTAL_ELLIPSIS);
            }
        }
        super.onReceiveAfterExecutingCommand(commandData);
    }

    @Override
    public boolean isRefreshNeededAfterExecuting(CommandData commandData) {
        boolean needed = super.isRefreshNeededAfterExecuting(commandData);
        switch (commandData.getCommand()) {
            case GET_TIMELINE:
            case GET_OLDER_TIMELINE:
                if (!getParamsLoaded().isLoaded()
                        || getParamsLoaded().getTimelineType() != commandData.getTimelineType()) {
                    break;
                }
                if (commandData.getResult().getDownloadedCount() > 0) {
                    needed = true;
                } else {
                    showSyncListButtons();
                }
                break;
            default:
                break;
        }
        return needed;
    }

    @Override
    protected boolean isAutoRefreshNow(boolean onStop) {
        return super.isAutoRefreshNow(onStop) && MyPreferences.isRefreshTimelineAutomatically();
    }

    @Override
    public void onMessageEditorVisibilityChange() {
        hideSyncing("onMessageEditorVisibilityChange");
        super.onMessageEditorVisibilityChange();
    }

    @Override
    public Timeline getTimeline() {
        return getParamsLoaded().getTimeline();
    }

    public void switchView(Timeline timeline, MyAccount newCurrentMyAccount) {
        MyAccount currentMyAccountToSet = MyAccount.EMPTY;
        if (newCurrentMyAccount != null && newCurrentMyAccount.isValid()) {
            currentMyAccountToSet = newCurrentMyAccount;
        } else if (timeline.getMyAccount().isValid()) {
            currentMyAccountToSet = timeline.getMyAccount();
        }
        if (currentMyAccountToSet.isValid()) {
            setCurrentMyAccount(currentMyAccountToSet, currentMyAccountToSet.getOrigin());
            myContext.persistentAccounts().setCurrentAccount(currentMyAccountToSet);
        }
        if (isFinishing() || !timeline.equals(getParamsLoaded().getTimeline())) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "switchTimelineActivity; " + timeline);
            }
            if (isFinishing()) {
                final Intent intent = getIntentForTimeline(myContext, timeline, newCurrentMyAccount, false);
                MyContextHolder.getMyFutureContext(this).thenStartActivity(intent);
            } else {
                TimelineActivity.startForTimeline(myContext, this, timeline, currentMyAccountToSet, false);
            }
        } else {
            showList(WhichPage.CURRENT);
        }
    }

    @NonNull
    public TimelineListParameters getParamsNew() {
        if (paramsNew == null) {
            paramsNew = new TimelineListParameters(myContext);
        }
        return paramsNew;
    }
}
