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
import android.os.AsyncTask;
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
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.support.android.v11.app.MyLoader;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

/**
 * Simplified implementation inspired by {@link android.content.Loader}
 * available in API >= 11
 * 
 * @author yvolk@yurivolkov.com
 */
public class TimelineCursorLoader extends MyLoader<Cursor> implements MyServiceListener {
    TimelineListParameters params;
    private Cursor mCursor = null;

    private int instanceId = InstanceId.next();
    private MyServiceReceiver serviceConnector;

    final Object asyncLoaderLock = new Object();
    @GuardedBy("asyncLoaderLock")
    AsyncLoader asyncLoader = null;

    public TimelineCursorLoader() {
        super(MyContextHolder.get().context());
        serviceConnector = new MyServiceReceiver(this);
    }

    @Override
    protected void onStartLoading() {
        final String method = "onStartLoading";
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method + ", " + params);
        }
        serviceConnector.registerReceiver(getContext());
        if (mayReuseResult()) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) MyLog.v(this, method + " reusing result");
            asyncLoaderEnded(mCursor);
        } else if (params.reQuery || taskIsNotRunning()) {
            restartLoader();
        } else {
            
        }
    }

    private boolean taskIsNotRunning() {
        boolean isNotRunning = true;
        synchronized (asyncLoaderLock) {
            if (asyncLoader != null) {
                isNotRunning = (asyncLoader.getStatus() != AsyncTask.Status.RUNNING);
            }
        }
        return isNotRunning;
    }

    private boolean mayReuseResult() {
        boolean ok = false;
        if (!params.reQuery && !takeContentChanged() && mCursor != null && !mCursor.isClosed()) {
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
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) MyLog.v(this, method +  ", status:" + getAsyncLoaderStatus());
        synchronized (asyncLoaderLock) {
            if (cancelAsyncTask(method)) {
                try {
                    asyncLoader = new AsyncLoader();
                    asyncLoader.execute(params);
                } catch (Exception e) {
                    MyLog.e(this, method, e);
                    ended = true;
                    asyncLoader = null;
                }
            }
        }
        if (ended) {
            asyncLoaderEnded(null);
        }
    }
    
    /**
     * Clean after successful or failed operation
     */
    private void asyncLoaderEnded(Cursor cursor) {
        Cursor cursorPrev = null;
        try {
            if (this.mCursor != cursor) {
                cursorPrev = this.mCursor; 
                this.mCursor = cursor;
            }
            if (params.cancelled || cursor == null) {
                deliverCancellation();
            } else {
                deliverResult(cursor);
            }
        } finally {
            DbUtils.closeSilently(cursorPrev, null);
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
    
    private final static long MIN_LIST_REQUERY_MILLISECONDS = 3000;
    private long previousRequeryTime = 0;
    @Override
    protected void onForceLoad() {
        if (isStarted()
                && System.currentTimeMillis() - previousRequeryTime > MIN_LIST_REQUERY_MILLISECONDS) {
            previousRequeryTime = System.currentTimeMillis();
            params.reQuery = true;
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
        DbUtils.closeSilently(mCursor);
        mCursor = null;
    }
    
    /**
     * @author yvolk@yurivolkov.com
     */
    private class AsyncLoader extends AsyncTask<TimelineListParameters, Void, Cursor> {
        TimelineListParameters params;

        @Override
        protected Cursor doInBackground(TimelineListParameters... params) {
            this.params = params[0];
            markStart();
            prepareQueryInBackground();
            Cursor cursor = queryDatabase();
            checkIfReloadIsNeeded(cursor);
            return cursor;
        }

        private void markStart() {
            params.startTime = System.nanoTime();
            params.cancelled = false;
            params.timelineToReload = TimelineTypeEnum.UNKNOWN;
            
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, (TextUtils.isEmpty(params.searchQuery) ? ""
                        : "queryString=\"" + params.searchQuery + "\"; ")
                        + params.timelineType
                        + "; isCombined=" + (params.timelineCombined ? "yes" : "no"));
            }
        }
        
        private void prepareQueryInBackground() {
            if (params.lastItemId > 0) {
                params.sa.addSelection(MyProvider.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENT_DATE
                        + " >= ?",
                        new String[] {
                            String.valueOf(MyProvider.msgIdToLongColumnValue(
                                    MyDatabase.Msg.SENT_DATE, params.lastItemId))
                        });
            }
        }

        private Cursor queryDatabase() {
            Cursor cursor = null;
            for (int attempt = 0; attempt < 3 && !isCancelled(); attempt++) {
                try {
                    cursor = MyContextHolder.get().context().getContentResolver()
                            .query(params.contentUri, params.projection, params.sa.selection,
                                    params.sa.selectionArgs, params.sortOrder);
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
            if (!params.loadOneMorePage && cursor != null && !cursor.isClosed() && cursor.getCount() == 0) {
                switch (params.timelineType) {
                    case USER:
                    case FOLLOWING_USER:
                        // This timeline doesn't update automatically so let's do it now if necessary
                        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(params.timelineType, params.selectedUserId);
                        if (latestTimelineItem.isTimeToAutoUpdate()) {
                            params.timelineToReload = params.timelineType;
                        }
                        break;
                    default:
                        if ( MyProvider.userIdToLongColumnValue(User.HOME_TIMELINE_DATE, params.myAccountUserId) == 0) {
                            // This is supposed to be a one time task.
                            params.timelineToReload = TimelineTypeEnum.ALL;
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
            params.cancelled = true;
            singleEnd(null);
        }

        private void singleEnd(Cursor result) {
            logExecutionStats(result);
            TimelineCursorLoader.this.asyncLoaderEnded(result);
        }
        
        private void logExecutionStats(Cursor cursor) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                StringBuilder text = new StringBuilder((params.cancelled ? "cancelled" : "ended"));
                if (!params.cancelled) {
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
                text.append(", " + Double.valueOf((System.nanoTime() - params.startTime)/1.0E6).longValue() + " ms");
                MyLog.v(this, text.toString());
            }
        }
    }

    @Override
    public void onReceive(CommandData commandData) {
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
}
