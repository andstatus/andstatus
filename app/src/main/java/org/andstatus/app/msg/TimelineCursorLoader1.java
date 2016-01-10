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
import android.os.AsyncTask;
import android.os.AsyncTask.Status;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineCursorLoader1 extends Loader<Cursor> implements MyServiceEventsListener {
    private final TimelineListParameters mParams;
    private Cursor mCursor = null;

    private final long instanceId = InstanceId.next();
    private final MyServiceEventsReceiver serviceConnector;

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
    private class AsyncLoader extends AsyncTask<Void, Void, Void> {
        volatile TimelineLoader loader;

        @Override
        protected Void doInBackground(Void... voidParams) {
            loader = new TimelineLoader(getParams());
            loader.allowLoadingFromInternet();
            loader.load(null);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            singleEnd(loader);
        }

        @Override
        protected void onCancelled(Void result) {
            getParams().cancelled = true;
            singleEnd(loader);
        }

        private void singleEnd(TimelineLoader result) {
            if (result != null) {
                result.logExecutionStats();
                // TODO delete this class after its function replication
                // TimelineCursorLoader1.this.deliverResultsAndClean(result.cursorLoaded);
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
