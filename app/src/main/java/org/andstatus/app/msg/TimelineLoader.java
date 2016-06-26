/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.TextUtils;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.WhichPage;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
* @author yvolk@yurivolkov.com
*/
public class TimelineLoader implements LoadableListActivity.SyncLoader {
    private final TimelineListParameters params;
    private volatile TimelinePage pageLoaded;
    private final long instanceId;
    private final MyAccount currentMyAccount;

    public TimelineLoader(@NonNull TimelineListParameters params, MyAccount currentMyAccount, long instanceId) {
        this.params = params;
        this.currentMyAccount = currentMyAccount;
        this.pageLoaded = new TimelinePage(new ArrayList<TimelineViewItem>(), getParams());
        this.instanceId = instanceId;
    }

    @Override
    public void allowLoadingFromInternet() {
        // Unused so far
    }

    @Override
    public void load(LoadableListActivity.ProgressPublisher publisher) {
        markStart();
        if (params.whichPage != WhichPage.EMPTY) {
            Cursor cursor = queryDatabase();
            checkIfReloadIsNeeded(cursor);
            pageLoaded = pageFromCursor(cursor);
        }
        params.endTime = System.nanoTime();
        logExecutionStats();
    }

    @Override
    public List<TimelinePage> getList() {
        return Collections.singletonList(pageLoaded);
    }

    @Override
    public int size() {
        return pageLoaded.items.size();
    }

    void markStart() {
        params.startTime = System.nanoTime();
        params.cancelled = false;
        params.timelineToSync = Timeline.getEmpty(params.getMyAccount());
        params.timeline.saveIfChanged();
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
                logD(method, "Attempt " + attempt + " to prepare cursor", e);
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
            if (getParams().getTimeline().isSynced()) {
                if ( MyContextHolder.get().persistentTimelines().fromNewTimeLine(
                        new Timeline(TimelineType.HOME, getParams().getMyAccount(), 0, null)).
                        getYoungestSyncedDate() == 0) {
                    // This is supposed to be a one time task.
                    getParams().timelineToSync = new Timeline(TimelineType.EVERYTHING, getParams().getMyAccount(), 0, null);
                }
            } else {
                // This timeline doesn't update automatically so let's do it now if necessary
                if (getParams().getTimeline().isTimeToAutoSync()) {
                    getParams().timelineToSync = getParams().getTimeline();
                }
            }
        }
    }

    private boolean noMessagesInATimeline(Cursor cursor) {
        return getParams().whichPage.isYoungest()
                && !getParams().hasSearchQuery()
                && cursor != null && !cursor.isClosed() && cursor.getCount() == 0;
    }

    @NonNull
    private TimelinePage pageFromCursor(Cursor cursor) {
        TimelinePage page = new TimelinePage(new ArrayList<TimelineViewItem>(), getParams());
        KeywordsFilter keywordsFilter = new KeywordsFilter(
                SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS, ""));
        boolean hideRepliesNotToMeOrFriends = getParams().getTimelineType() == TimelineType.HOME
                && SharedPreferencesUtil.getBoolean(MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false);
        String searchQuery = getParams().getTimeline().getSearchQuery().toLowerCase();

        long startTime = System.currentTimeMillis();
        int rowsCount = 0;
        int filteredOutCount = 0;
        if (cursor != null && !cursor.isClosed()) {
            try {
                if (cursor.moveToFirst()) {
                    boolean reversedOrder = getParams().isSortOrderAscending();
                    do {
                        rowsCount++;
                        TimelineViewItem item = TimelineViewItem.fromCursorRow(cursor);
                        getParams().rememberSentDateLoaded(item.sentDate);
                        String body = MyHtml.fromHtml(item.body).toLowerCase();
                        boolean skip = keywordsFilter.matched(body);
                        if (!skip && !TextUtils.isEmpty(searchQuery)) {
                            skip = !body.contains(searchQuery);
                        }
                        if (!skip && hideRepliesNotToMeOrFriends && item.inReplyToUserId != 0) {
                            skip = !MyContextHolder.get().persistentAccounts().isMeOrMyFriend(item.inReplyToUserId);
                        }
                        if (skip) {
                            filteredOutCount++;
                            if (MyLog.isVerboseEnabled()) {
                                MyLog.v(this, filteredOutCount + " Filtered out: " + I18n.trimTextAt(body, 40));
                            }
                        } else if (reversedOrder) {
                            page.items.add(0, item);
                        } else {
                            page.items.add(item);
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
        return page;
    }

    public TimelineListParameters getParams() {
        return params;
    }

    void logExecutionStats() {
        if (MyLog.isVerboseEnabled()) {
            StringBuilder text = new StringBuilder(getParams().cancelled ? "cancelled" : "ended");
            if (!getParams().cancelled) {
                text.append(", " + pageLoaded.items.size() + " rows");
                text.append(", dates from " + pageLoaded.parameters.minSentDateLoaded + " to "
                        + pageLoaded.parameters.maxSentDateLoaded);
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
}
