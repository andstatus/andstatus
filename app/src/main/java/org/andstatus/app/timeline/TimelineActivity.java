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

package org.andstatus.app.timeline;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityAdapter;
import org.andstatus.app.activity.ActivityContextMenu;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.actor.ActorProfileViewer;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.note.NoteAdapter;
import org.andstatus.app.note.NoteContextMenu;
import org.andstatus.app.note.NoteContextMenuContainer;
import org.andstatus.app.note.NoteEditorListActivity;
import org.andstatus.app.note.NoteViewItem;
import org.andstatus.app.note.SharedNote;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginSelector;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.test.SelectorActivityMock;
import org.andstatus.app.timeline.meta.ManageTimelines;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineSelector;
import org.andstatus.app.timeline.meta.TimelineTitle;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.ViewUtils;
import org.andstatus.app.view.MyContextMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivity<T extends ViewItem<T>> extends NoteEditorListActivity<T> implements
        NoteContextMenuContainer, AbsListView.OnScrollListener {
    public static final String HORIZONTAL_ELLIPSIS = "\u2026";

    /** Parameters for the next page request, not necessarily requested already */
    private volatile TimelineParameters paramsNew = null;
    /** Last parameters, requested to load. Thread safe. They are taken by a Loader at some time */
    private volatile TimelineParameters paramsToLoad;
    private volatile TimelineData<T> listData;

    private ActivityContextMenu contextMenu;

    private volatile Optional<SharedNote> sharedNote = Optional.empty();

    private String mRateLimitText = "";

    DrawerLayout mDrawerLayout;
    ActionBarDrawerToggle mDrawerToggle;

    private volatile SelectorActivityMock selectorActivityMock;
    View syncYoungerView = null;
    View syncOlderView = null;
    ActorProfileViewer actorProfileViewer = null;

    public static void startForTimeline(MyContext myContext, Context context, Timeline timeline) {
        startForTimeline(myContext, context, timeline, false, false);
    }

    public static void startForTimeline(MyContext myContext, Context context, Timeline timeline, boolean clearTask,
                                        boolean initialAccountSync) {
        timeline.save(myContext);
        final Intent intent = getIntentForTimeline(myContext, timeline, clearTask);
        if (initialAccountSync) {
            intent.putExtra(IntentExtra.INITIAL_ACCOUNT_SYNC.key, true);
        }
        context.startActivity(intent);
    }

    @NonNull
    private static Intent getIntentForTimeline(MyContext myContext, Timeline timeline, boolean clearTask) {
        Intent intent = new Intent(myContext.context(), clearTask ? FirstActivity.class : TimelineActivity.class);
        intent.setData(timeline.getUri());
        if (clearTask) {
            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    public static void goHome(Activity activity) {
        try {
            MyLog.v(TimelineActivity.class, () -> "goHome from " + MyLog.objToTag(activity));
            Intent intent = new Intent(activity, FirstActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            MyLog.v(TimelineActivity.class, "goHome", e);
            FirstActivity.startApp();
        }
    }

    @Override
    public void onRefresh() {
        syncWithInternet(getParamsLoaded().getTimeline(), true, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.timeline;
        super.onCreate(savedInstanceState);
        showSyncIndicatorSetting = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SYNC_INDICATOR_ON_TIMELINE, false);
        if (isFinishing()) return;

        contextMenu = new ActivityContextMenu(this);

        initializeDrawer();
        getListView().setOnScrollListener(this);

        View view = findViewById(R.id.my_action_bar);
        if (view != null) {
            view.setOnClickListener(this::onTimelineTitleClick);
        }

        syncYoungerView = View.inflate(this, R.layout.sync_younger, null);
        syncYoungerView.findViewById(R.id.sync_younger_button).setOnClickListener(
                v -> syncWithInternet(getParamsLoaded().getTimeline(), true, true));

        syncOlderView = View.inflate(this, R.layout.sync_older, null);
        syncOlderView.findViewById(R.id.sync_older_button).setOnClickListener(
                v -> syncWithInternet(getParamsLoaded().getTimeline(), false, true));

        actorProfileViewer = new ActorProfileViewer(this);

        addSyncButtons();

        searchView = findViewById(R.id.my_search_view);
        searchView.initialize(this);

        if (savedInstanceState != null) {
            restoreActivityState(savedInstanceState);
        } else {
            parseNewIntent(getIntent());
        }
    }

    @NonNull
    @Override
    protected BaseTimelineAdapter<T> newListAdapter() {
        if (getParamsNew().getTimelineType().showsActivities()) {
            return (BaseTimelineAdapter<T>) new ActivityAdapter(contextMenu,
                    (TimelineData<ActivityViewItem>) getListData());
        }
        return (BaseTimelineAdapter<T>) new NoteAdapter(
                contextMenu.note, (TimelineData<NoteViewItem>) getListData());
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    private void initializeDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
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
    }

    private void restoreActivityState(@NonNull Bundle savedInstanceState) {
        ParsedUri parsedUri = ParsedUri.fromUri(
                Uri.parse(savedInstanceState.getString(IntentExtra.MATCHED_URI.key,"")));
        final Timeline timeline = Timeline.fromParsedUri(myContext, parsedUri, "");
        TimelineParameters params = new TimelineParameters(myContext,
                timeline.isEmpty() ? myContext.timelines().getDefault() : timeline, WhichPage.CURRENT);
        setParamsNew(params);

        if (timeline.nonEmpty()) {
            contextMenu.note.loadState(savedInstanceState);
        }
        LoadableListViewParameters viewParameters = new LoadableListViewParameters(
                TriState.fromBoolean(savedInstanceState.getBoolean(
                    IntentExtra.COLLAPSE_DUPLICATES.key, MyPreferences.isCollapseDuplicates())),
                0,
                Optional.of(myContext.origins().fromId(savedInstanceState.getLong(
                        IntentExtra.ORIGIN_ID.key)))
        );
        getListData().updateView(viewParameters);
        actorProfileViewer.ensureView(getParamsNew().getTimeline().hasActorProfile());
        MyLog.v(this, () -> "restoreActivityState; " + getParamsNew());
    }

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

    public void onSwitchToDefaultTimelineButtonClick(View item) {
        closeDrawer();
        goHome(this);
    }

    public void onGoToTheTopButtonClick(View item) {
        closeDrawer();
        if (getListData().mayHaveYoungerPage()) {
            showList(WhichPage.TOP);
        } else {
            LoadableListPosition.setPosition(getListView(), 0);
        }
    }

    public void onCombinedTimelineToggleClick(View item) {
        closeDrawer();
        switchView( getParamsLoaded().getTimeline().fromIsCombined(myContext, !getParamsLoaded().isTimelineCombined()));
    }

    private void closeDrawer() {
        ViewGroup mDrawerList = findViewById(R.id.navigation_drawer);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    public void onCollapseDuplicatesToggleClick(View view) {
        closeDrawer();
        updateList(LoadableListViewParameters.collapseDuplicates(((CheckBox) view).isChecked()));
    }

    public void onShowSensitiveContentToggleClick(View view) {
        closeDrawer();
        MyPreferences.setShowSensitiveContent(((CheckBox) view).isChecked());
        updateList(LoadableListViewParameters.EMPTY);
    }

    public void onTimelineTypeButtonClick(View item) {
        TimelineSelector.selectTimeline(this, ActivityRequestCode.SELECT_TIMELINE,
                getParamsNew().getTimeline(), myContext.accounts().getCurrentAccount());
        closeDrawer();
    }

    public void onSelectAccountButtonClick(View item) {
        if (myContext.accounts().size() > 1) {
            AccountSelector.selectAccountOfOrigin(this, ActivityRequestCode.SELECT_ACCOUNT, 0);
        }
        closeDrawer();
    }

    public void onSelectProfileOriginButtonClick(View view) {
        OriginSelector.selectOriginForActor(this, MyContextMenu.MENU_GROUP_ACTOR_PROFILE,
                ActivityRequestCode.SELECT_ORIGIN, getParamsLoaded().timeline.actor);
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
            if (myContext.accounts().getCurrentAccount().isValid()) {
                if (isConfigChanged()) {
                    MyLog.v(this, () ->
                            method + "; Restarting this Activity to apply all new changes of configuration");
                    finish();
                    MyContextHolder.setExpiredIfConfigChanged();
                    switchView(getParamsLoaded().getTimeline());
                }
            } else { 
                MyLog.v(this, () ->
                        method + "; Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        final String method = "onPause";
        MyLog.v(this, method);
        hideLoading(method);
        hideSyncing(method);
        DemoData.crashTest(() -> getNoteEditor() != null
                && getNoteEditor().getData().getContent().startsWith("Crash me on pause 2015-04-10"));
        saveTimelinePosition();
        myContext.timelines().saveChanged();
        super.onPause();
    }

    /**
     * May be executed on any thread
     * That advice doesn't fit here:
     * see http://stackoverflow.com/questions/5996885/how-to-wait-for-android-runonuithread-to-be-finished
     */
    protected void saveTimelinePosition() {
        if (getParamsLoaded().isLoaded() && isPositionRestored()) {
            new TimelineViewPositionStorage<>(this, getParamsLoaded()).save();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == MyContextMenu.MENU_GROUP_ACTOR_PROFILE) {
            actorProfileViewer.contextMenu.onContextItemSelected(item);
        } else {
            contextMenu.onContextItemSelected(item);
        }
        return super.onContextItemSelected(item);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.create_note, menu);
        prepareMarkAllAsReadButton(menu);
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.timeline, menu);
        return true;
    }

    private void prepareMarkAllAsReadButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.markAllAsReadButton);
        if (item != null) {
            boolean enable = getParamsNew().timeline.getTimelineType() == TimelineType.UNREAD_NOTIFICATIONS;
            item.setEnabled(enable);
            item.setVisible(enable);
            if (enable) {
                item.setOnMenuItemClickListener(item1 -> {
                    clearNotifications(this);
                    return true;
                });
            }
        }
    }

    private static <T extends ViewItem<T>> void clearNotifications(TimelineActivity<T> timelineActivity) {
        final Timeline timeline = timelineActivity.getParamsLoaded().getTimeline();
        AsyncTaskLauncher.execute(timelineActivity, true,
                new MyAsyncTask<Void, Void, Void>("clearNotifications" + timeline.getId(),
                        MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected Void doInBackground2(Void aVoid) {
                        timelineActivity.myContext.clearNotifications(timeline);
                        return null;
                    }

                    @Override
                    protected void onPostExecute2(Void v) {
                        timelineActivity.refreshFromCache();
                    }
                }
        );
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MyAccount ma = myContext.accounts().getCurrentAccount();
        boolean enableSync = (getParamsLoaded().getTimeline().isCombined() || ma.isValidAndSucceeded())
                && getParamsLoaded().getTimeline().isSynableSomehow();
        MenuItem item = menu.findItem(R.id.sync_menu_item);
        if (item != null) {
            item.setEnabled(enableSync);
            item.setVisible(enableSync);
        }

        prepareDrawer();

        if (contextMenu != null) {
            contextMenu.note.setSelectedActingAccount(MyAccount.EMPTY);
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
                // Show the "Combined" toggle even for one account to see notes,
                // which are not on the timeline.
                // E.g. notes by actors, downloaded on demand.
                getParamsNew().getSelectedActorId() == 0 ||
                        getParamsNew().getSelectedActorId() == getParamsNew().getMyAccount().getActorId())) {
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
            case R.id.refresh_menu_item:
                refreshFromCache();
                break;
            case R.id.commands_queue_id:
                startActivity(new Intent(getActivity(), QueueViewer.class));
                break;
            case R.id.manage_timelines:
                startActivity(new Intent(getActivity(), ManageTimelines.class));
                break;
            case R.id.preferences_menu_id:
                startMyPreferenceActivity();
                break;
            case R.id.help_menu_id:
                onHelp();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    private void refreshFromCache() {
        if (getListData().mayHaveYoungerPage() || getListView().getLastVisiblePosition() > TimelineParameters.PAGE_SIZE / 2) {
            showList(WhichPage.CURRENT);
        } else {
            showList(WhichPage.YOUNGEST);
        }
    }

    private void onHelp() {
        HelpActivity.startMe(this, false, HelpActivity.PAGE_CHANGELOG);
    }

    public void onItemClick(NoteViewItem item) {
        MyAccount ma = myContext.accounts().getAccountForThisNote(item.getOrigin(),
                getParamsNew().getMyAccount(), item.getLinkedMyAccount(), false);
        MyLog.v(this, () -> "onItemClick, " + item + "; " + item + " account=" + ma.getAccountName());
        if (item.getNoteId() <= 0) return;

        Uri uri = MatchedUri.getTimelineItemUri(
                myContext.timelines().get(TimelineType.EVERYTHING, 0, item.getOrigin()),
                item.getNoteId());

        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, setData=" + uri);
            }
            setResult(Activity.RESULT_OK, new Intent().setData(uri));
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
        return TimelineTitle.from(myContext, getParamsLoaded().getTimeline(),
                MyAccount.EMPTY, false, TimelineTitle.Destination.DEFAULT).toString();
    }

    private void updateAccountButtonText(View drawerView) {
        TextView textView = drawerView.findViewById(R.id.selectAccountButton);
        if (textView == null) {
            return;
        }
        String accountButtonText = myContext.accounts().getCurrentAccount().toAccountButtonText();
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
            MyLog.v(this,  () ->"onNewIntent, context is " + myContext.state());
            finish();
            this.startActivity(intent);
            return;
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "onNewIntent");
        }
        super.onNewIntent(intent);
        parseNewIntent(intent);
        checkForInitialSync(intent);
        if (isMyResumed() || getListData().size() > 0 || isLoading()) {
            showList(getParamsNew().whichPage);
		}
    }

    private void checkForInitialSync(Intent intent) {
        Timeline timeline = getParamsNew().timeline;
        if (timeline.isTimeToAutoSync() && timeline.getLastSyncedDate() == DATETIME_MILLIS_NEVER) {
            if (intent.hasExtra(IntentExtra.INITIAL_ACCOUNT_SYNC.key)) {
                timeline.setSyncSucceededDate(System.currentTimeMillis()); // To avoid repetition
                MyServiceManager.setServiceAvailable();
                MyServiceManager.sendManualForegroundCommand(
                        CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, timeline));
                MyServiceManager.sendCommand(
                        CommandData.newActorCommand(CommandEnum.GET_FRIENDS, timeline.myAccountToSync.getActor().actorId, ""));
            } else {
                onNoRowsLoaded(timeline);
            }
        }
    }

    private void parseNewIntent(Intent intentNew) {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "parseNewIntent");
        }
        mRateLimitText = "";
        WhichPage whichPage = WhichPage.load(intentNew.getStringExtra(IntentExtra.WHICH_PAGE.key), WhichPage.CURRENT);
        String searchQuery = intentNew.getStringExtra(IntentExtra.SEARCH_QUERY.key);
        ParsedUri parsedUri = ParsedUri.fromUri(intentNew.getData());
        Timeline timeline = Timeline.fromParsedUri(myContext, parsedUri, searchQuery);
        setParamsNew(new TimelineParameters(myContext,
                timeline.isEmpty() ? myContext.timelines().getDefault() : timeline,
                whichPage));

        actorProfileViewer.ensureView(getParamsNew().getTimeline().hasActorProfile());

        if (Intent.ACTION_SEND.equals(intentNew.getAction())) {
            sharedNote = SharedNote.fromIntent(intentNew);
            sharedNote.ifPresent(shared -> {
                MyLog.v(this, shared.toString());
                AccountSelector.selectAccountOfOrigin(this, ActivityRequestCode.SELECT_ACCOUNT_TO_SHARE_VIA, 0);
            });
        }
    }

    @Override
    public void updateScreen() {
        MyServiceManager.setServiceAvailable();
        invalidateOptionsMenu();
        getNoteEditor().updateScreen();
        updateTitle(mRateLimitText);
        mDrawerToggle.setDrawerIndicatorEnabled(!getParamsLoaded().isAtHome());
        showRecentAccounts();
        ViewUtils.showView(
                findViewById(R.id.switchToDefaultTimelineButton), !getParamsLoaded().isAtHome());
        ViewUtils.showView(this, R.id.collapseDuplicatesToggle,
                MyPreferences.getMaxDistanceBetweenDuplicates() > 0);
        MyCheckBox.setEnabled(this, R.id.collapseDuplicatesToggle,
                getListData().isCollapseDuplicates());
        MyCheckBox.setEnabled(this, R.id.showSensitiveContentToggle, MyPreferences.isShowSensitiveContent());
        showSyncListButtons();
        showActorProfile();
    }

    private void showRecentAccounts() {
        List<MyAccount> recentAccounts = new ArrayList<>(myContext.accounts().recentAccounts);
        for (int ind = 0; ind < 3; ind++) {
            MyAccount ma = recentAccounts.size() > ind ? recentAccounts.get(ind) : MyAccount.EMPTY;
            AvatarView avatarView = findViewById(ind == 0
                    ? R.id.current_account_avatar_image
                    : (ind == 1 ? R.id.account_avatar_image_1 : R.id.account_avatar_image_2));
            if (avatarView == null) break;

            ViewUtils.showView(avatarView, ma.isValid());
            if (ma.nonValid()) continue;

            ma.getActor().avatarFile.showImage(this, avatarView);
            avatarView.setContentDescription(ma.getAccountName());
            avatarView.setOnClickListener(ind == 0
                ? v -> {
                    TimelineActivity.startForTimeline(
                        getMyContext(), this,
                        getMyContext().timelines().forUser(TimelineType.SENT, ma.getActor()));
                    closeDrawer();
                    }
                : v -> {
                    onAccountSelected(ma);
                    closeDrawer();
                    }
            );
        }
    }

    @Override
    protected void updateTitle(String additionalTitleText) {
        TimelineTitle.from(myContext, getParamsLoaded().getTimeline(), MyAccount.EMPTY,true,
                TimelineTitle.Destination.TIMELINE_ACTIVITY).updateActivityTitle(this, additionalTitleText);
    }

    NoteContextMenu getContextMenu() {
        return contextMenu.note;
    }

    /** Parameters of currently shown Timeline */
    @NonNull
    private TimelineParameters getParamsLoaded() {
        return getListData().params;
    }

    @Override
    @NonNull
    public TimelineData<T> getListData() {
        if (listData == null) {
            listData = new TimelineData<>(null, new TimelinePage<T>(getParamsNew(), Collections.emptyList()));
        }
        return listData;
    }

    @NonNull
    private TimelineData<T> setListData(TimelinePage<T> pageLoaded) {
        listData = new TimelineData<T>(listData, pageLoaded);
        return listData;
    }

    @Override
    public void showList(WhichPage whichPage) {
        showList(whichPage, TriState.FALSE);
    }

    protected void showList(WhichPage whichPage, TriState chainedRequest) {
        showList(TimelineParameters.clone(getReferenceParametersFor(whichPage), whichPage),
                chainedRequest);
    }

    @NonNull
    private TimelineParameters getReferenceParametersFor(WhichPage whichPage) {
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
                return new TimelineParameters(myContext, Timeline.EMPTY, WhichPage.EMPTY);
            default:
                return getParamsNew();
        }
    }

    /**
     * Prepare a query to the ContentProvider (to the database) and load the visible List of notes with this data
     * This is done asynchronously.
     * This method should be called from UI thread only.
     */
    protected void showList(TimelineParameters params, TriState chainedRequest) {
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
                    MyLog.v(this, () -> method + "; different while loading " + params.toSummary());
                } else {
                    MyLog.v(this, () -> method + "; ignored duplicating " + params.toSummary());
                }
            }
        } else {
            MyLog.v(this, () -> method
                    + (chainedRequest == TriState.TRUE ? "; chained" : "")
                    + "; requesting " + (isDifferentRequest ? "" : "duplicating ")
                    + params.toSummary());
            if (chainedRequest.untrue) saveTimelinePosition();
            disableHeaderSyncButton(R.string.loading);
            disableFooterButton(R.string.loading);
            showLoading(method, getText(R.string.loading) + " "
                    + paramsToLoad.toSummary() + HORIZONTAL_ELLIPSIS);
            super.showList(chainedRequest.toBundle(paramsToLoad.whichPage.toBundle(),
                    IntentExtra.CHAINED_REQUEST.key));
        }
    }

    @Override
    protected SyncLoader<T> newSyncLoader(Bundle args) {
        final String method = "newSyncLoader";
        WhichPage whichPage = WhichPage.load(args);
        TimelineParameters params = paramsToLoad == null || whichPage == WhichPage.EMPTY
                ? new TimelineParameters(myContext, Timeline.EMPTY, whichPage) : paramsToLoad;
        if (params.whichPage != WhichPage.EMPTY) {
            MyLog.v(this, () -> method + ": " + params);
            Intent intent = getIntent();
            if (!params.getContentUri().equals(intent.getData())) {
                intent.setData(params.getContentUri());
            }
        }
        return new TimelineLoader<T>(params, BundleUtils.fromBundle(args, IntentExtra.INSTANCE_ID));
    }

    @Override
    public void onLoadFinished(LoadableListPosition posIn) {
        final String method = "onLoadFinished";
        if (MyLog.isVerboseEnabled()) posIn.logV(method + " started;");
        TimelineData<T> dataLoaded = setListData(((TimelineLoader<T>) getLoaded()).getPage());
        MyLog.v(this, () -> method + "; " + dataLoaded.params.toSummary());

        LoadableListPosition pos = posIn.nonEmpty() && getListData().isSameTimeline &&
            isPositionRestored() && dataLoaded.params.whichPage != WhichPage.TOP
                ? posIn
                : TimelineViewPositionStorage.loadListPosition(dataLoaded.params);
        super.onLoadFinished(pos);
        if (dataLoaded.params.whichPage == WhichPage.TOP) {
            LoadableListPosition.setPosition(getListView(), 0);
            getListAdapter().setPositionRestored(true);
        }

        if (!isPositionRestored()) {
            new TimelineViewPositionStorage<T>(this, dataLoaded.params).restore();
        }

        TimelineParameters otherParams = paramsToLoad;
        boolean isParamsChanged = otherParams != null && !dataLoaded.params.equals(otherParams);
        WhichPage otherPageToRequest;
        if (!isParamsChanged && getListData().size() < 10) {
            if (getListData().mayHaveYoungerPage()) {
                otherPageToRequest = WhichPage.YOUNGER;
            } else if (getListData().mayHaveOlderPage()) {
                otherPageToRequest = WhichPage.OLDER;
            } else if (!dataLoaded.params.whichPage.isYoungest()) {
                otherPageToRequest = WhichPage.YOUNGEST;
            } else if (dataLoaded.params.rowsLoaded == 0) {
                otherPageToRequest = WhichPage.EMPTY;
                onNoRowsLoaded(dataLoaded.params.timeline);
            } else {
                otherPageToRequest = WhichPage.EMPTY;
            }
        } else {
            otherPageToRequest = WhichPage.EMPTY;
        }
        hideLoading(method);
        updateScreen();
        if (isParamsChanged) {
            MyLog.v(this, () -> method + "; Parameters changed, requesting " + otherParams.toSummary());
            showList(otherParams, TriState.TRUE);
        } else if (otherPageToRequest != WhichPage.EMPTY) {
            MyLog.v(this, () -> method + "; Other page requested " + otherPageToRequest);
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
        showHeaderSyncButton();
        showFooterSyncButton();
    }

    private void showHeaderSyncButton() {
        if (getListData().mayHaveYoungerPage()) {
            disableHeaderSyncButton(R.string.loading);
            return;
        }
        if (!getParamsLoaded().getTimeline().isSynableSomehow()) {
            disableHeaderSyncButton(R.string.not_syncable);
            return;
        }
        SyncStats stats = SyncStats.fromYoungestDates(myContext.timelines().toTimelinesToSync(getParamsLoaded().getTimeline()));
        String format = getText(stats.itemDate > SOME_TIME_AGO
                ? R.string.sync_younger_messages
                : R.string.options_menu_sync).toString();
        MyUrlSpan.showText(syncYoungerView, R.id.sync_younger_button,
                String.format(format,
                    stats.syncSucceededDate > SOME_TIME_AGO
                            ? RelativeTime.getDifference(this, stats.syncSucceededDate)
                            : getText(R.string.never),
                    DateUtils.getRelativeTimeSpanString(this, stats.itemDate)),
                false,
                false);
        syncYoungerView.setEnabled(true);
    }

    private void disableHeaderSyncButton(int resInfo) {
        MyUrlSpan.showText(syncYoungerView, R.id.sync_younger_button,
                getText(resInfo).toString(),
                false,
                false);
        syncYoungerView.setEnabled(false);
    }

    private void showFooterSyncButton() {
        if (getListData().mayHaveOlderPage()) {
            disableFooterButton(R.string.loading);
            return;
        }
        if (!getParamsLoaded().getTimeline().isSynableSomehow()) {
            disableFooterButton(R.string.no_more_messages);
            return;
        }
        SyncStats stats = SyncStats.fromOldestDates(myContext.timelines().toTimelinesToSync(getParamsLoaded().getTimeline()));
        MyUrlSpan.showText(syncOlderView, R.id.sync_older_button,
                String.format(getText(stats.itemDate > SOME_TIME_AGO
                                ? R.string.sync_older_messages
                                : R.string.options_menu_sync).toString(),
                        DateUtils.getRelativeTimeSpanString(this, stats.itemDate)),
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

    private void showActorProfile() {
        if (getParamsLoaded().timeline.hasActorProfile()) {
            actorProfileViewer.populateView();
        }
    }

    private void onNoRowsLoaded(@NonNull Timeline timeline) {
        MyAccount ma = timeline.myAccountToSync;
        if (!timeline.isSyncable() || !timeline.isTimeToAutoSync() || !ma.isValidAndSucceeded()) {
            return;
        }
        timeline.setSyncSucceededDate(System.currentTimeMillis()); // To avoid repetition
        syncWithInternet(timeline, true, false);
    }

    protected void syncWithInternet(Timeline timelineToSync, boolean syncYounger, boolean manuallyLaunched) {
        myContext.timelines().toTimelinesToSync(timelineToSync)
                .forEach(timeline -> syncOneTimeline(timeline, syncYounger, manuallyLaunched));
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
        outState.putBoolean(IntentExtra.COLLAPSE_DUPLICATES.key, getListData().isCollapseDuplicates());
        if (getListData().getPreferredOrigin().nonEmpty()) {
            outState.putLong(IntentExtra.ORIGIN_ID.key, getListData().getPreferredOrigin().getId());
        }
        contextMenu.saveState(outState);
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
        MyLog.v(this, () -> "onActivityResult; request:" + requestCode
                + ", result:" + (resultCode == Activity.RESULT_OK ? "ok" : "fail"));
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT:
                onAccountSelected(data);
                break;
            case SELECT_ACCOUNT_TO_ACT_AS:
                setSelectedActingAccount(data);
                break;
            case SELECT_ACCOUNT_TO_SHARE_VIA:
                sharedNote.ifPresent(shared -> getNoteEditor().startEditingSharedData(
                        myContext.accounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key)),
                        shared)
                );
                break;
            case SELECT_TIMELINE:
                Timeline timeline = myContext.timelines()
                        .fromId(data.getLongExtra(IntentExtra.TIMELINE_ID.key, 0));
                if (timeline.isValid()) {
                    switchView(timeline);
                }
                break;
            case SELECT_ORIGIN:
                onOriginSelected(data);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void onAccountSelected(Intent data) {
        onAccountSelected(myContext.accounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key)));
    }

    private void onAccountSelected(MyAccount ma) {
        if (ma.isValid()) {
            myContext.accounts().setCurrentAccount(ma);
            switchView(getParamsLoaded().getTimeline().isCombined() ?
                    getParamsLoaded().getTimeline() :
                    getParamsLoaded().getTimeline().fromMyAccount(myContext, ma));
        }
    }

    private void onOriginSelected(Intent data) {
        Origin origin = myContext.origins().fromName(data.getStringExtra(IntentExtra.ORIGIN_NAME.key));
        if (origin.isValid() && getParamsLoaded().getTimeline().hasActorProfile()) {
            updateList(LoadableListViewParameters.fromOrigin(origin));
        }
    }

    private void setSelectedActingAccount(Intent data) {
        MyAccount ma = myContext.accounts().fromAccountName(
                data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        if (ma.isValid()) {
            MyContextMenu contextMenu = getContextMenu(
                    data.getIntExtra(IntentExtra.MENU_GROUP.key, MyContextMenu.MENU_GROUP_NOTE));
            contextMenu.setSelectedActingAccount(ma);
            contextMenu.showContextMenu();
        }
    }

    @NonNull
    private MyContextMenu getContextMenu(int menuGroup) {
        switch (menuGroup) {
            case MyContextMenu.MENU_GROUP_ACTOR:
                return contextMenu.actor;
            case MyContextMenu.MENU_GROUP_OBJACTOR:
                return contextMenu.objActor;
            case MyContextMenu.MENU_GROUP_ACTOR_PROFILE:
                return actorProfileViewer == null
                    ? contextMenu.note
                    : actorProfileViewer.contextMenu;
            default:
                return contextMenu.note;
        }
    }

    @Override
    protected boolean isEditorVisible() {
        return getNoteEditor().isVisible();
    }

    @Override
    protected boolean isCommandToShowInSyncIndicator(CommandData commandData) {
        switch (commandData.getCommand()) {
            case GET_TIMELINE:
            case GET_OLDER_TIMELINE:
            case GET_ATTACHMENT:
            case GET_AVATAR:
            case UPDATE_NOTE:
            case DELETE_NOTE:
            case LIKE:
            case UNDO_LIKE:
            case FOLLOW:
            case UNDO_FOLLOW:
            case ANNOUNCE:
            case UNDO_ANNOUNCE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        return getListData().mayHaveYoungerPage() || super.canSwipeRefreshChildScrollUp();
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
    public void onNoteEditorVisibilityChange() {
        hideSyncing("onNoteEditorVisibilityChange");
        super.onNoteEditorVisibilityChange();
    }

    @Override
    public Timeline getTimeline() {
        return getParamsLoaded().getTimeline();
    }

    public void switchView(Timeline timeline) {
        timeline.save(myContext);

        if (isFinishing() || !timeline.equals(getParamsLoaded().getTimeline())) {
            MyLog.v(this, () -> "switchTimelineActivity; " + timeline);
            if (isFinishing()) {
                final Intent intent = getIntentForTimeline(myContext, timeline, false);
                MyContextHolder.getMyFutureContext(this).thenStartActivity(intent);
            } else {
                TimelineActivity.startForTimeline(myContext, this, timeline);
            }
        } else {
            showList(WhichPage.CURRENT);
        }
    }

    @NonNull
    public TimelineParameters setParamsNew(TimelineParameters params) {
        paramsNew = params;
        return paramsNew;
    }

    @NonNull
    public TimelineParameters getParamsNew() {
        if (paramsNew == null) {
            paramsNew = new TimelineParameters(myContext, Timeline.EMPTY, WhichPage.EMPTY);
        }
        return paramsNew;
    }

    public void setSelectorActivityMock(SelectorActivityMock selectorActivityMock) {
        this.selectorActivityMock = selectorActivityMock;
    }
}
