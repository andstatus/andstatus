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
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;

/**
 * Simplified implementation inspired by {@link android.content.Loader}
 * available in API >= 11
 * 
 * @author yvolk@yurivolkov.com
 */
public class TimelineCursorLoader extends MyLoader<Cursor> {
    TimelineListParameters params;
    Cursor mCursor = null;

    public TimelineCursorLoader() {
        super(MyContextHolder.get().context());
    }

    final Object asyncLoaderLock = new Object();
    @GuardedBy("asyncLoaderLock")
    AsyncLoader asyncLoader = null;

    /**
     * Clean after successful or failed operation
     */
    private void asyncLoaderEnded(Cursor cursor) {
        // Do we really need this?
        this.mCursor = cursor;
        if (params.cancelled || cursor == null) {
            deliverCancellation();
        } else {
            deliverResult(cursor);
        }
        synchronized (asyncLoaderLock) {
            asyncLoader = null;
        }
    }

    private void cancelAsyncTask(final String method) {
        synchronized (asyncLoaderLock) {
            if (asyncLoader != null && asyncLoader.getStatus() == Status.RUNNING) {
                MyLog.v(this, method + " task is running. Cancelling");
                if (asyncLoader.cancel(true)) {
                    MyLog.v(this, method + " task cancelled");
                } else {
                    MyLog.d(this, method + " couldn't cancel task");
                }
            }
            asyncLoader = null;
        }
    }

    @Override
    protected void onReset() {
        cancelAsyncTask("onReset");
    }

    @Override
    protected void onStartLoading() {
        final String method = "onStartLoading";
        synchronized (asyncLoaderLock) {
            cancelAsyncTask(method);
            if (asyncLoader == null) {
                try {
                    asyncLoader = new AsyncLoader(params);
                    asyncLoader.execute();
                } catch (Exception e) {
                    MyLog.e(this, method, e);
                    asyncLoaderEnded(null);
                }
            }
        }
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
    

    /**
     * @author yvolk@yurivolkov.com
     */
    private class AsyncLoader extends AsyncTask<Void, Void, Cursor> {
        TimelineListParameters params;

        public AsyncLoader(TimelineListParameters params) {
            super();
            this.params = params;
        }

        @Override
        protected void onPreExecute() {
            params.startTime = System.nanoTime();
            params.cancelled = false;
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, (TextUtils.isEmpty(params.searchQuery) ? ""
                        : "queryString=\"" + params.searchQuery + "\"; ")
                        + params.timelineType
                        + "; isCombined=" + (params.timelineCombined ? "yes" : "no"));
            }
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            prepareQueryInBackground();
            return queryDatabase();
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

        @Override
        protected void onPostExecute(Cursor result) {
            params.cancelled = isCancelled();
            TimelineCursorLoader.this.asyncLoaderEnded(result);
        }

        @Override
        protected void onCancelled(Cursor result) {
            params.cancelled = true;
            TimelineCursorLoader.this.asyncLoaderEnded(null);
        }

    }
}
