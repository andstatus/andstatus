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

package org.andstatus.app.msg;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.SyncLoader;
import org.andstatus.app.WhichPage;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.widget.TimelineViewItem;

import java.util.ArrayList;

/**
* @author yvolk@yurivolkov.com
*/
public class TimelineLoader<T extends TimelineViewItem> extends SyncLoader<T> {
    private final TimelineListParameters params;
    private final TimelinePage<T> page;

    private final long instanceId;

    TimelineLoader(@NonNull TimelineListParameters params, long instanceId) {
        this.params = params;
        this.page = new TimelinePage<>(getParams(), new ArrayList<>());
        this.items = page.items;
        this.instanceId = instanceId;
    }

    @Override
    public void load(LoadableListActivity.ProgressPublisher publisher) {
        markStart();
        if (params.whichPage != WhichPage.EMPTY) {
            Cursor cursor = queryDatabase();
            loadFromCursor(cursor);
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
        return cursor;
    }

    private void loadFromCursor(Cursor cursor) {
        KeywordsFilter keywordsFilter = new KeywordsFilter(
                SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS, ""));
        boolean hideRepliesNotToMeOrFriends = getParams().getTimelineType() == TimelineType.HOME
                && SharedPreferencesUtil.getBoolean(MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false);
        KeywordsFilter searchQuery = new KeywordsFilter(getParams().getTimeline().getSearchQuery());

        long startTime = System.currentTimeMillis();
        int rowsCount = 0;
        int filteredOutCount = 0;
        if (cursor != null && !cursor.isClosed()) {
            try {
                if (cursor.moveToFirst()) {
                    boolean reversedOrder = getParams().isSortOrderAscending();
                    do {
                        rowsCount++;
                        Pair<T, Boolean> itemAndSkip = (Pair<T, Boolean>) page.getEmptyItem().
                                fromCursor(cursor, keywordsFilter, searchQuery, hideRepliesNotToMeOrFriends);
                        getParams().rememberSentDateLoaded(itemAndSkip.first.getDate());
                        if (itemAndSkip.second) {
                            filteredOutCount++;
                            if (MyLog.isVerboseEnabled()) {
                                MyLog.v(this, filteredOutCount + " Filtered out: "
                                        + I18n.trimTextAt(itemAndSkip.first.toString(), 100));
                            }
                        } else if (reversedOrder) {
                            page.items.add(0, itemAndSkip.first);
                        } else {
                            page.items.add(itemAndSkip.first);
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
    }

    public TimelineListParameters getParams() {
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
