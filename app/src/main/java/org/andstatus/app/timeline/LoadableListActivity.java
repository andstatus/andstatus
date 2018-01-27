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

package org.andstatus.app.timeline;

import android.net.Uri;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.list.MyBaseListActivity;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TriState;
import org.andstatus.app.widget.MySearchView;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * List, loaded asynchronously. Updated by MyService
 * 
 * @author yvolk@yurivolkov.com
 */
public abstract class LoadableListActivity<T extends ViewItem<T>> extends MyBaseListActivity implements MyServiceEventsListener {

    protected boolean showSyncIndicatorSetting = true;
    protected View textualSyncIndicator = null;
    protected CharSequence syncingText = "";
    protected CharSequence loadingText = "";
    private boolean onRefreshHandled = false;

    ParsedUri mParsedUri = ParsedUri.fromUri(Uri.EMPTY);

    protected MyContext myContext = MyContextHolder.get();
    private MyAccount ma = MyAccount.EMPTY;
    private long configChangeTime = 0;
    MyServiceEventsReceiver myServiceReceiver;

    private final Object loaderLock = new Object();
    @GuardedBy("loaderLock")
    private AsyncLoader mCompletedLoader = new AsyncLoader();
    @GuardedBy("loaderLock")
    private AsyncLoader mWorkingLoader = mCompletedLoader;
    @GuardedBy("loaderLock")
    private boolean loaderIsWorking = false;

    long lastLoadedAt = 0;
    protected final AtomicLong refreshNeededSince = new AtomicLong(0);
    protected final AtomicBoolean refreshNeededAfterForegroundCommand = new AtomicBoolean(false);
    private static final long NO_AUTO_REFRESH_AFTER_LOAD_SECONDS = 5;

    protected CharSequence mSubtitle = "";
    /**
     * Id of current list item, which is sort of a "center" of the list view
     */
    protected long centralItemId = 0;
    protected MySearchView searchView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        myContext = MyContextHolder.get();
        if (!myContext.isReady()) {
            MyContextHolder.initializeThenRestartMe(this);
        }
        super.onCreate(savedInstanceState);
        if (isFinishing()) {
            return;
        }

        textualSyncIndicator = findViewById(R.id.sync_indicator);

        configChangeTime = myContext.preferencesChangeTime();
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "onCreate, config changed " + RelativeTime.secondsAgo(configChangeTime) + " seconds ago"
                    + (MyContextHolder.get().isReady() ? "" : ", MyContext is not ready")
            );
        }

        if (myContext.isReady()) {
            MyServiceManager.setServiceAvailable();
        }
        myServiceReceiver = new MyServiceEventsReceiver(myContext, this);

        mParsedUri = ParsedUri.fromIntent(getIntent());
        setCurrentMyAccount(getParsedUri().getAccountUserId(), getParsedUri().getOriginId());
        centralItemId = getParsedUri().getItemId();
    }

    protected ParsedUri getParsedUri() {
        return mParsedUri;
    }

    @NonNull
    public TimelineData<T> getListData() {
        return new TimelineData<T>(null,
                new TimelinePage<>(new TimelineParameters(myContext), null)) {
            @Override
            public int size() {
                return getListAdapter() == null ? 0 : getListAdapter().getCount();
            }
        };
    }

    public void showList(WhichPage whichPage) {
        showList(whichPage.toBundle());
    }

    protected void showList(Bundle args) {
        WhichPage whichPage = WhichPage.load(args);
        TriState chainedRequest = TriState.fromBundle(args, IntentExtra.CHAINED_REQUEST);
        String msgLog = "showList" + (chainedRequest == TriState.TRUE ? ", chained" : "")
                + ", " + whichPage + " page"
                + (centralItemId == 0 ? "" : ", center:" + centralItemId);
        if (whichPage == WhichPage.EMPTY) {
            MyLog.v(this, "Ignored Empty page request: " + msgLog);
        } else {
            MyLog.v(this, "Started " + msgLog);
            synchronized (loaderLock) {
                if (isLoading() && chainedRequest != TriState.TRUE) {
                    msgLog = "Ignored " + msgLog + ", " + mWorkingLoader;
                } else {
                    AsyncLoader newLoader = new AsyncLoader(MyLog.getInstanceTag(this));
                    if (new AsyncTaskLauncher<Bundle>().execute(this, true, newLoader, args)) {
                        mWorkingLoader = newLoader;
                        loaderIsWorking = true;
                        refreshNeededSince.set(0);
                        refreshNeededAfterForegroundCommand.set(false);
                        msgLog = "Launched, " + msgLog;
                    } else {
                        msgLog = "Couldn't launch, " + msgLog;
                    }
                }
            }
            MyLog.v(this, "Ended " + msgLog);
        }
    }

    public boolean isLoading() {
        boolean reset = false;
        synchronized (loaderLock) {
            if (loaderIsWorking && mWorkingLoader.getStatus() == Status.FINISHED) {
                reset = true;
                loaderIsWorking = false;
            }
        }
        if (reset) {
            MyLog.d(this, "WorkingLoader finished but didn't reset loaderIsWorking flag "
                    + mWorkingLoader);
        }
        return loaderIsWorking;
    }

    protected boolean isConfigChanged() {
        MyContext myContextNew = MyContextHolder.get();
        return this.myContext != myContextNew || configChangeTime != myContextNew.preferencesChangeTime();
    }

    /** @return selectedItem or EmptyViewItem */
    @NonNull
    public ViewItem saveContextOfSelectedItem(View v) {
        int position = -1;
        if (getListAdapter() != null) {
            position = getListAdapter().getPosition(v);
        }
        setPositionOfContextMenu(position);
        if (position >= 0) {
            Object viewItem = getListAdapter().getItem(position);
            if (viewItem != null) {
                if (ViewItem.class.isAssignableFrom(viewItem.getClass())) {
                    return (ViewItem) viewItem;
                } else {
                    MyLog.i(this, "Unexpected type of selected item: " + viewItem.getClass() + ", " + viewItem);
                }
            }
        }
        return EmptyViewItem.EMPTY;
    }

    protected void setCurrentMyAccount(long accountId, long originId) {
        setCurrentMyAccount(myContext.persistentAccounts().fromActorId(accountId),
                myContext.persistentOrigins().fromId(originId));
    }

    public void setCurrentMyAccount(MyAccount ma, Origin origin) {
        if (ma != null && ma.isValid()) {
            this.ma = ma;
        } else {
            if (origin != null && origin.isValid()) {
                if (!this.ma.isValid() || !this.ma.getOrigin().equals(origin)) {
                    this.ma = myContext.persistentAccounts().getFirstSucceededForOrigin(origin);
                }
            } else if (!getCurrentMyAccount().isValid()) {
                this.ma = myContext.persistentAccounts().getCurrentAccount();
            }
        }
    }

    public MyContext getMyContext() {
        return myContext;
    }

    public LoadableListActivity getActivity() {
        return this;
    }

    public interface ProgressPublisher {
        void publish(String progress);
    }

    /** Called not in UI thread */
    protected abstract SyncLoader<T> newSyncLoader(Bundle args);
    
    private class AsyncLoader extends MyAsyncTask<Bundle, String, SyncLoader> implements LoadableListActivity.ProgressPublisher {
        private SyncLoader mSyncLoader = null;

        public AsyncLoader(String taskId) {
            super(taskId, PoolEnum.LONG_UI);
        }

        public AsyncLoader() {
            super(PoolEnum.LONG_UI);
        }

        SyncLoader getSyncLoader() {
            return mSyncLoader == null ? newSyncLoader(null) : mSyncLoader;
        }

        @Override
        protected SyncLoader doInBackground2(Bundle... params) {
            publishProgress("...");
            SyncLoader loader = newSyncLoader(BundleUtils.toBundle(params[0], IntentExtra.INSTANCE_ID.key, instanceId));
            if (ma.isValidAndSucceeded()) {
                loader.allowLoadingFromInternet();
            }
            loader.load(this);
            return loader;
        }

        @Override
        public void publish(String progress) {
            publishProgress(progress);
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            updateTitle(values[0]);
        }

        @Override
        protected void onCancelled2(SyncLoader syncLoader) {
            resetIsWorkingFlag();
        }

        private void resetIsWorkingFlag() {
            synchronized (loaderLock) {
                if (mWorkingLoader == this) {
                    loaderIsWorking = false;
                }
            }
        }

        @Override
        protected void onPostExecute2(SyncLoader loader) {
            mSyncLoader = loader;
            updateCompletedLoader();
            try {
                if (isMyResumed()) {
                    onLoadFinished(true);
                }
            } catch (Exception e) {
                MyLog.d(this,"onPostExecute", e);
            }
            long endedAt = System.currentTimeMillis();
            long timeTotal = endedAt - createdAt;
            MyLog.v(this, "Load completed, " + (mSyncLoader == null ? "?" : mSyncLoader.size()) + " items, "
                    + timeTotal + "ms total, "
                    + (endedAt - backgroundEndedAt) + "ms on UI thread");
            resetIsWorkingFlag();
        }

        @Override
        public String toString() {
            return super.toString() + (mSyncLoader == null ? "" : "; " + mSyncLoader);
        }
    }

    public void onLoadFinished(boolean keepCurrentPosition) {
        if (keepCurrentPosition) {
            updateList(TriState.UNKNOWN, 0, true);
        } else {
            setListAdapter(newListAdapter());
        }
        updateTitle("");
        if (onRefreshHandled) {
            onRefreshHandled = false;
            hideSyncing("onLoadFinished");
        }
    }

    public void updateList(TriState collapseDuplicates, long itemId, boolean newAdapter) {
        final String method = "updateList";
        BaseTimelineAdapter adapter = getListAdapter();
        ListView list = getListView();
        // For a finer position restore see http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview?rq=1
        long itemIdOfAdapterPosition = centralItemId;
        int y = 0;
        if (list.getChildCount() > list.getHeaderViewsCount() + list.getFooterViewsCount() && adapter != null) {
            int firstVisibleAdapterPosition = Integer.max(list.getFirstVisiblePosition() - list.getHeaderViewsCount(), 0);
            itemIdOfAdapterPosition = adapter.getItemId(firstVisibleAdapterPosition);
            y = getYOfPosition(list, adapter, firstVisibleAdapterPosition);
        }

        if (!TriState.UNKNOWN.equals(collapseDuplicates)) {
            getListData().collapseDuplicates(collapseDuplicates.toBoolean(true), itemId);
        }

        if (newAdapter) {
            adapter = newListAdapter();
            verboseListPositionLog(method, "Before setting new adapter", itemIdOfAdapterPosition);
            setListAdapter(adapter);
        } else if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (adapter != null) {
            boolean positionRestored = false;
            if (itemIdOfAdapterPosition >= 0) {
                int firstVisibleAdapterPosition = adapter.getPositionById(itemIdOfAdapterPosition);
                if (firstVisibleAdapterPosition >= 0) {
                    list.setSelectionFromTop(firstVisibleAdapterPosition + list.getHeaderViewsCount(), y);
                    positionRestored = true;
                    verboseListPositionLog(method, "After setting position: " + firstVisibleAdapterPosition,
                            itemIdOfAdapterPosition);
                }
            }
            adapter.setPositionRestored(positionRestored);
        }
    }

    protected void verboseListPositionLog(String method, String description, long itemId) {
        if (MyLog.isVerboseEnabled()) {
            int firstVisibleAdapterPosition = getListView().getFirstVisiblePosition() - getListView().getHeaderViewsCount();
            MyLog.d(this, method + "; " + description
                    + ", adapter count:" + (getListAdapter() == null ? "(no adapter)" : getListAdapter().getCount())
                    + ", list items:" + getListView().getChildCount()
                    + (itemId != 0 ? ", itemId:" + itemId
                        + " -> first position:" + (getListAdapter() == null ? "(no adapter)"
                            : getListAdapter().getPositionById(itemId))
                        : ", first position:" + firstVisibleAdapterPosition
                        + " -> itemId:" + (getListAdapter() == null ? "(no adapter)"
                            : getListAdapter().getItemId(firstVisibleAdapterPosition))
                    )
            );
        }
    }

    public static int getYOfPosition(ListView list, BaseTimelineAdapter adapter, int position) {
        int y = 0;
        int zeroChildPosition = adapter.getPosition(list.getChildAt(list.getHeaderViewsCount()));
        if (position - zeroChildPosition != 0) {
            MyLog.v("getYOfPosition", "pos:" + position + ", zeroChildPos:" + zeroChildPosition);
        }
        View viewOfPosition = list.getChildAt(position - zeroChildPosition + list.getHeaderViewsCount());
        if (viewOfPosition != null) {
            y  = viewOfPosition.getTop() - list.getPaddingTop();
        }
        return y;
    }

    protected abstract BaseTimelineAdapter newListAdapter();

    @Override
    public BaseTimelineAdapter<T> getListAdapter() {
        return (BaseTimelineAdapter<T>) super.getListAdapter();
    }

    private void updateCompletedLoader() {
        synchronized(loaderLock) {
            mCompletedLoader = mWorkingLoader;
        }
        lastLoadedAt = System.currentTimeMillis();
    }
    
    protected void updateTitle(String progress) {
        StringBuilder title = new StringBuilder(getCustomTitle());
        if (!TextUtils.isEmpty(progress)) {
            I18n.appendWithSpace(title, progress);
        }
        setTitle(title.toString());
        setSubtitle(mSubtitle);
    }

    protected CharSequence getCustomTitle() {
        return getTitle();
    }
    
    @NonNull
    protected SyncLoader getLoaded() {
        synchronized(loaderLock) {
            return mCompletedLoader.getSyncLoader();
        }
    }
    
    @Override
    protected void onResume() {
        String method = "onResume";
        super.onResume();
        MyLog.v(this, method + (mFinishing ? ", finishing" : "") );
        if (!mFinishing && !MyContextHolder.initializeThenRestartMe(this)) {
            myServiceReceiver.registerReceiver(this);
            myContext.setInForeground(true);
            if (getListData().size() == 0 && !isLoading()) {
                showList(WhichPage.ANY);
            }
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
        super.onPause();
        myServiceReceiver.unregisterReceiver(this);
        MyContextHolder.get().setInForeground(false);
    }
    
    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        switch (event) {
            case BEFORE_EXECUTING_COMMAND:
                if (isCommandToShowInSyncIndicator(commandData)) {
                    showSyncing(commandData);
                }
                break;
            case PROGRESS_EXECUTING_COMMAND:
                if (isCommandToShowInSyncIndicator(commandData)) {
                    showSyncing("Show Progress",
                            commandData.toCommandProgress(MyContextHolder.get()));
                }
                break;
            case AFTER_EXECUTING_COMMAND:
                onReceiveAfterExecutingCommand(commandData);
                break;
            case ON_STOP:
                hideSyncing("onReceive STOP");
                break;
            default:
                break;
        }
        if (isAutoRefreshNow(event == MyServiceEvent.ON_STOP)) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Auto refresh on content change");
            }
            showList(WhichPage.CURRENT);
        }
    }

    private void showSyncing(final CommandData commandData) {
        new AsyncTaskLauncher<CommandData>().execute(this, true,
                new MyAsyncTask<CommandData, Void, String>("ShowSyncing" + getInstanceId(), MyAsyncTask.PoolEnum.QUICK_UI) {

                    @Override
                    protected String doInBackground2(CommandData... commandData) {
                        return commandData[0].toCommandSummary(MyContextHolder.get());
                    }

                    @Override
                    protected void onPostExecute2(String result) {
                        showSyncing("Show " + commandData.getCommand(),
                                getText(R.string.title_preference_syncing) + ": " + result);
                    }

                    @Override
                    public String toString() {
                        return "ShowSyncing " + super.toString();
                    }

                }
                , commandData);
    }

    protected boolean isCommandToShowInSyncIndicator(CommandData commandData) {
        return false;
    }

    protected void onReceiveAfterExecutingCommand(CommandData commandData) {
        if (isRefreshNeededAfterExecuting(commandData)) {
            refreshNeededSince.compareAndSet(0, System.currentTimeMillis());
            refreshNeededAfterForegroundCommand.compareAndSet(false, commandData.isInForeground());
        }
    }

    /**
     * @return true if needed, false means "don't know"
     */
    protected boolean isRefreshNeededAfterExecuting(CommandData commandData) {
        boolean needed = false;
        switch(commandData.getCommand()) {
            case GET_STATUS:
            case GET_CONVERSATION:
			case GET_FOLLOWERS:
            case GET_FRIENDS:
                if (commandData.getResult().getDownloadedCount() > 0) {
                    needed = true;
                }
                break;
            case GET_USER:
            case UPDATE_STATUS:
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case REBLOG:
            case DESTROY_REBLOG:
            case DESTROY_STATUS:
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
                if (!commandData.getResult().hasError()) {
                    needed = true;
                }
                break;
            default:
                break;
        }
        return needed;
    }

    protected boolean isAutoRefreshNow(boolean onStop) {
        if (refreshNeededSince.get() == 0) {
            return false;
        } else if (isLoading()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Ignoring content change while loading");
            }
            return false;
        } else if (!onStop && !refreshNeededAfterForegroundCommand.get() &&
                !RelativeTime.wasButMoreSecondsAgoThan(lastLoadedAt, NO_AUTO_REFRESH_AFTER_LOAD_SECONDS)) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Ignoring background content change - loaded recently");
            }
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        MyLog.v(this, "onDestroy");
        if (myServiceReceiver != null) {
            myServiceReceiver.unregisterReceiver(this);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sync_menu_item:
                showList(BundleUtils.toBundle(WhichPage.CURRENT.toBundle(), IntentExtra.SYNC.key, 1L));
                break;
            case R.id.refresh_menu_item:
                showList(WhichPage.CURRENT);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    public MyAccount getCurrentMyAccount() {
        return ma;
    }

    public boolean isPositionRestored() {
        return getListAdapter() != null && getListAdapter().isPositionRestored();
    }

    protected void showSyncing(String source, CharSequence text) {
        if (!showSyncIndicatorSetting || isEditorVisible()) {
            return;
        }
        syncingText = text;
        updateTextualSyncIndicator(source);
    }

    @Override
    protected void hideSyncing(String source) {
        syncingText = "";
        updateTextualSyncIndicator(source);
        super.hideSyncing(source);
    }

    protected void showLoading(String source, String text) {
        if (!showSyncIndicatorSetting) {
            return;
        }
        loadingText = text;
        updateTextualSyncIndicator(source);
    }

    protected void hideLoading(String source) {
        loadingText = "";
        updateTextualSyncIndicator(source);
    }

    protected void updateTextualSyncIndicator(String source) {
        if (textualSyncIndicator == null) {
            return;
        }
        boolean isVisible = !TextUtils.isEmpty(loadingText) || !TextUtils.isEmpty(syncingText);
        if (isVisible) {
            isVisible = !isEditorVisible();
        }
        if (isVisible) {
            ((TextView) findViewById(R.id.sync_text)).setText(TextUtils.isEmpty(loadingText) ? syncingText : loadingText );
        }
        if (isVisible ? (textualSyncIndicator.getVisibility() != View.VISIBLE) : ((textualSyncIndicator.getVisibility() == View.VISIBLE))) {
            MyLog.v(this, source + " set textual Sync indicator to " + isVisible);
            textualSyncIndicator.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
    }

    protected boolean isEditorVisible() {
        return false;
    }

    @Override
    public void onRefresh() {
        onRefreshHandled = true;
        showList(WhichPage.CURRENT);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (searchView != null && searchView.getVisibility() == View.VISIBLE) {
                searchView.onActionViewCollapsed();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }


}
