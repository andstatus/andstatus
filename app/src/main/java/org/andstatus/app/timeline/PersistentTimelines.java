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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yvolk@yurivolkov.com
 */
public class PersistentTimelines {
    private final List<Timeline> timelines = new ArrayList<>();
    private final MyContext myContext;

    public static PersistentTimelines newEmpty(MyContext myContext) {
        return new PersistentTimelines(myContext);
    }

    private PersistentTimelines(MyContext myContext) {
        this.myContext = myContext;
    }

    public PersistentTimelines initialize() {
        final String method = "initialize";
        Context context = myContext.context();
        timelines.clear();
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.d(context, method + "; Database is unavailable");
        } else {
            String sql = "SELECT * FROM " + TimelineTable.TABLE_NAME;
            Cursor c = null;
            try {
                c = db.rawQuery(sql, null);
                while (c.moveToNext()) {
                    Timeline timeline = Timeline.fromCursor(myContext, c);
                    if (!timeline.isValid()) {
                        MyLog.e(context, method + "; invalid skipped " + timeline);
                    } else {
                        timelines.add(timeline);
                        if (MyLog.isVerboseEnabled() && timelines.size() < 5) {
                            MyLog.v(context, method + "; " + timeline);
                        }
                    }
                }
            } finally {
                DbUtils.closeSilently(c);
            }
            sort(timelines);
            MyLog.v(this, "Timelines initialized, " + timelines.size() + " timelines");
        }
        return this;
    }

    @NonNull
    public Timeline fromId(long id) {
        Timeline timelineFound = Timeline.getEmpty(MyAccount.getEmpty(myContext));
        if (id != 0) {
            for (Timeline timeline : timelines) {
                if (timeline.getId() == id) {
                    timelineFound = timeline;
                    break;
                }
            }
        }
        return timelineFound;
    }

    @NonNull
    public Timeline getDefaultForCurrentAccount() {
        return fromNewTimeLine(
                new Timeline(MyPreferences.getDefaultTimeline(),
                        myContext.persistentAccounts().getCurrentAccount(), 0, null));
    }

    @NonNull
    public Timeline fromNewTimeLine(Timeline newTimeline) {
        Timeline found = newTimeline;
        for (Timeline timeline : timelines) {
            if (timeline.equals(newTimeline)) {
                found = timeline;
                break;
            }
        }
        return found;
    }

    public List<Timeline> getList() {
        return timelines;
    }

    @NonNull
    public List<Timeline> toSyncForAccount(MyAccount ma) {
        List<Timeline> timelines = new ArrayList<>();
        for (Timeline timeline : getList()) {
            if (timeline.isSynced() && timeline.getMyAccount().equals(ma)) {
                timelines.add(timeline);
            }
        }
        return timelines;
    }

    @NonNull
    public List<Timeline> getFiltered(boolean isForSelector,
                                      boolean isTimelineCombined,
                                      MyAccount ma,
                                      Origin origin) {
        List<Timeline> timelines = new ArrayList<>();
        for (Timeline timeline : getList()) {
            boolean include = false;
            if (isForSelector && !timeline.isDisplayedInSelector()) {
                include = false;
            } else if (isTimelineCombined) {
                include = true;
            } else if (ma != null && ma.isValid() && timeline.getMyAccount().equals(ma)) {
                include = true;
            } else if (origin != null && origin.isValid() && timeline.getOrigin().equals(origin)) {
                include = true;
            }
            if (include) {
                timelines.add(timeline);
            }
        }
        if (isForSelector) {
            removeDuplicatesForSelector(timelines);
        }
        return timelines;
    }

    private void removeDuplicatesForSelector(List<Timeline> timelines) {
        Map<String, Timeline> map = new HashMap<>();
        for (Timeline timeline : timelines) {
            String key = timeline.getTimelineType().save() + timeline.getName();
            // TODO
            if (!map.containsKey(key)) {
                map.put(key, timeline);
            }
        }
        timelines.retainAll(map.values());
    }

    private void sort(List<Timeline> timelines) {
        Collections.sort(timelines, new Comparator<Timeline>() {
            @Override
            public int compare(Timeline lhs, Timeline rhs) {
                if (lhs.getSelectorOrder() == rhs.getSelectorOrder()) {
                    return lhs.getTimelineType().compareTo(rhs.getTimelineType());
                } else {
                    return lhs.getSelectorOrder() > rhs.getSelectorOrder() ? 1 : -1;
                }
            }
        });
    }

    public void addDefaultTimelinesIfNoneFound() {
        for (MyAccount ma : myContext.persistentAccounts().collection()) {
            addDefaultMyAccountTimelinesIfNoneFound(ma);
        }
    }

    public void addDefaultMyAccountTimelinesIfNoneFound(MyAccount ma) {
        if (ma.isValid() && getFiltered(false, false, ma, null).isEmpty()) {
            addDefaultOriginTimelinesIfNoneFound(ma.getOrigin());

            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID, TimelineTable.ACCOUNT_ID + "=" + ma.getUserId());
            if (timelineId == 0) {
                timelines.addAll(Timeline.addDefaultForAccount(ma));
                sort(timelines);
            }
        }
    }

    public void addDefaultOriginTimelinesIfNoneFound(Origin origin) {
        if (origin.isValid()) {
            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID,
                    TimelineTable.ORIGIN_ID + "=" + origin.getId() + " AND " +
                            TimelineTable.TIMELINE_TYPE + "='" + TimelineType.EVERYTHING.save() + "'");
            if (timelineId == 0) {
                timelines.addAll(Timeline.addDefaultForOrigin(origin));
                sort(timelines);
            }
        }
    }

    public void onAccountDelete(MyAccount ma) {
        List<Timeline> toRemove = new ArrayList<>();
        for (Timeline timeline : getList()) {
            if (timeline.getMyAccount().equals(ma)) {
                timeline.delete();
                toRemove.add(timeline);
            }
        }
        timelines.removeAll(toRemove);
    }

    @NonNull
    public Timeline fromParsedUri(ParsedUri parsedUri, String searchQuery) {
        return fromNewTimeLine(Timeline.fromParsedUri(myContext, parsedUri, searchQuery));
    }

    public void saveChanged() {
        for (Timeline timeline : getList()) {
            timeline.saveIfChanged();
        }
    }

    public Timeline getHome() {
        return fromNewTimeLine(getDefaultForCurrentAccount().
                fromIsCombined(MyPreferences.isTimelineCombinedByDefault()));
    }
}
