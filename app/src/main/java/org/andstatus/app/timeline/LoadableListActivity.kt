/* 
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import net.jcip.annotations.GuardedBy
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.ParsedUri
import org.andstatus.app.list.MyBaseListActivity
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceEvent
import org.andstatus.app.service.MyServiceEventsListener
import org.andstatus.app.service.MyServiceEventsReceiver
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.BundleUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TriState
import org.andstatus.app.widget.MySearchView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * List, loaded asynchronously. Updated by MyService
 *
 * @author yvolk@yurivolkov.com
 */
abstract class LoadableListActivity<T : ViewItem<T?>?> : MyBaseListActivity(), MyServiceEventsListener {
    protected var showSyncIndicatorSetting = true
    protected var textualSyncIndicator: View? = null
    protected var syncingText: CharSequence? = ""
    protected var loadingText: CharSequence? = ""
    private var onRefreshHandled = false
    private var parsedUri: ParsedUri = ParsedUri.fromUri(Uri.EMPTY)
    protected var myContext: MyContext =  MyContextHolder.myContextHolder.getNow()
    private var configChangeTime: Long = 0
    var myServiceReceiver: MyServiceEventsReceiver? = null
    private val loaderLock: Any = Any()

    @GuardedBy("loaderLock")
    private var mCompletedLoader: AsyncLoader = AsyncLoader()

    @GuardedBy("loaderLock")
    private var mWorkingLoader = mCompletedLoader

    @GuardedBy("loaderLock")
    private var loaderIsWorking = false
    var lastLoadedAt: Long = 0
    protected val refreshNeededSince: AtomicLong = AtomicLong(0)
    protected val refreshNeededAfterForegroundCommand: AtomicBoolean = AtomicBoolean(false)
    protected var mSubtitle: CharSequence? = ""

    /**
     * Id of current list item, which is sort of a "center" of the list view
     */
    protected var centralItemId: Long = 0
    protected var searchView: MySearchView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        myContext =  MyContextHolder.myContextHolder.getNow()
        super.onCreate(savedInstanceState)
        if (restartMeIfNeeded()) return
        textualSyncIndicator = findViewById(R.id.sync_indicator)
        configChangeTime = myContext.preferencesChangeTime()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "onCreate, config changed " + RelativeTime.secondsAgo(configChangeTime) + " seconds ago"
                    + if ( MyContextHolder.myContextHolder.getNow().isReady()) "" else ", MyContext is not ready"
            )
        }
        if (myContext.isReady()) {
            MyServiceManager.Companion.setServiceAvailable()
        }
        myServiceReceiver = MyServiceEventsReceiver(myContext, this)
        parsedUri = ParsedUri.Companion.fromIntent(intent)
        centralItemId = getParsedUri().getItemId()
    }

    protected fun getParsedUri(): ParsedUri {
        return parsedUri
    }

    open fun getListData(): TimelineData<T?> {
        return getListAdapter().getListData()
    }

    open fun showList(whichPage: WhichPage) {
        showList(whichPage.toBundle())
    }

    protected fun showList(args: Bundle?) {
        val whichPage: WhichPage = WhichPage.load(args)
        val chainedRequest: TriState = TriState.Companion.fromBundle(args, IntentExtra.CHAINED_REQUEST)
        val msgLog = StringBuilder("showList" + (if (chainedRequest == TriState.TRUE) ", chained" else "")
                + ", " + whichPage + " page"
                + if (centralItemId == 0L) "" else ", center:$centralItemId")
        if (whichPage == WhichPage.EMPTY) {
            MyLog.v(this) { "Ignored Empty page request: $msgLog" }
        } else {
            MyLog.v(this) { "Started $msgLog" }
            synchronized(loaderLock) {
                if (isLoading() && chainedRequest != TriState.TRUE) {
                    msgLog.append(", Ignored $mWorkingLoader")
                } else {
                    val newLoader = AsyncLoader(instanceTag())
                    if (AsyncTaskLauncher<Bundle?>().execute(this, newLoader, args).isSuccess) {
                        mWorkingLoader = newLoader
                        loaderIsWorking = true
                        refreshNeededSince.set(0)
                        refreshNeededAfterForegroundCommand.set(false)
                        msgLog.append(", Launched")
                    } else {
                        msgLog.append(", Couldn't launch")
                    }
                }
            }
            MyLog.v(this, "Ended $msgLog")
        }
    }

    fun isLoading(): Boolean {
        var reset = false
        synchronized(loaderLock) {
            if (loaderIsWorking && mWorkingLoader.getStatus() == AsyncTask.Status.FINISHED) {
                reset = true
                loaderIsWorking = false
            }
        }
        if (reset) {
            MyLog.d(this, "WorkingLoader finished but didn't reset loaderIsWorking flag "
                    + mWorkingLoader)
        }
        return loaderIsWorking
    }

    protected fun isContextNeedsUpdate(): Boolean {
        val myContextNew: MyContext =  MyContextHolder.myContextHolder.getNow()
        return !myContext.isReady() || myContext !== myContextNew || configChangeTime != myContextNew.preferencesChangeTime()
    }

    /** @return selectedItem or EmptyViewItem
     */
    fun saveContextOfSelectedItem(v: View?): ViewItem<*> {
        val position = getListAdapter().getPosition(v)
        setPositionOfContextMenu(position)
        if (position >= 0) {
            val viewItem: Any? = getListAdapter().getItem(position)
            if (viewItem is ViewItem<*>) {
                return viewItem
            } else if (viewItem != null) {
                MyLog.i(this, "Unexpected type of selected item: " + viewItem.javaClass + ", " + viewItem)
            }
        }
        return EmptyViewItem.EMPTY
    }

    fun getActivity(): LoadableListActivity<*> {
        return this
    }

    interface ProgressPublisher {
        fun publish(progress: String?)
    }

    /** Called not in UI thread  */
    protected abstract fun newSyncLoader(args: Bundle?): SyncLoader<T?>?
    private inner class AsyncLoader : MyAsyncTask<Bundle?, String?, SyncLoader<*>?>, ProgressPublisher {
        private var mSyncLoader: SyncLoader<*>? = null

        constructor(taskId: String?) : super(taskId, PoolEnum.LONG_UI) {}
        constructor() : super(PoolEnum.LONG_UI) {}

        fun getSyncLoader(): SyncLoader<*>? {
            return if (mSyncLoader == null) newSyncLoader(null) else mSyncLoader
        }

        override fun doInBackground2(bundle: Bundle?): SyncLoader<*>? {
            publishProgress("...")
            val loader: SyncLoader<*>? = newSyncLoader(BundleUtils.toBundle(bundle, IntentExtra.INSTANCE_ID.key, instanceId))
            loader.allowLoadingFromInternet()
            loader.load(this)
            return loader
        }

        override fun publish(progress: String?) {
            publishProgress(progress)
        }

        override fun onProgressUpdate(vararg values: String?) {
            updateTitle(values[0])
        }

        override fun onCancelled2(syncLoader: SyncLoader<*>?) {
            resetIsWorkingFlag()
        }

        private fun resetIsWorkingFlag() {
            synchronized(loaderLock) {
                if (mWorkingLoader === this) {
                    loaderIsWorking = false
                }
            }
        }

        override fun onPostExecute2(loader: SyncLoader<*>?) {
            mSyncLoader = loader
            updateCompletedLoader()
            try {
                if (isMyResumed()) {
                    onLoadFinished(getCurrentListPosition())
                }
            } catch (e: Exception) {
                MyLog.d(this, "onPostExecute", e)
            }
            val endedAt = System.currentTimeMillis()
            val timeTotal = endedAt - createdAt
            MyLog.v(this) {
                ("Load completed, " +
                        ( mSyncLoader?.size()?.toString() ?: "?") + " items, " +
                        timeTotal + "ms total, " +
                        (endedAt - backgroundEndedAt) + "ms on UI thread")
            }
            resetIsWorkingFlag()
        }

        override fun toString(): String {
            return super.toString() + if (mSyncLoader == null) "" else "; $mSyncLoader"
        }
    }

    fun getCurrentListPosition(): LoadableListPosition<*> {
        return LoadableListPosition.getCurrent(getListView(), getListAdapter(), centralItemId)
    }

    open fun onLoadFinished(pos: LoadableListPosition<*>?) {
        updateList(pos)
        updateTitle("")
        if (onRefreshHandled) {
            onRefreshHandled = false
            hideSyncing("onLoadFinished")
        }
    }

    fun updateList(pos: LoadableListPosition<*>?) {
        updateList(pos, LoadableListViewParameters.Companion.EMPTY, true)
    }

    fun updateList(viewParameters: LoadableListViewParameters?) {
        updateList(getCurrentListPosition(), viewParameters, false)
    }

    private fun updateList(pos: LoadableListPosition<*>?, viewParameters: LoadableListViewParameters?, newAdapter: Boolean) {
        val method = "updateList"
        val list = listView ?: return
        if (MyLog.isVerboseEnabled()) pos.logV(method + "; Before " + if (newAdapter) "setting new adapter" else "notifying change")
        val adapter = if (newAdapter) newListAdapter() else listAdapter
        if (viewParameters.isViewChanging()) {
            adapter.getListData().updateView(viewParameters)
        }
        if (newAdapter) {
            setListAdapter(adapter)
        } else {
            adapter.notifyDataSetChanged()
        }
        adapter.setPositionRestored(LoadableListPosition.Companion.restore(list, adapter, pos))
        if (viewParameters.isViewChanging()) {
            updateScreen()
        }
    }

    open fun updateScreen() {
        // Empty
    }

    protected abstract fun newListAdapter(): BaseTimelineAdapter<T?>?
    override fun getListAdapter(): BaseTimelineAdapter<T?> {
        return super.getListAdapter() as BaseTimelineAdapter<T?>
    }

    private fun updateCompletedLoader() {
        synchronized(loaderLock) { mCompletedLoader = mWorkingLoader }
        lastLoadedAt = System.currentTimeMillis()
    }

    protected open fun updateTitle(progress: String?) {
        val title = StringBuilder(getCustomTitle())
        if (!progress.isNullOrEmpty()) {
            MyStringBuilder.Companion.appendWithSpace(title, progress)
        }
        setTitle(title.toString())
        setSubtitle(mSubtitle)
    }

    protected open fun getCustomTitle(): CharSequence? {
        return title
    }

    protected fun getLoaded(): SyncLoader<*> {
        synchronized(loaderLock) { return mCompletedLoader.getSyncLoader() }
    }

    override fun onResume() {
        val method = "onResume"
        super.onResume()
        val isFinishing = restartMeIfNeeded()
        MyLog.v(this) { method + if (isFinishing) ", and finishing" else "" }
        if (!isFinishing) {
            myServiceReceiver.registerReceiver(this)
            myContext.setInForeground(true)
            if (!isLoading()) {
                showList(WhichPage.ANY)
            }
        }
    }

    override fun onContentChanged() {
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            MyLog.d(this, "onContentChanged started")
        }
        super.onContentChanged()
    }

    override fun onPause() {
        super.onPause()
        if (myServiceReceiver != null) {
            myServiceReceiver.unregisterReceiver(this)
        }
         MyContextHolder.myContextHolder.getNow().setInForeground(false)
        listAdapter.isPositionRestored = false
    }

    override fun onReceive(commandData: CommandData?, event: MyServiceEvent?) {
        when (event) {
            MyServiceEvent.BEFORE_EXECUTING_COMMAND -> if (isCommandToShowInSyncIndicator(commandData)) {
                showSyncing(commandData)
            }
            MyServiceEvent.PROGRESS_EXECUTING_COMMAND -> if (isCommandToShowInSyncIndicator(commandData)) {
                showSyncing("Show Progress", commandData.toCommandProgress( MyContextHolder.myContextHolder.getNow()))
            }
            MyServiceEvent.AFTER_EXECUTING_COMMAND -> onReceiveAfterExecutingCommand(commandData)
            MyServiceEvent.ON_STOP -> hideSyncing("onReceive STOP")
            else -> {
            }
        }
        if (isAutoRefreshNow(event == MyServiceEvent.ON_STOP)) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Auto refresh on content change")
            }
            showList(WhichPage.CURRENT)
        }
    }

    private fun showSyncing(commandData: CommandData?) {
        AsyncTaskLauncher<CommandData?>().execute(this,
                object : MyAsyncTask<CommandData?, Void?, String?>("ShowSyncing" + getInstanceId(), PoolEnum.QUICK_UI) {
                    override fun doInBackground2(commandData: CommandData?): String? {
                        return commandData.toCommandSummary(myContext)
                    }

                    override fun onPostExecute2(result: String?) {
                        showSyncing("Show " + commandData.getCommand(),
                                getText(R.string.title_preference_syncing).toString() + ": " + result)
                    }

                    override fun toString(): String? {
                        return "ShowSyncing " + super.toString()
                    }
                }, commandData)
    }

    protected open fun isCommandToShowInSyncIndicator(commandData: CommandData?): Boolean {
        return false
    }

    protected open fun onReceiveAfterExecutingCommand(commandData: CommandData?) {
        if (isRefreshNeededAfterExecuting(commandData)) {
            refreshNeededSince.compareAndSet(0, System.currentTimeMillis())
            refreshNeededAfterForegroundCommand.compareAndSet(false, commandData.isInForeground())
        }
    }

    /**
     * @return true if needed, false means "don't know"
     */
    protected open fun isRefreshNeededAfterExecuting(commandData: CommandData?): Boolean {
        var needed = false
        when (commandData.getCommand()) {
            CommandEnum.GET_NOTE, CommandEnum.GET_CONVERSATION, CommandEnum.GET_FOLLOWERS, CommandEnum.GET_FRIENDS -> if (commandData.getResult().downloadedCount > 0) {
                needed = true
            }
            CommandEnum.GET_ACTOR, CommandEnum.UPDATE_NOTE, CommandEnum.FOLLOW, CommandEnum.UNDO_FOLLOW, CommandEnum.LIKE, CommandEnum.UNDO_LIKE, CommandEnum.ANNOUNCE, CommandEnum.UNDO_ANNOUNCE, CommandEnum.DELETE_NOTE, CommandEnum.GET_ATTACHMENT, CommandEnum.GET_AVATAR -> if (!commandData.getResult().hasError()) {
                needed = true
            }
            else -> {
            }
        }
        return needed
    }

    protected open fun isAutoRefreshNow(onStop: Boolean): Boolean {
        if (refreshNeededSince.get() == 0L) {
            return false
        } else if (isLoading()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Ignoring content change while loading")
            }
            return false
        } else if (!onStop && !refreshNeededAfterForegroundCommand.get() &&
                !RelativeTime.wasButMoreSecondsAgoThan(lastLoadedAt, NO_AUTO_REFRESH_AFTER_LOAD_SECONDS)) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Ignoring background content change - loaded recently")
            }
            return false
        }
        return true
    }

    public override fun onDestroy() {
        MyLog.v(this, "onDestroy")
        if (myServiceReceiver != null) {
            myServiceReceiver.unregisterReceiver(this)
        }
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item.getItemId()) {
            R.id.sync_menu_item -> showList(BundleUtils.toBundle(WhichPage.CURRENT.toBundle(), IntentExtra.SYNC.key, 1L))
            R.id.refresh_menu_item -> showList(WhichPage.CURRENT)
            else -> return super.onOptionsItemSelected(item)
        }
        return false
    }

    fun isPositionRestored(): Boolean {
        return listAdapter.isPositionRestored
    }

    protected fun showSyncing(source: String?, text: CharSequence?) {
        if (!showSyncIndicatorSetting || isEditorVisible()) {
            return
        }
        syncingText = text
        updateTextualSyncIndicator(source)
    }

    override fun hideSyncing(source: String?) {
        syncingText = ""
        updateTextualSyncIndicator(source)
        super.hideSyncing(source)
    }

    protected fun showLoading(source: String?, text: String?) {
        if (!showSyncIndicatorSetting) {
            return
        }
        loadingText = text
        updateTextualSyncIndicator(source)
    }

    protected fun hideLoading(source: String?) {
        loadingText = ""
        updateTextualSyncIndicator(source)
    }

    protected fun updateTextualSyncIndicator(source: String?) {
        if (textualSyncIndicator == null) {
            return
        }
        val isVisible = (!TextUtils.isEmpty(loadingText) || !TextUtils.isEmpty(syncingText)) && !isEditorVisible()
        if (isVisible) {
            (findViewById<View?>(R.id.sync_text) as TextView).text = if (TextUtils.isEmpty(loadingText)) syncingText else loadingText
        }
        if (if (isVisible) textualSyncIndicator.visibility != View.VISIBLE else textualSyncIndicator.visibility == View.VISIBLE) {
            MyLog.v(this) { "$source set textual Sync indicator to $isVisible" }
            textualSyncIndicator.setVisibility(if (isVisible) View.VISIBLE else View.GONE)
        }
    }

    protected open fun isEditorVisible(): Boolean {
        return false
    }

    override fun onRefresh() {
        onRefreshHandled = true
        showList(WhichPage.CURRENT)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (searchView != null && searchView.visibility == View.VISIBLE) {
                searchView.onActionViewCollapsed()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val NO_AUTO_REFRESH_AFTER_LOAD_SECONDS: Long = 5
    }
}