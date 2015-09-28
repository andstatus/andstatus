/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Loader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.text.TextUtils;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TypedCursorValue;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineCursorLoader1 extends Loader<Cursor> implements MyServiceEventsListener {
    private final TimelineListParameters mParams;
    private Cursor mCursor = null;

    private long instanceId = InstanceId.next();
    private MyServiceEventsReceiver serviceConnector;

    private final Object asyncLoaderLock = new Object();
    @GuardedBy("asyncLoaderLock")
    private AsyncLoader asyncLoader = null;

    public TimelineCursorLoader1(TimelineListParameters params) {
        super(MyContextHolder.get().context());
        this.mParams = params;
        serviceConnector = new MyServiceEventsReceiver(this);
    }

    @Override
    protected void onStartLoading() {
        final String method = "onStartLoading";
        logV(method, getParams());
        serviceConnector.registerReceiver(getContext());
        if (mayReuseResult()) {
            logV(method, "reusing result");
            deliverResultsAndClean(mCursor);
        } else if (getParams().mReQuery || taskIsNotRunning()) {
            restartLoader();
        }
    }

    private void logV(String method, Object obj) {
        if (MyLog.isVerboseEnabled()) {
            String message = (obj != null) ? obj.toString() : "";
            MyLog.v(this, String.valueOf(instanceId) + " " + method + "; " + message);
        }
    }
    
    private boolean taskIsNotRunning() {
        boolean isNotRunning = true;
        synchronized (asyncLoaderLock) {
            if (asyncLoader != null) {
                isNotRunning = (asyncLoader.getStatus() != Status.RUNNING);
            }
        }
        return isNotRunning;
    }

    private boolean mayReuseResult() {
        boolean ok = false;
        if (!getParams().mReQuery && !takeContentChanged() && mCursor != null && !mCursor.isClosed()) {
            synchronized (asyncLoaderLock) {
                if (asyncLoader == null) {
                    ok = true;
                }
            }
        }
        return ok;
    }
    
    private void restartLoader() {
        final String method = "restartLoader";
        boolean ended = false;
        synchronized (asyncLoaderLock) {
            if (MyLog.isVerboseEnabled() && asyncLoader != null) {
                logV(method, "status:" + getAsyncLoaderStatus());
            }
            if (cancelAsyncTask(method)) {
                try {
                    asyncLoader = new AsyncLoader();
                    asyncLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } catch (Exception e) {
                    logD(method, "", e);
                    ended = true;
                    asyncLoader = null;
                }
            }
        }
        if (ended) {
            logV(method, "deliver null as result");
            deliverResultsAndClean(null);
        }
    }
    
    private void logD(String method, String message, Throwable tr) {
        MyLog.d(this, String.valueOf(instanceId) + " " + method + "; " + message, tr);
    }
    
    private void deliverResultsAndClean(Cursor cursor) {
        Cursor cursorPrev = null;
        try {
            if (this.mCursor != cursor) {
                cursorPrev = this.mCursor; 
                this.mCursor = cursor;
            }
            if (getParams().cancelled || cursor == null) {
                deliverCancellation();
            } else {
                deliverResult(cursor);
            }
        } finally {
            DbUtils.closeSilently(cursorPrev, "asyncLoaderEnded");
            synchronized (asyncLoaderLock) {
                asyncLoader = null;
            }
        }
    }
    
    private boolean cancelAsyncTask(String callerMethod) {
        boolean cancelled = false;
        synchronized (asyncLoaderLock) {
            if (MyLog.isVerboseEnabled() && asyncLoader != null) {
                logV(callerMethod + "-cancelAsyncTask", "status:" + getAsyncLoaderStatus());
            }
            if (asyncLoader != null && asyncLoader.getStatus() == Status.RUNNING) {
                if (asyncLoader.cancel(true)) {
                    logV(callerMethod, "task cancelled");
                } else {
                    logV(callerMethod, "couldn't cancel task");
                }
            }
            asyncLoader = null;
            cancelled = true;
        }
        return cancelled;
    }
    
    private String getAsyncLoaderStatus() {
        String status = "null";
        synchronized (asyncLoaderLock) {
            if (asyncLoader != null) {
                status = asyncLoader.getStatus().name();
            } 
        }
        return status;
    }

    @Override
    protected void onStopLoading() {
        cancelAsyncTask("onStopLoading");
    }
    
    @Override
    protected boolean onCancelLoad() {
        cancelAsyncTask("onCancelLoad");
        return true;
    }
    
    private static final long MIN_LIST_REQUERY_MILLISECONDS = 3000;
    private long previousRequeryTime = 0;
    @Override
    protected void onForceLoad() {
        if (isStarted()
                && System.currentTimeMillis() - previousRequeryTime > MIN_LIST_REQUERY_MILLISECONDS) {
            previousRequeryTime = System.currentTimeMillis();
            getParams().mReQuery = true;
            onStartLoading();
        }
    }
    
    @Override
    protected void onReset() {
        serviceConnector.unregisterReceiver(getContext());
        disposeResult();
        cancelAsyncTask("onReset");
    }

    private void disposeResult() {
        DbUtils.closeSilently(mCursor, "disposeResult");
        mCursor = null;
    }
    
    /**
     * @author yvolk@yurivolkov.com
     */
    private class AsyncLoader extends AsyncTask<Void, Void, Cursor> {
        
        @Override
        protected Cursor doInBackground(Void... voidParams) {
            markStart();
            prepareQueryInBackground();
            Cursor cursor = queryDatabase();
            checkIfReloadIsNeeded(cursor);
            return applyFilters(cursor);
        }

        private void markStart() {
            getParams().startTime = System.nanoTime();
            getParams().cancelled = false;
            getParams().timelineToReload = TimelineType.UNKNOWN;
            getParams().rowsFilteredOut = 0;
            
            if (MyLog.isVerboseEnabled()) {
                logV("markStart", (TextUtils.isEmpty(getParams().mSearchQuery) ? ""
                        : "queryString=\"" + getParams().mSearchQuery + "\"; ")
                        + getParams().mTimelineType
                        + "; isCombined=" + (getParams().mTimelineCombined ? "yes" : "no"));
            }
        }
        
        private void prepareQueryInBackground() {
            if (getParams().mLastItemSentDate > 0) {
                getParams().mSa.addSelection(ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENT_DATE
                                + " >= ?",
                        new String[]{
                                String.valueOf(getParams().mLastItemSentDate)
                        });
            }
        }

        private Cursor queryDatabase() {
            final String method = "queryDatabase";
            Cursor cursor = null;
            for (int attempt = 0; attempt < 3 && !isCancelled(); attempt++) {
                try {
                    cursor = MyContextHolder.get().context().getContentResolver()
                            .query(getParams().mContentUri, getParams().mProjection, getParams().mSa.selection,
                                    getParams().mSa.selectionArgs, getParams().mSortOrder);
                    break;
                } catch (IllegalStateException e) {
                    logD(method, "Attempt " + attempt + " to prepare cursor", e);
                    DbUtils.closeSilently(cursor);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e2) {
                        logD(method, "Attempt " + attempt + " to prepare cursor was interrupted",
                                e2);
                        break;
                    }
                }
            }
            return cursor;
        }
        
        private void checkIfReloadIsNeeded(Cursor cursor) {
            if (noMessagesInATimeline(cursor)) {
                switch (getParams().mTimelineType) {
                    case USER:
                        // This timeline doesn't update automatically so let's do it now if necessary
                        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(getParams().mTimelineType, getParams().mSelectedUserId);
                        if (latestTimelineItem.isTimeToAutoUpdate()) {
                            getParams().timelineToReload = getParams().mTimelineType;
                        }
                        break;
                    case FOLLOWING_USER:
                        // This timeline doesn't update automatically so let's do it now if necessary
                        latestTimelineItem = new LatestTimelineItem(getParams().mTimelineType, getParams().myAccountUserId);
                        if (latestTimelineItem.isTimeToAutoUpdate()) {
                            getParams().timelineToReload = getParams().mTimelineType;
                        }
                        break;
                    default:
                        if ( MyQuery.userIdToLongColumnValue(User.HOME_TIMELINE_DATE, getParams().myAccountUserId) == 0) {
                            // This is supposed to be a one time task.
                            getParams().timelineToReload = TimelineType.ALL;
                        } 
                        break;
                }
            }
        }

        private boolean noMessagesInATimeline(Cursor cursor) {
            return !getParams().mLoadOneMorePage
                    && TextUtils.isEmpty(getParams().mSearchQuery)
                    && cursor != null && !cursor.isClosed() && cursor.getCount() == 0;
        }

        private Cursor applyFilters(Cursor cursor) {
            KeywordsFilter keywordsFilter = new KeywordsFilter(
                    MyPreferences.getString(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS, ""));
            if (keywordsFilter.isEmpty()) {
                if (cursor != null) {
                    getParams().rowsLoaded = cursor.getCount();
                }
                return cursor;
            }
            long startTime = System.currentTimeMillis();
            int rowsCount = 0;
            int filteredOutCount = 0;
            MatrixCursor cursorOut = new MatrixCursor(cursor.getColumnNames());
            if (cursor != null && !cursor.isClosed()) {
                try {
                    int indBody = cursor.getColumnIndex(MyDatabase.Msg.BODY);
                    if (cursor.moveToFirst()) {
                        do {
                            rowsCount++;
                            Object[] row = new Object[cursor.getColumnCount()];
                            boolean skip = false;
                            String body = "";
                            for (int ind = 0; ind < cursor.getColumnCount(); ind++) {
                                row[ind] = new TypedCursorValue(cursor, ind).value;
                                if (ind == indBody) {
                                    body = row[ind].toString();
                                    skip = keywordsFilter.matched(body);
                                }
                            }
                            if (skip) {
                                filteredOutCount++;
                                if (MyLog.isVerboseEnabled()) {
                                    MyLog.v(this, filteredOutCount + " Filtered out: " + I18n.trimTextAt(body, 40));
                                }
                            } else {
                                cursorOut.addRow(row);
                            }
                        } while (cursor.moveToNext());
                    }
                } finally {
                    cursor.close();
                }
            }
            MyLog.d(this, "Filtered out " + filteredOutCount + " of " + rowsCount + " rows, "
                    + (System.currentTimeMillis() - startTime) + "ms" );
            getParams().rowsLoaded = rowsCount;
            getParams().rowsFilteredOut = filteredOutCount;
            return cursorOut;
        }

        @Override
        protected void onPostExecute(Cursor result) {
            singleEnd(result);
        }

        @Override
        protected void onCancelled(Cursor result) {
            getParams().cancelled = true;
            singleEnd(null);
        }

        private void singleEnd(Cursor result) {
            logExecutionStats(result);
            TimelineCursorLoader1.this.deliverResultsAndClean(result);
        }
        
        private void logExecutionStats(Cursor cursor) {
            if (MyLog.isVerboseEnabled()) {
                StringBuilder text = new StringBuilder(getParams().cancelled ? "cancelled" : "ended");
                if (!getParams().cancelled) {
                    String cursorInfo;
                    if (cursor == null) {
                        cursorInfo = "cursor is null";
                    } else if (cursor.isClosed()) {
                        cursorInfo = "cursor is Closed";
                    } else {
                        cursorInfo = cursor.getCount() + " rows";
                    }
                    text.append(", " + cursorInfo);
                }
                text.append(", " + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - getParams().startTime) + " ms");
                logV("stats", text.toString());
            }
        }
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        if (event != MyServiceEvent.AFTER_EXECUTING_COMMAND) {
            return;
        }
        final String method = "onReceive";
        switch (commandData.getCommand()) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
                if (mParams.mTimelineType != commandData.getTimelineType()) {
                    break;
                }
            case GET_STATUS:
            case SEARCH_MESSAGE:
                if (commandData.getResult().getDownloadedCount() > 0) {
                    if (MyLog.isVerboseEnabled()) {
                        logV(method, "Content changed, " + commandData.toString());
                    }
                    onContentChanged();
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
                    if (MyLog.isVerboseEnabled()) {
                        logV(method, "Content changed, " + commandData.toString());
                    }
                    onContentChanged();
                }
                break;
            default:
                break;
        }
    }
    
    @Override
    public void onContentChanged() {
        if (taskIsNotRunning()) {
            super.onContentChanged();
        } else {
            logV("onContentChanged", "ignoring because task is running");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("instance:" + instanceId + ",");
        sb.append("id:" + getId() + ",");
        return MyLog.formatKeyValue(this, sb.toString());
    }

    public TimelineListParameters getParams() {
        return mParams;
    }
}
