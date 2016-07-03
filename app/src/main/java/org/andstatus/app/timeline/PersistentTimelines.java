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
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class PersistentTimelines {
    private final ConcurrentMap<Long, Timeline> timelines = new ConcurrentHashMap<>();
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
                        timelines.put(timeline.getId(), timeline);
                        if (MyLog.isVerboseEnabled() && timelines.size() < 5) {
                            MyLog.v(context, method + "; " + timeline);
                        }
                    }
                }
            } finally {
                DbUtils.closeSilently(c);
            }
            MyLog.v(this, "Timelines initialized, " + timelines.size() + " timelines");
        }
        return this;
    }

    @NonNull
    public Timeline fromId(long id) {
        Timeline timeline = timelines.get(id);
        if (timeline == null) {
            timeline = Timeline.getEmpty(MyAccount.getEmpty(myContext));
        }
        return timeline;
    }

    @NonNull
    public Timeline getDefaultForCurrentAccount() {
        return Timeline.getTimeline(myContext, 0, MyPreferences.getDefaultTimeline(),
                        myContext.persistentAccounts().getCurrentAccount(), 0, null, "");
    }

    @NonNull
    Timeline fromNewTimeLine(Timeline newTimeline) {
        Timeline found = newTimeline;
        for (Timeline timeline : values()) {
            if (newTimeline.getId() == 0) {
                if (timeline.equals(newTimeline)) {
                    found = timeline;
                    break;
                }
            } else if (timeline.getId() == newTimeline.getId()) {
                found = timeline;
                break;
            }
        }
        return found;
    }

    public Collection<Timeline> values() {
        return timelines.values();
    }

    @NonNull
    public List<Timeline> toAutoSyncForAccount(MyAccount ma) {
        List<Timeline> timelines = new ArrayList<>();
        if (ma.isValidAndSucceeded()) {
            for (Timeline timeline : values()) {
                if (timeline.isSyncedAutomatically() &&
                        ((!timeline.getTimelineType().isAtOrigin() && timeline.getMyAccount().equals(ma)) ||
                                timeline.getTimelineType().isAtOrigin() && timeline.getOrigin().equals(ma.getOrigin())) &&
                        timeline.isTimeToAutoSync()) {
                    timelines.add(timeline);
                }
            }
        }
        return timelines;
    }

    @NonNull
    public List<Timeline> getFiltered(boolean isForSelector,
                                      TriState isTimelineCombined,
                                      MyAccount currentMyAccount,
                                      Origin origin) {
        List<Timeline> timelines = new ArrayList<>();
        for (Timeline timeline : values()) {
            boolean include = (!isForSelector || timeline.isDisplayedInSelector()) &&
                    isTimelineCombined.isBoolean(timeline.isCombined()) &&
                    (currentMyAccount == null || !currentMyAccount.isValid() ||
                            timeline.isCombined() ||
                            timeline.getMyAccount().equals(currentMyAccount)) &&
                    (origin == null || !origin.isValid() ||
                            timeline.isCombined() || timeline.getOrigin().equals(origin));
            if (include) {
                timelines.add(timeline);
            }
        }
        return timelines;
    }

    public void addDefaultTimelinesIfNoneFound() {
        for (MyAccount ma : myContext.persistentAccounts().collection()) {
            addDefaultMyAccountTimelinesIfNoneFound(ma);
        }
    }

    public void addDefaultMyAccountTimelinesIfNoneFound(MyAccount ma) {
        if (ma.isValid() && getFiltered(false, TriState.FALSE, ma, null).isEmpty()) {
            addDefaultCombinedTimelinesIfNoneFound();
            addDefaultOriginTimelinesIfNoneFound(ma.getOrigin());

            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID, TimelineTable.ACCOUNT_ID + "=" + ma.getUserId());
            if (timelineId == 0) {
                Timeline.addDefaultForAccount(myContext, ma);
            }
        }
    }

    private void addDefaultCombinedTimelinesIfNoneFound() {
        if (!timelines.isEmpty()) {
            return;
        }
        long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                TimelineTable._ID,
                TimelineTable.ACCOUNT_ID + "=0 AND " + TimelineTable.ORIGIN_ID + "=0");
        if (timelineId == 0) {
            Timeline.addDefaultCombined(myContext);
        }
    }

    public void addDefaultOriginTimelinesIfNoneFound(Origin origin) {
        if (origin.isValid()) {
            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID,
                    TimelineTable.ORIGIN_ID + "=" + origin.getId() + " AND " +
                            TimelineTable.TIMELINE_TYPE + "='" + TimelineType.EVERYTHING.save() + "'");
            if (timelineId == 0) {
                Timeline.addDefaultForOrigin(myContext, origin);
            }
        }
    }

    public void onAccountDelete(MyAccount ma) {
        List<Timeline> toRemove = new ArrayList<>();
        for (Timeline timeline : values()) {
            if (timeline.getMyAccount().equals(ma)) {
                timeline.delete();
                toRemove.add(timeline);
            }
        }
        for (Timeline timeline : toRemove) {
            timelines.remove(timeline.getId());
        }
    }

    public void delete(Timeline timeline) {
        if (myContext.isReady()) {
            timeline.delete();
            timelines.remove(timeline.getId());
        }
    }

    public void saveChanged() {
        for (Timeline timeline : values()) {
            timeline.save(myContext);
        }
    }

    public Timeline getHome() {
        return getDefaultForCurrentAccount().
                fromIsCombined(myContext, MyPreferences.isTimelineCombinedByDefault());
    }

    public void addNew(Timeline timeline) {
        if (timeline.getId() != 0) {
            timelines.putIfAbsent(timeline.getId(), timeline);
        }
    }

    public void resetCounters() {
        for (Timeline timeline : values()) {
            timeline.resetCounters();
        }
    }
}
