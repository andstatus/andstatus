/*
 * Copyright (C) 2015-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor;
import android.support.annotation.NonNull;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;

/**
* @author yvolk@yurivolkov.com
*/
public class TimelineLoader<T extends ViewItem<T>> extends SyncLoader<T> {
    private final TimelineParameters params;
    private final TimelinePage<T> page;

    private final long instanceId;

    protected TimelineLoader(@NonNull TimelineParameters params, long instanceId) {
        this.params = params;
        this.page = new TimelinePage<T>(getParams(), new ArrayList<>());
        this.items = page.items;
        this.instanceId = instanceId;
    }

    @Override
    public void load(LoadableListActivity.ProgressPublisher publisher) {
        markStart();
        if (params.whichPage != WhichPage.EMPTY) {
            filter(loadFromCursor(queryDatabase()));
        }
        params.endTime = System.nanoTime();
        logExecutionStats();
    }

    private void markStart() {
        params.startTime = System.nanoTime();
        params.cancelled = false;
        params.timeline.save(params.getMyContext());
        if (MyLog.isVerboseEnabled()) {
            logV("markStart", params.toSummary());
        }
    }

    private Cursor queryDatabase() {
        final String method = "queryDatabase";
        logV(method, "started");
        Cursor cursor = null;
        for (int attempt = 0; attempt < 3 && !getParams().cancelled; attempt++) {
            try {
                cursor = getParams().queryDatabase();
                break;
            } catch (IllegalStateException e) {
                String message = "Attempt " + attempt + " to prepare cursor";
                logD(method, message, e);
                if (DbUtils.waitBetweenRetries(message)) {
                    break;
                }
            }
        }
        logV(method, "cursor loaded");
        return cursor;
    }

    private List<T> loadFromCursor(Cursor cursor) {
        long startTime = System.currentTimeMillis();
        List<T> items = new ArrayList<>();
        int rowsCount = 0;
        if (cursor != null && !cursor.isClosed()) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        long rowStartTime = System.currentTimeMillis();
                        rowsCount++;
                        T item = (T) page.getEmptyItem().fromCursor(cursor);
                        long afterFromCursor = System.currentTimeMillis();
                        getParams().rememberSentDateLoaded(item.getDate());
                        items.add(item);
                        if (MyLog.isVerboseEnabled()) {
                            MyLog.v(this, "row " + rowsCount + ", id:" + item.getId()
                                    + ": " + (System.currentTimeMillis() - rowStartTime) + "ms, fromCursor: "
                                    + (afterFromCursor - rowStartTime) + "ms");
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        MyLog.d(this, "loadFromCursor " + rowsCount + " rows, "
                + (System.currentTimeMillis() - startTime) + "ms" );
        getParams().rowsLoaded = rowsCount;
        return items;
    }

    protected void filter(List<T> items) {
        long startTime = System.currentTimeMillis();
        TimelineFilter filter = new TimelineFilter(getParams().getTimeline());
        int rowsCount = 0;
        int filteredOutCount = 0;
        boolean reversedOrder = getParams().isSortOrderAscending();
        for (T item : items) {
            long rowStartTime = System.currentTimeMillis();
            rowsCount++;
            if (item.matches(filter)) {
                if (reversedOrder) {
                    page.items.add(0, item);
                } else {
                    page.items.add(item);
                }
            } else {
                filteredOutCount++;
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, filteredOutCount + " Filtered out: "
                            + I18n.trimTextAt(item.toString(), 100));
                }
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "row " + rowsCount + ", id:" + item.getId()
                        + ": " + (System.currentTimeMillis() - rowStartTime) + "ms");
            }
        }
        MyLog.d(this, "Filtered out " + filteredOutCount + " of " + rowsCount + " rows, "
                + (System.currentTimeMillis() - startTime) + "ms" );
    }

    public TimelineParameters getParams() {
        return params;
    }

    private void logExecutionStats() {
        if (MyLog.isVerboseEnabled()) {
            StringBuilder text = new StringBuilder(getParams().cancelled ? "cancelled" : "ended");
            if (!getParams().cancelled) {
                text.append(", " + page.items.size() + " rows");
                text.append(", dates from " + page.params.minSentDateLoaded + " to "
                        + page.params.maxSentDateLoaded);
            }
            text.append(", " + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(getParams().endTime
                    - getParams().startTime) + " ms");
            logV("stats", text.toString());
        }
    }

    private void logD(String method, String message, Throwable tr) {
        MyLog.d(this, String.valueOf(instanceId) + " " + method + "; " + message, tr);
    }

    private void logV(String method, Object obj) {
        if (MyLog.isVerboseEnabled()) {
            String message = (obj != null) ? obj.toString() : "";
            MyLog.v(this, String.valueOf(instanceId) + " " + method + "; " + message);
        }
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, getParams().toString());
    }

    @NonNull
    public TimelinePage<T> getPage() {
        return page;
    }
}
