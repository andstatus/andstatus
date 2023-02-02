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
package org.andstatus.app.timeline

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import io.vavr.control.Try
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.FirstActivity
import org.andstatus.app.HelpActivity
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyAction
import org.andstatus.app.R
import org.andstatus.app.account.AccountSelector
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityAdapter
import org.andstatus.app.activity.ActivityContextMenu
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.actor.ActorProfileViewer
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.ParsedUri
import org.andstatus.app.graphics.AvatarView
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ApiDebugger
import org.andstatus.app.note.NoteAdapter
import org.andstatus.app.note.NoteContextMenu
import org.andstatus.app.note.NoteContextMenuContainer
import org.andstatus.app.note.NoteEditorListActivity
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.note.SharedNote
import org.andstatus.app.origin.OriginSelector
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncRunnable
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.service.QueueViewer
import org.andstatus.app.test.SelectorActivityStub
import org.andstatus.app.timeline.meta.ManageTimelines
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineSelector
import org.andstatus.app.timeline.meta.TimelineTitle
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.BundleUtils
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.ViewUtils
import org.andstatus.app.view.MyContextMenu
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author yvolk@yurivolkov.com
 */
class TimelineActivity<T : ViewItem<T>> : NoteEditorListActivity<T>(TimelineActivity::class),
    NoteContextMenuContainer, AbsListView.OnScrollListener {
    /** Parameters for the next page request, not necessarily requested already  */
    @Volatile
    private var paramsNew: TimelineParameters? = null

    /** Last parameters, requested to load. Thread safe. They are taken by a Loader at some time  */
    @Volatile
    private var paramsToLoad: TimelineParameters? = null

    @Volatile
    private var listData: TimelineData<T>? = null
    private var contextMenu: ActivityContextMenu? = null

    @Volatile
    private var sharedNote: Optional<SharedNote> = Optional.empty()
    private var mRateLimitText: String? = ""
    var mDrawerLayout: DrawerLayout? = null
    var mDrawerToggle: ActionBarDrawerToggle? = null

    @Volatile
    private var selectorActivityStub: SelectorActivityStub? = null
    var syncYoungerView: View? = null
    var syncOlderView: View? = null
    var actorProfileViewer: ActorProfileViewer? = null
    override fun onRefresh() {
        syncWithInternet(getParamsLoaded().timeline, true, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.timeline
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        showSyncIndicatorSetting = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SYNC_INDICATOR_ON_TIMELINE, false)
        contextMenu = ActivityContextMenu(this)
        initializeDrawer()
        listView?.setOnScrollListener(this)
        findViewById<View?>(R.id.my_action_bar)?.setOnClickListener { item: View? -> onTimelineTitleClick(item) }
        syncYoungerView = View.inflate(this, R.layout.sync_younger, null)
        syncYoungerView?.findViewById<View?>(R.id.sync_younger_button)?.setOnClickListener { v: View ->
            syncWithInternet(getParamsLoaded().timeline, true, true)
        }
        syncOlderView = View.inflate(this, R.layout.sync_older, null)
        syncOlderView?.findViewById<View?>(R.id.sync_older_button)?.setOnClickListener { v: View? ->
            syncWithInternet(getParamsLoaded().timeline, false, true)
        }
        actorProfileViewer = ActorProfileViewer(this)
        addSyncButtons()
        searchView = findViewById(R.id.my_search_view)
        searchView?.initialize(this)
        if (savedInstanceState != null) {
            restoreActivityState(savedInstanceState)
        } else {
            parseNewIntent(intent)
        }
    }

    override fun newListAdapter(): BaseTimelineAdapter<T> {
        return if (getParamsNew().getTimelineType().showsActivities()) {
            ActivityAdapter(
                contextMenu ?: throw IllegalStateException("No context menu"),
                getListData() as TimelineData<ActivityViewItem>
            ) as BaseTimelineAdapter<T>
        } else NoteAdapter(
            contextMenu?.note ?: throw IllegalStateException("No context menu"),
            getListData() as TimelineData<NoteViewItem>
        ) as BaseTimelineAdapter<T>
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle?.syncState()
    }

    private fun initializeDrawer() {
        mDrawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)?.also { layout ->
            mDrawerToggle = ActionBarDrawerToggle(
                this,
                layout,
                R.string.drawer_open,
                R.string.drawer_close
            ).also { toggle ->
                layout.addDrawerListener(toggle)
                toggle.setHomeAsUpIndicator(MyPreferences.getActionBarTextHomeIconResourceId())
            }
        }
    }

    private fun restoreActivityState(savedInstanceState: Bundle) {
        val parsedUri: ParsedUri = ParsedUri.fromUri(
            Uri.parse(savedInstanceState.getString(IntentExtra.MATCHED_URI.key, ""))
        )
        val timeline: Timeline = Timeline.fromParsedUri(myContext, parsedUri, "")
        val params = TimelineParameters(
            myContext,
            if (timeline.isEmpty) myContext.timelines.getDefault() else timeline, WhichPage.CURRENT
        )
        setParamsNew(params)
        if (timeline.nonEmpty) {
            contextMenu?.note?.loadState(savedInstanceState)
        }
        val viewParameters = LoadableListViewParameters(
            TriState.fromBoolean(
                savedInstanceState.getBoolean(
                    IntentExtra.COLLAPSE_DUPLICATES.key, MyPreferences.isCollapseDuplicates()
                )
            ),
            0,
            Optional.of(
                myContext.origins.fromId(
                    savedInstanceState.getLong(
                        IntentExtra.ORIGIN_ID.key
                    )
                )
            )
        )
        getListData().updateView(viewParameters)
        actorProfileViewer?.ensureView(getParamsNew().timeline.hasActorProfile())
        MyLog.v(this) { "restoreActivityState; " + getParamsNew() }
    }

    fun onTimelineTitleClick(item: View?) {
        when (MyPreferences.getTapOnATimelineTitleBehaviour()) {
            TapOnATimelineTitleBehaviour.SWITCH_TO_DEFAULT_TIMELINE -> if (getParamsLoaded().isAtHome()) {
                onTimelineTypeButtonClick(item)
            } else {
                FirstActivity.goHome(this)
            }
            TapOnATimelineTitleBehaviour.GO_TO_THE_TOP -> onGoToTheTopButtonClick(item)
            TapOnATimelineTitleBehaviour.SELECT_TIMELINE -> onTimelineTypeButtonClick(item)
            else -> {
            }
        }
    }

    fun onSwitchToDefaultTimelineButtonClick(item: View?) {
        closeDrawer()
        FirstActivity.goHome(this)
    }

    fun onGoToTheTopButtonClick(item: View?) {
        closeDrawer()
        if (getListData().mayHaveYoungerPage()) {
            showList(WhichPage.TOP)
        } else {
            LoadableListPosition.setPosition(listView, 0)
        }
    }

    fun onCombinedTimelineToggleClick(item: View?) {
        closeDrawer()
        switchView(getParamsLoaded().timeline.fromIsCombined(myContext, !getParamsLoaded().isTimelineCombined()))
    }

    private fun closeDrawer() {
        val mDrawerList = findViewById<ViewGroup?>(R.id.navigation_drawer)
        mDrawerLayout?.closeDrawer(mDrawerList)
    }

    fun onCollapseDuplicatesToggleClick(view: View?) {
        closeDrawer()
        updateList(LoadableListViewParameters.collapseDuplicates((view as CheckBox).isChecked()))
    }

    fun onShowSensitiveContentToggleClick(view: View?) {
        closeDrawer()
        MyPreferences.setShowSensitiveContent((view as CheckBox).isChecked())
        updateList(LoadableListViewParameters.EMPTY)
    }

    fun onTimelineTypeButtonClick(item: View?) {
        TimelineSelector.selectTimeline(
            this, ActivityRequestCode.SELECT_TIMELINE,
            getParamsNew().timeline, myContext.accounts.currentAccount
        )
        closeDrawer()
    }

    fun onSelectAccountButtonClick(item: View?) {
        if (myContext.accounts.size() > 1) {
            AccountSelector.selectAccountOfOrigin(this, ActivityRequestCode.SELECT_ACCOUNT, 0)
        }
        closeDrawer()
    }

    fun onSelectProfileOriginButtonClick(view: View?) {
        OriginSelector.selectOriginForActor(
            this, MyContextMenu.MENU_GROUP_ACTOR_PROFILE,
            ActivityRequestCode.SELECT_ORIGIN, getParamsLoaded().timeline.actor
        )
    }

    /**
     * See [Creating
 * a Search Interface](http://developer.android.com/guide/topics/search/search-dialog.html)
     */
    override fun onSearchRequested(): Boolean {
        searchView?.showSearchView(getParamsLoaded().timeline)?.also {
            return true
        }
        return false
    }

    override fun onResume() {
        val method = "onResume"
        if (!isFinishing) {
            correctCurrentAccount(getParamsLoaded().timeline)
            if (myContext.accounts.currentAccount.isValid) {
                if (isContextNeedsUpdate()) {
                    MyLog.v(this) { "$method; Restarting this Activity to apply all new changes of configuration" }
                    finish()
                    switchView(getParamsLoaded().timeline)
                }
            } else {
                MyLog.v(this) { "$method; Finishing this Activity and going Home because there is no Account selected" }
                FirstActivity.goHome(this)
                finish()
            }
        }
        super.onResume()
    }

    override fun onPause() {
        val method = "onPause"
        MyLog.v(this, method)
        hideLoading(method)
        hideSyncing(method)
        DemoData.crashTest(getNoteEditor()?.getData()?.getContent() ?: "")
        saveTimelinePosition()
        myContext.timelines.saveChanged()
        super.onPause()
    }

    /**
     * May be executed on any thread
     * That advice doesn't fit here:
     * see http://stackoverflow.com/questions/5996885/how-to-wait-for-android-runonuithread-to-be-finished
     */
    protected fun saveTimelinePosition() {
        if (getParamsLoaded().isLoaded && isPositionRestored()) {
            TimelineViewPositionStorage(this, getParamsLoaded()).save()
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.getGroupId() == MyContextMenu.MENU_GROUP_ACTOR_PROFILE) {
            actorProfileViewer?.contextMenu?.onContextItemSelected(item)
        } else {
            contextMenu?.onContextItemSelected(item)
        }
        return super.onContextItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu ?: return false

        menuInflater.inflate(R.menu.create_note, menu)
        prepareMarkAllAsReadButton(menu)
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.timeline, menu)
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            val item = menu.findItem(R.id.debug_api)
            if (item != null) {
                item.isVisible = true
            }
        }
        return true
    }

    private fun prepareMarkAllAsReadButton(menu: Menu?) {
        menu ?: return

        val item = menu.findItem(R.id.markAllAsReadButton)
        if (item != null) {
            val enable = getParamsNew().timeline.timelineType == TimelineType.UNREAD_NOTIFICATIONS
            item.isEnabled = enable
            item.isVisible = enable
            if (enable) {
                item.setOnMenuItemClickListener { item1: MenuItem ->
                    clearNotifications(this)
                    true
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu ?: return false

        val ma = myContext.accounts.currentAccount
        val enableSync = ((getParamsLoaded().timeline.isCombined || ma.isValidAndSucceeded())
            && getParamsLoaded().timeline.isSyncableSomehow)
        val item = menu.findItem(R.id.sync_menu_item)
        if (item != null) {
            item.isEnabled = enableSync
            item.isVisible = enableSync
        }
        prepareDrawer()
        contextMenu?.note?.setSelectedActingAccount(MyAccount.EMPTY)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun prepareDrawer() {
        val drawerView = findViewById<View?>(R.id.navigation_drawer) ?: return
        val item = drawerView.findViewById<TextView?>(R.id.timelineTypeButton)
        if (item != null) {
            item.text = timelineTypeButtonText()
        }
        prepareCombinedTimelineToggle(drawerView)
        updateAccountButtonText(drawerView)
    }

    private fun prepareCombinedTimelineToggle(drawerView: View?) {
        if (ViewUtils.showView(
                drawerView,
                R.id.combinedTimelineToggle,  // Show the "Combined" toggle even for one account to see notes,
                // which are not on the timeline.
                // E.g. notes by actors, downloaded on demand.
                getParamsNew().getSelectedActorId() == 0L ||
                    getParamsNew().getSelectedActorId() == getParamsNew().getMyAccount().actorId
            )
        ) {
            MyCheckBox.setEnabled(
                drawerView, R.id.combinedTimelineToggle,
                getParamsLoaded().timeline.isCombined
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mDrawerToggle?.onOptionsItemSelected(item) ?: false) {
            return true
        }
        when (item.getItemId()) {
            android.R.id.home -> mDrawerLayout?.let { layout ->
                if (layout.isDrawerOpen(Gravity.LEFT)) {
                    layout.closeDrawer(Gravity.LEFT)
                } else {
                    layout.openDrawer(Gravity.LEFT)
                }
            }
            R.id.search_menu_id -> onSearchRequested()
            R.id.sync_menu_item -> syncWithInternet(getParamsLoaded().timeline, true, true)
            R.id.refresh_menu_item -> refreshFromCache()
            R.id.commands_queue_id -> startActivity(Intent(getActivity(), QueueViewer::class.java))
            R.id.manage_timelines -> startActivity(Intent(getActivity(), ManageTimelines::class.java))
            R.id.preferences_menu_id -> startMyPreferenceActivity()
            R.id.help_menu_id -> onHelp()
            R.id.debug_api -> ApiDebugger(myContext, this).debugGet()
            else -> return super.onOptionsItemSelected(item)
        }
        return false
    }

    private fun refreshFromCache() {
        if (getListData().mayHaveYoungerPage() || listView?.lastVisiblePosition ?: 0 > TimelineParameters.PAGE_SIZE / 2) {
            showList(WhichPage.CURRENT)
        } else {
            showList(WhichPage.YOUNGEST)
        }
    }

    private fun onHelp() {
        HelpActivity.startMe(this, false, HelpActivity.PAGE_CHANGELOG)
    }

    fun onItemClick(item: NoteViewItem) {
        val ma = myContext.accounts.getAccountForThisNote(
            item.getOrigin(),
            getParamsNew().getMyAccount(), item.getLinkedMyAccount(), false
        )
        MyLog.v(this) { "onItemClick, " + item + "; " + item + " account=" + ma.getAccountName() }
        if (item.getNoteId() <= 0) return
        val uri: Uri = MatchedUri.getTimelineItemUri(
            myContext.timelines[TimelineType.EVERYTHING, Actor.EMPTY, item.getOrigin()],
            item.getNoteId()
        )
        val action = intent.action
        if (Intent.ACTION_PICK == action || Intent.ACTION_GET_CONTENT == action) {
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, setData=$uri")
            }
            setResult(RESULT_OK, Intent().setData(uri))
        } else {
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, startActivity=$uri")
            }
            startActivity(MyAction.VIEW_CONVERSATION.newIntent(uri))
        }
    }

    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
        // Empty
    }

    override fun onScroll(
        view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int,
        totalItemCount: Int
    ) {
        var up = false
        if (firstVisibleItem == 0) {
            val v = listView?.getChildAt(0)
            val offset = v?.top ?: 0
            up = offset == 0
            if (up && getListData().mayHaveYoungerPage()) {
                showList(WhichPage.YOUNGER)
            }
        }
        // Idea from http://stackoverflow.com/questions/1080811/android-endless-list
        if (!up && visibleItemCount > 0
            && firstVisibleItem + visibleItemCount >= totalItemCount - 1
            && getListData().mayHaveOlderPage()
        ) {
            MyLog.d(this, "Start Loading older items, rows=$totalItemCount")
            showList(WhichPage.OLDER)
        }
    }

    private fun timelineTypeButtonText(): String {
        return TimelineTitle.from(
            myContext, getParamsLoaded().timeline,
            MyAccount.EMPTY, false, TimelineTitle.Destination.DEFAULT
        ).toString()
    }

    private fun updateAccountButtonText(drawerView: View) {
        val textView = drawerView.findViewById<TextView?>(R.id.selectAccountButton) ?: return
        val accountButtonText = myContext.accounts.currentAccount.toAccountButtonText()
        textView.text = accountButtonText
    }

    override fun onNewIntent(intent: Intent) {
        if (isFinishing) {
            MyLog.v(this) { "onNewIntent, Is finishing" }
            return
        }
        MyLog.v(this) { "onNewIntent" }
        super.onNewIntent(intent)
        parseNewIntent(intent)
        checkForInitialSync(intent)
        if (isMyResumed() || getListData().size() > 0 || isLoading()) {
            showList(getParamsNew().whichPage)
        }
    }

    private fun checkForInitialSync(intent: Intent) {
        val timeline = getParamsNew().timeline
        if (timeline.isTimeToAutoSync && timeline.getLastSyncedDate() == RelativeTime.DATETIME_MILLIS_NEVER) {
            if (intent.hasExtra(IntentExtra.INITIAL_ACCOUNT_SYNC.key)) {
                timeline.setSyncSucceededDate(System.currentTimeMillis()) // To avoid repetition
                MyServiceManager.setServiceAvailable()
                MyServiceManager.sendManualForegroundCommand(
                    CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, timeline)
                )
                MyServiceManager.sendCommand(
                    CommandData.newActorCommand(CommandEnum.GET_FRIENDS, timeline.myAccountToSync.actor, "")
                )
            } else {
                onNoRowsLoaded(timeline)
            }
        }
    }

    private fun parseNewIntent(intentNew: Intent) {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "parseNewIntent")
        }
        mRateLimitText = ""
        val whichPage: WhichPage =
            WhichPage.load(intentNew.getStringExtra(IntentExtra.WHICH_PAGE.key), WhichPage.CURRENT)
        val searchQuery = intentNew.getStringExtra(IntentExtra.SEARCH_QUERY.key)
        val parsedUri: ParsedUri = ParsedUri.fromUri(intentNew.getData())
        val timeline: Timeline = Timeline.fromParsedUri(myContext, parsedUri, searchQuery).let {
            if (it.isEmpty) myContext.timelines.getDefault().also {
                myContext.accounts.setCurrentAccount(it.myAccountToSync)
            } else it
        }
        if (onAppStart.compareAndSet(true, false)) {
            MyLog.i(this, "onAppStart")
            myContext.accounts.setCurrentAccount(timeline.myAccountToSync)
        } else {
            correctCurrentAccount(timeline)
        }
        setParamsNew(TimelineParameters(myContext, timeline, whichPage))
        actorProfileViewer?.ensureView(getParamsNew().timeline.hasActorProfile())
        if (Intent.ACTION_SEND == intentNew.getAction()) {
            sharedNote = SharedNote.fromIntent(intentNew)
            sharedNote.ifPresent { shared: SharedNote? ->
                MyLog.v(this, shared.toString())
                AccountSelector.selectAccountOfOrigin(this, ActivityRequestCode.SELECT_ACCOUNT_TO_SHARE_VIA, 0)
            }
        }
    }

    override fun updateScreen() {
        MyServiceManager.setServiceAvailable()
        invalidateOptionsMenu()
        getNoteEditor()?.updateScreen()
        updateTitle(mRateLimitText)
        mDrawerToggle?.let { toggle ->
            val currentActor = myContext.accounts.currentAccount.actor
            if (getParamsLoaded().timeline.let { it.isForMyAccount && it.actor.isSame(currentActor) }) {
                toggle.isDrawerIndicatorEnabled = false
                currentActor.avatarFile
                    .loadDrawable({ drawable: Drawable? -> scaleDrawableForToolbar(drawable) })
                    { indicator: Drawable? -> toggle.setHomeAsUpIndicator(indicator) }
            } else {
                toggle.isDrawerIndicatorEnabled = true
                toggle.setHomeAsUpIndicator(null)
            }
        }
        showRecentAccounts()
        ViewUtils.showView(
            findViewById(R.id.switchToDefaultTimelineButton), !getParamsLoaded().isAtHome()
        )
        ViewUtils.showView(
            this, R.id.collapseDuplicatesToggle,
            MyPreferences.getMaxDistanceBetweenDuplicates() > 0
        )
        MyCheckBox.setEnabled(
            this, R.id.collapseDuplicatesToggle,
            getListData().isCollapseDuplicates()
        )
        MyCheckBox.setEnabled(this, R.id.showSensitiveContentToggle, MyPreferences.isShowSensitiveContent())
        showSyncListButtons()
        showActorProfile()
    }

    private fun scaleDrawableForToolbar(drawable: Drawable?): Drawable? {
        val scaledDrawable: Drawable?
        scaledDrawable = if (drawable is BitmapDrawable) {
            val bitmap = drawable.getBitmap()
            val pixels = getListAdapter().dpToPixes(32)
            BitmapDrawable(resources, Bitmap.createScaledBitmap(bitmap, pixels, pixels, true))
        } else {
            drawable
        }
        return scaledDrawable
    }

    private fun showRecentAccounts() {
        val recentAccounts: MutableList<MyAccount> = ArrayList(myContext.accounts.recentAccounts)
        for (ind in 0..2) {
            val ma = if (recentAccounts.size > ind) recentAccounts[ind] else MyAccount.EMPTY
            val avatarView =
                findViewById<AvatarView?>(if (ind == 0) R.id.current_account_avatar_image else if (ind == 1) R.id.account_avatar_image_1 else R.id.account_avatar_image_2)
                    ?: break
            ViewUtils.showView(avatarView, ma.isValid)
            if (ma.nonValid) {
                avatarView.setImageResource(R.drawable.blank_image)
                avatarView.visibility = View.VISIBLE
                continue
            }
            ma.actor.avatarFile.showImage(this, avatarView)
            avatarView.contentDescription = ma.getAccountName()
            if (Build.VERSION.SDK_INT >= 26) {
                avatarView.tooltipText = ma.getAccountName()
            }
            avatarView.setOnClickListener(if (ind == 0) View.OnClickListener { v: View? ->
                startForTimeline(
                    myContext, this,
                    myContext.timelines.forUser(TimelineType.SENT, ma.actor)
                )
                closeDrawer()
            } else View.OnClickListener { v: View? ->
                onAccountSelected(ma)
                closeDrawer()
            }
            )
        }
    }

    override fun updateTitle(additionalTitleText: String?) {
        TimelineTitle.from(
            myContext, getParamsLoaded().timeline, MyAccount.EMPTY, true,
            TimelineTitle.Destination.TIMELINE_ACTIVITY
        ).updateActivityTitle(this, additionalTitleText)
    }

    fun getContextMenu(): NoteContextMenu? {
        return contextMenu?.note
    }

    /** Parameters of currently shown Timeline  */
    private fun getParamsLoaded(): TimelineParameters {
        return getListData().params
    }

    override fun getListData(): TimelineData<T> {
        return listData ?: TimelineData<T>(null, TimelinePage(getParamsNew(), mutableListOf()))
            .also {
                listData = it
            }
    }

    private fun setListData(pageLoaded: TimelinePage<T>): TimelineData<T> {
        return TimelineData(listData, pageLoaded).also {
            listData = it
        }
    }

    override fun showList(whichPage: WhichPage) {
        showList(whichPage, TriState.FALSE)
    }

    protected fun showList(whichPage: WhichPage, chainedRequest: TriState) {
        showList(
            TimelineParameters.clone(getReferenceParametersFor(whichPage), whichPage),
            chainedRequest
        )
    }

    private fun getReferenceParametersFor(whichPage: WhichPage): TimelineParameters {
        return when (whichPage) {
            WhichPage.OLDER -> {
                if (getListData().size() > 0) {
                    getListData().pages[getListData().pages.size - 1].params
                } else getParamsLoaded()
            }
            WhichPage.YOUNGER -> {
                if (getListData().size() > 0) {
                    getListData().pages[0].params
                } else getParamsLoaded()
            }
            WhichPage.EMPTY -> TimelineParameters(myContext, Timeline.EMPTY, WhichPage.EMPTY)
            else -> getParamsNew()
        }
    }

    /**
     * Prepare a query to the ContentProvider (to the database) and load the visible List of notes with this data
     * This is done asynchronously.
     * This method should be called from UI thread only.
     */
    protected fun showList(params: TimelineParameters, chainedRequest: TriState) {
        val method = "showList"
        if (params.isEmpty()) {
            MyLog.v(this, "$method; ignored empty request")
            return
        }
        val isDifferentRequest = params != paramsToLoad
        paramsToLoad = params
        if (isLoading() && chainedRequest != TriState.TRUE) {
            if (MyLog.isVerboseEnabled()) {
                if (isDifferentRequest) {
                    MyLog.v(this) { method + "; different while loading " + params.toSummary() }
                } else {
                    MyLog.v(this) { method + "; ignored duplicating " + params.toSummary() }
                }
            }
        } else {
            MyLog.v(this) {
                (method
                    + (if (chainedRequest == TriState.TRUE) "; chained" else "")
                    + "; requesting " + (if (isDifferentRequest) "" else "duplicating ")
                    + params.toSummary())
            }
            if (chainedRequest.untrue) saveTimelinePosition()
            disableHeaderSyncButton(R.string.loading)
            disableFooterButton(R.string.loading)
            showLoading(
                method, getText(R.string.loading).toString() + " "
                    + params.toSummary() + HORIZONTAL_ELLIPSIS
            )
            super.showList(
                chainedRequest.toBundle(
                    params.whichPage.toBundle(),
                    IntentExtra.CHAINED_REQUEST.key
                )
            )
        }
    }

    override fun newSyncLoader(args: Bundle?): SyncLoader<T> {
        val method = "newSyncLoader"
        val whichPage: WhichPage = WhichPage.load(args)
        val params = paramsToLoad?.let {
            if (whichPage == WhichPage.EMPTY) TimelineParameters(myContext, Timeline.EMPTY, whichPage) else it
        } ?: TimelineParameters(myContext, Timeline.EMPTY, whichPage)
        if (params.whichPage != WhichPage.EMPTY) {
            MyLog.v(this) { "$method; $params" }
            val intent = intent
            if (params.getContentUri() != intent.data) {
                intent.data = params.getContentUri()
            }
        }
        return TimelineLoader(params, BundleUtils.fromBundle(args, IntentExtra.INSTANCE_ID))
    }

    override fun onLoadFinished(posIn: LoadableListPosition<*>) {
        val method = "onLoadFinished"
        if (MyLog.isVerboseEnabled()) posIn.logV("$method started;")
        val dataLoaded = setListData((getLoaded() as TimelineLoader<T>).getPage())
        MyLog.v(this) { method + "; " + dataLoaded.params.toSummary() }
        val pos = if (posIn.nonEmpty && getListData().isSameTimeline &&
            isPositionRestored() && dataLoaded.params.whichPage != WhichPage.TOP
        ) posIn
        else TimelineViewPositionStorage.loadListPosition(dataLoaded.params)
        super.onLoadFinished(pos)
        if (dataLoaded.params.whichPage == WhichPage.TOP) {
            LoadableListPosition.setPosition(listView, 0)
            getListAdapter().setPositionRestored(true)
        }
        if (!isPositionRestored()) {
            TimelineViewPositionStorage(this, dataLoaded.params).restore()
        }
        val otherParams = paramsToLoad
        val isParamsChanged = otherParams != null && dataLoaded.params != otherParams
        val otherPageToRequest: WhichPage
        if (!isParamsChanged && getListData().size() < 10) {
            if (getListData().mayHaveYoungerPage()) {
                otherPageToRequest = WhichPage.YOUNGER
            } else if (getListData().mayHaveOlderPage()) {
                otherPageToRequest = WhichPage.OLDER
            } else if (!dataLoaded.params.whichPage.isYoungest()) {
                otherPageToRequest = WhichPage.YOUNGEST
            } else if (dataLoaded.params.rowsLoaded == 0) {
                otherPageToRequest = WhichPage.EMPTY
                onNoRowsLoaded(dataLoaded.params.timeline)
            } else {
                otherPageToRequest = WhichPage.EMPTY
            }
        } else {
            otherPageToRequest = WhichPage.EMPTY
        }
        hideLoading(method)
        updateScreen()
        if (isParamsChanged && otherParams != null) {
            MyLog.v(this) { method + "; Parameters changed, requesting " + otherParams.toSummary() }
            showList(otherParams, TriState.TRUE)
        } else if (otherPageToRequest != WhichPage.EMPTY) {
            MyLog.v(this) { "$method; Other page requested $otherPageToRequest" }
            showList(otherPageToRequest, TriState.TRUE)
        }
    }

    private fun addSyncButtons() {
        val listView = listView
        if (listView != null) {
            if (listView.headerViewsCount == 0) {
                listView.addHeaderView(syncYoungerView)
                disableHeaderSyncButton(R.string.loading)
            }
            if (listView.footerViewsCount == 0) {
                listView.addFooterView(syncOlderView)
                disableFooterButton(R.string.loading)
            }
        }
    }

    private fun showSyncListButtons() {
        showHeaderSyncButton()
        showFooterSyncButton()
    }

    private fun showHeaderSyncButton() {
        if (getListData().mayHaveYoungerPage()) {
            disableHeaderSyncButton(R.string.loading)
            return
        }
        if (!getParamsLoaded().timeline.isSyncableSomehow) {
            disableHeaderSyncButton(R.string.not_syncable)
            return
        }
        val stats: SyncStats =
            SyncStats.fromYoungestDates(myContext.timelines.toTimelinesToSync(getParamsLoaded().timeline))
        val format = getText(
            if (stats.itemDate > RelativeTime.SOME_TIME_AGO) R.string.sync_younger_messages
            else R.string.options_menu_sync
        ).toString()
        syncYoungerView?.let { view ->
            MyUrlSpan.showText(
                view, R.id.sync_younger_button,
                StringUtil.format(
                    format,
                    if (stats.syncSucceededDate > RelativeTime.SOME_TIME_AGO)
                        RelativeTime.getDifference(this, stats.syncSucceededDate) else getText(R.string.never),
                    DateUtils.getRelativeTimeSpanString(this, stats.itemDate)
                ),
                false,
                false
            )
            view.setEnabled(true)
        }
    }

    private fun disableHeaderSyncButton(resInfo: Int) {
        syncYoungerView?.let { view ->
            MyUrlSpan.showText(
                view, R.id.sync_younger_button,
                getText(resInfo).toString(),
                false,
                false
            )
            view.setEnabled(false)
        }
    }

    private fun showFooterSyncButton() {
        if (getListData().mayHaveOlderPage()) {
            disableFooterButton(R.string.loading)
            return
        }
        if (!getParamsLoaded().timeline.isSyncableSomehow) {
            disableFooterButton(R.string.no_more_messages)
            return
        }
        val stats: SyncStats =
            SyncStats.fromOldestDates(myContext.timelines.toTimelinesToSync(getParamsLoaded().timeline))
        syncOlderView?.let { view ->
            MyUrlSpan.showText(
                view, R.id.sync_older_button,
                StringUtil.format(
                    this, if (stats.itemDate > RelativeTime.SOME_TIME_AGO) R.string.sync_older_messages
                    else R.string.options_menu_sync,
                    DateUtils.getRelativeTimeSpanString(this, stats.itemDate)
                ),
                false,
                false
            )
            view.setEnabled(true)
        }
    }

    private fun disableFooterButton(resInfo: Int) {
        syncOlderView?.let { view ->
            MyUrlSpan.showText(
                view, R.id.sync_older_button,
                getText(resInfo).toString(),
                false,
                false
            )
            view.setEnabled(false)
        }
    }

    private fun showActorProfile() {
        if (getParamsLoaded().timeline.hasActorProfile()) {
            actorProfileViewer?.populateView()
        }
    }

    private fun onNoRowsLoaded(timeline: Timeline) {
        val ma = timeline.myAccountToSync
        if (!timeline.isSyncable || !timeline.isTimeToAutoSync || !ma.isValidAndSucceeded()) {
            return
        }
        timeline.setSyncSucceededDate(System.currentTimeMillis()) // To avoid repetition
        syncWithInternet(timeline, true, false)
    }

    protected fun syncWithInternet(timelineToSync: Timeline, syncYounger: Boolean, manuallyLaunched: Boolean) {
        myContext.timelines.toTimelinesToSync(timelineToSync)
            .forEach { timeline: Timeline -> syncOneTimeline(timeline, syncYounger, manuallyLaunched) }
    }

    private fun syncOneTimeline(timeline: Timeline, syncYounger: Boolean, manuallyLaunched: Boolean) {
        val method = "syncOneTimeline"
        if (timeline.isSyncable) {
            setCircularSyncIndicator(method, true)
            showSyncing(method, getText(R.string.options_menu_sync))
            MyServiceManager.sendForegroundCommand(
                CommandData.newTimelineCommand(
                    if (syncYounger) CommandEnum.GET_TIMELINE else CommandEnum.GET_OLDER_TIMELINE,
                    timeline
                )
                    .setManuallyLaunched(manuallyLaunched)
            )
        }
    }

    protected fun startMyPreferenceActivity() {
        startActivity(Intent(this, MySettingsActivity::class.java))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        getParamsNew().saveState(outState)
        outState.putBoolean(IntentExtra.COLLAPSE_DUPLICATES.key, getListData().isCollapseDuplicates())
        if (getListData().homeOrigin.nonEmpty) {
            outState.putLong(IntentExtra.ORIGIN_ID.key, getListData().homeOrigin.id)
        }
        contextMenu?.saveState(outState)
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int) {
        selectorActivityStub?.startActivityForResult(intent, requestCode)
            ?: super.startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        MyLog.v(this) {
            ("onActivityResult; request:" + requestCode
                + ", result:" + if (resultCode == RESULT_OK) "ok" else "fail")
        }
        if (resultCode != RESULT_OK || data == null) {
            return
        }
        when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.SELECT_ACCOUNT -> onAccountSelected(data)
            ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS -> setSelectedActingAccount(data)
            ActivityRequestCode.SELECT_ACCOUNT_TO_SHARE_VIA -> sharedNote.ifPresent { shared: SharedNote ->
                getNoteEditor()?.startEditingSharedData(
                    myContext.accounts.fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key)),
                    shared
                )
            }
            ActivityRequestCode.SELECT_TIMELINE -> {
                val timeline = myContext.timelines
                    .fromId(data.getLongExtra(IntentExtra.TIMELINE_ID.key, 0))
                if (timeline.isValid) {
                    switchView(timeline)
                }
            }
            ActivityRequestCode.SELECT_ORIGIN -> onOriginSelected(data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onAccountSelected(data: Intent) {
        onAccountSelected(myContext.accounts.fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key)))
    }

    private fun onAccountSelected(ma: MyAccount) {
        if (ma.isValid) {
            myContext.accounts.setCurrentAccount(ma)
            switchView(
                if (getParamsLoaded().timeline.isCombined) getParamsLoaded().timeline else getParamsLoaded().timeline.fromMyAccount(
                    myContext,
                    ma
                )
            )
        }
    }

    private fun onOriginSelected(data: Intent) {
        val origin = myContext.origins.fromName(data.getStringExtra(IntentExtra.ORIGIN_NAME.key))
        if (origin.isValid && getParamsLoaded().timeline.hasActorProfile()) {
            updateList(LoadableListViewParameters.fromOrigin(origin))
        }
    }

    private fun setSelectedActingAccount(data: Intent) {
        val ma = myContext.accounts.fromAccountName(
            data.getStringExtra(IntentExtra.ACCOUNT_NAME.key)
        )
        if (ma.isValid) {
            val contextMenu = getContextMenu(
                data.getIntExtra(IntentExtra.MENU_GROUP.key, MyContextMenu.MENU_GROUP_NOTE)
            )
            contextMenu.setSelectedActingAccount(ma)
            contextMenu.showContextMenu()
        }
    }

    private fun getContextMenu(menuGroup: Int): MyContextMenu {
        return when (menuGroup) {
            MyContextMenu.MENU_GROUP_ACTOR -> contextMenu?.actor
            MyContextMenu.MENU_GROUP_OBJACTOR -> contextMenu?.objActor
            MyContextMenu.MENU_GROUP_ACTOR_PROFILE -> if (actorProfileViewer == null) contextMenu?.note
            else actorProfileViewer?.contextMenu
            else -> contextMenu?.note
        } ?: throw IllegalStateException("No context menu for $menuGroup")
    }

    override fun isEditorVisible(): Boolean {
        return getNoteEditor()?.isVisible() ?: false
    }

    override fun isCommandToShowInSyncIndicator(commandData: CommandData): Boolean {
        return when (commandData.command) {
            CommandEnum.GET_TIMELINE,
            CommandEnum.GET_OLDER_TIMELINE,
            CommandEnum.GET_ATTACHMENT,
            CommandEnum.GET_AVATAR,
            CommandEnum.UPDATE_NOTE,
            CommandEnum.UPDATE_MEDIA,
            CommandEnum.DELETE_NOTE,
            CommandEnum.LIKE,
            CommandEnum.UNDO_LIKE,
            CommandEnum.FOLLOW,
            CommandEnum.UNDO_FOLLOW,
            CommandEnum.ANNOUNCE,
            CommandEnum.UNDO_ANNOUNCE -> true
            else -> false
        }
    }

    override fun canSwipeRefreshChildScrollUp(): Boolean {
        return getListData().mayHaveYoungerPage() || super.canSwipeRefreshChildScrollUp()
    }

    override fun onReceiveAfterExecutingCommand(commandData: CommandData) {
        when (commandData.command) {
            CommandEnum.RATE_LIMIT_STATUS -> if (commandData.result.hourlyLimit > 0) {
                mRateLimitText = (commandData.result.remainingHits.toString() + "/"
                    + commandData.result.hourlyLimit)
                updateTitle(mRateLimitText)
            }
            else -> {
            }
        }
        if (commandData.command.isGetTimeline) {
            setCircularSyncIndicator("onReceiveAfterExecutingCommand ", false)
        }
        if (!TextUtils.isEmpty(syncingText)) {
            if (MyServiceManager.getServiceState() != MyServiceState.RUNNING) {
                hideSyncing("Service is not running")
            } else if (isCommandToShowInSyncIndicator(commandData)) {
                showSyncing("After executing " + commandData.command, HORIZONTAL_ELLIPSIS)
            }
        }
        super.onReceiveAfterExecutingCommand(commandData)
    }

    public override fun isRefreshNeededAfterExecuting(commandData: CommandData): Boolean {
        var needed = super.isRefreshNeededAfterExecuting(commandData)
        when (commandData.command) {
            CommandEnum.GET_TIMELINE, CommandEnum.GET_OLDER_TIMELINE -> {
                if (!getParamsLoaded().isLoaded
                    || getParamsLoaded().getTimelineType() != commandData.getTimelineType()
                ) {
                    // Nothing
                } else if (commandData.result.downloadedCount > 0) {
                    needed = true
                } else {
                    showSyncListButtons()
                }
            }
            else -> {
            }
        }
        return needed
    }

    override fun isAutoRefreshNow(onStop: Boolean): Boolean {
        return super.isAutoRefreshNow(onStop) && MyPreferences.isRefreshTimelineAutomatically()
    }

    override fun onNoteEditorVisibilityChange() {
        hideSyncing("onNoteEditorVisibilityChange")
        super.onNoteEditorVisibilityChange()
    }

    override fun getTimeline(): Timeline {
        return getParamsLoaded().timeline
    }

    fun switchView(timeline: Timeline) {
        timeline.save(myContext)
        correctCurrentAccount(timeline)

        if (isFinishing || timeline != getParamsLoaded().timeline) {
            MyLog.v(this) { "switchTimelineActivity; $timeline" }
            if (isFinishing) {
                val intent = getIntentForTimeline(myContext, timeline, false)
                myContextHolder.initialize(this).thenStartActivity("${instanceTag}Start", intent)
            } else {
                startForTimeline(myContext, this, timeline)
            }
        } else {
            showList(WhichPage.CURRENT)
        }
    }

    private fun correctCurrentAccount(timeline: Timeline) {
        if (!timeline.isCombined && timeline.origin != myContext.accounts.currentAccount.origin) {
            MyLog.d(
                this,
                "Correcting account ${myContext.accounts.currentAccount.getAccountName()} -> ${timeline.myAccountToSync.getAccountName()}"
            )
            myContext.accounts.setCurrentAccount(timeline.myAccountToSync)
        }
    }

    fun setParamsNew(params: TimelineParameters): TimelineParameters {
        paramsNew = params
        return params
    }

    fun getParamsNew(): TimelineParameters {
        return paramsNew ?: TimelineParameters(myContext, Timeline.EMPTY, WhichPage.EMPTY).also {
            paramsNew = it
        }
    }

    fun setSelectorActivityStub(selectorActivityStub: SelectorActivityStub?) {
        this.selectorActivityStub = selectorActivityStub
    }

    companion object {
        val HORIZONTAL_ELLIPSIS: String = "\u2026"
        val onAppStart: AtomicBoolean = AtomicBoolean(true)

        @JvmOverloads
        fun startForTimeline(
            myContext: MyContext, context: Context, timeline: Timeline, clearTask: Boolean = false,
            initialAccountSync: Boolean = false
        ) {
            timeline.save(myContext)
            val intent = getIntentForTimeline(myContext, timeline, clearTask)
            if (initialAccountSync) {
                intent.putExtra(IntentExtra.INITIAL_ACCOUNT_SYNC.key, true)
            }
            context.startActivity(intent)
        }

        private fun getIntentForTimeline(myContext: MyContext, timeline: Timeline, clearTask: Boolean): Intent {
            val intent =
                Intent(myContext.context, if (clearTask) FirstActivity::class.java else TimelineActivity::class.java)
            intent.data = timeline.getUri()
            if (clearTask) {
                // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return intent
        }

        private fun <T : ViewItem<T>> clearNotifications(timelineActivity: TimelineActivity<T>) {
            val timeline = timelineActivity.getParamsLoaded().timeline
            object : AsyncRunnable(
                "clearNotifications" + timeline.id,
                AsyncEnum.QUICK_UI
            ) {
                override suspend fun doInBackground(params: Unit): Try<Unit> {
                    timelineActivity.myContext.clearNotifications(timeline)
                    return TryUtils.SUCCESS
                }

                override suspend fun onPostExecute(result: Try<Unit>) {
                    timelineActivity.refreshFromCache()
                }
            }.execute(timelineActivity, Unit)
        }
    }
}
