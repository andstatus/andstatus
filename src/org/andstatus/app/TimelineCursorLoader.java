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

package org.andstatus.app;

import android.database.Cursor;
import android.os.AsyncTask.Status;
import android.text.TextUtils;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.support.android.v11.app.MyLoader;
import org.andstatus.app.support.android.v11.os.AsyncTask;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

/**
 * Simplified implementation inspired by {@link android.content.Loader}
 * available in API >= 11
 * 
 * @author yvolk@yurivolkov.com
 */
public class TimelineCursorLoader extends MyLoader<Cursor> implements MyServiceListener {
    private final TimelineListParameters mParams;
    private Cursor mCursor = null;

    private long instanceId = InstanceId.next();
    private MyServiceReceiver serviceConnector;

    private final Object asyncLoaderLock = new Object();
    @GuardedBy("asyncLoaderLock")
    private AsyncLoader asyncLoader = null;

    public TimelineCursorLoader(TimelineListParameters params) {
        super(MyContextHolder.get().context());
        this.mParams = params;
        serviceConnector = new MyServiceReceiver(this);
    }

    @Override
    protected void onStartLoading() {
        final String method = "onStartLoading";
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method + ", " + getParams());
        }
        serviceConnector.registerReceiver(getContext());
        if (mayReuseResult()) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + " reusing result");
            }
            deliverResultsAndClean(mCursor);
        } else if (getParams().reQuery || taskIsNotRunning()) {
            restartLoader();
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
        if (!getParams().reQuery && !takeContentChanged() && mCursor != null && !mCursor.isClosed()) {
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
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method +  ", status:" + getAsyncLoaderStatus());
        }
        synchronized (asyncLoaderLock) {
            if (cancelAsyncTask(method)) {
                try {
                    asyncLoader = new AsyncLoader();
                    asyncLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } catch (Exception e) {
                    MyLog.e(this, method, e);
                    ended = true;
                    asyncLoader = null;
                }
            }
        }
        if (ended) {
            deliverResultsAndClean(null);
        }
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
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, callerMethod + "-cancelAsyncTask status:" + getAsyncLoaderStatus());
            }
            if (asyncLoader != null && asyncLoader.getStatus() == Status.RUNNING) {
                if (asyncLoader.cancel(true)) {
                    MyLog.v(this, callerMethod + " task cancelled");
                } else {
                    MyLog.d(this, callerMethod + " couldn't cancel task");
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
            getParams().reQuery = true;
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
            return cursor;
        }

        private void markStart() {
            getParams().startTime = System.nanoTime();
            getParams().cancelled = false;
            getParams().timelineToReload = TimelineTypeEnum.UNKNOWN;
            
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, (TextUtils.isEmpty(getParams().searchQuery) ? ""
                        : "queryString=\"" + getParams().searchQuery + "\"; ")
                        + getParams().timelineType
                        + "; isCombined=" + (getParams().timelineCombined ? "yes" : "no"));
            }
        }
        
        private void prepareQueryInBackground() {
            if (getParams().lastItemId > 0) {
                getParams().sa.addSelection(MyProvider.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENT_DATE
                        + " >= ?",
                        new String[] {
                            String.valueOf(MyProvider.msgIdToLongColumnValue(
                                    MyDatabase.Msg.SENT_DATE, getParams().lastItemId))
                        });
            }
        }

        private Cursor queryDatabase() {
            Cursor cursor = null;
            for (int attempt = 0; attempt < 3 && !isCancelled(); attempt++) {
                try {
                    cursor = MyContextHolder.get().context().getContentResolver()
                            .query(getParams().contentUri, getParams().projection, getParams().sa.selection,
                                    getParams().sa.selectionArgs, getParams().sortOrder);
                    break;
                } catch (IllegalStateException e) {
                    MyLog.d(this, "Attempt " + attempt + " to prepare cursor", e);
                    DbUtils.closeSilently(cursor);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e2) {
                        MyLog.d(this, "Attempt " + attempt + " to prepare cursor was interrupted",
                                e2);
                        break;
                    }
                }
            }
            return cursor;
        }
        
        private void checkIfReloadIsNeeded(Cursor cursor) {
            if (!getParams().loadOneMorePage && cursor != null && !cursor.isClosed() && cursor.getCount() == 0) {
                switch (getParams().timelineType) {
                    case USER:
                    case FOLLOWING_USER:
                        // This timeline doesn't update automatically so let's do it now if necessary
                        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(getParams().timelineType, getParams().selectedUserId);
                        if (latestTimelineItem.isTimeToAutoUpdate()) {
                            getParams().timelineToReload = getParams().timelineType;
                        }
                        break;
                    default:
                        if ( MyProvider.userIdToLongColumnValue(User.HOME_TIMELINE_DATE, getParams().myAccountUserId) == 0) {
                            // This is supposed to be a one time task.
                            getParams().timelineToReload = TimelineTypeEnum.ALL;
                        } 
                        break;
                }
            }
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
            TimelineCursorLoader.this.deliverResultsAndClean(result);
        }
        
        private void logExecutionStats(Cursor cursor) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
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
                text.append(", " + Double.valueOf((System.nanoTime() - getParams().startTime)/1.0E6).longValue() + " ms");
                MyLog.v(this, text.toString());
            }
        }
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        if (event != MyServiceEvent.AFTER_EXECUTING_COMMAND) {
            return;
        }
        final String method = "onReceive";
        MyLog.v(this, method + ": " + commandData);
        switch (commandData.getCommand()) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case GET_STATUS:
            case SEARCH_MESSAGE:
                if (commandData.getResult().getDownloadedCount() > 0) {
                    if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                        MyLog.v(this, method + ": Content changed, downloaded " + commandData.getResult().getDownloadedCount());
                    }
                    onContentChanged();
                }
                break;
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case DESTROY_REBLOG:
            case DESTROY_STATUS:
            case REBLOG:
            case UPDATE_STATUS:
                if (!commandData.getResult().hasError()) {
                    if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                        MyLog.v(this, method + ": Content changed, command " + commandData.getCommand());
                    }
                    onContentChanged();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("instance:" + instanceId + ",");
        sb.append("id:" + getId() + ",");
        return MyLog.formatKeyValue(this, sb.toString());
    }

    TimelineListParameters getParams() {
        return mParams;
    }
}
