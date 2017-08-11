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

package org.andstatus.app.timeline.meta;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        return timeline == null ? Timeline.EMPTY : timeline;
    }

    @NonNull
    public Timeline getDefault() {
        Timeline defaultTimeline = Timeline.EMPTY;
        for (Timeline timeline : values()) {
            if (defaultTimeline.compareTo(timeline) > 0 || !defaultTimeline.isValid()) {
                defaultTimeline = timeline;
            }
        }
        return defaultTimeline;
    }

    public void setDefault(@NonNull Timeline timelineIn) {
        Timeline prevDefault = getDefault();
        if (!timelineIn.equals(prevDefault) && timelineIn.getSelectorOrder() >= prevDefault.getSelectorOrder()) {
            timelineIn.setSelectorOrder(prevDefault.getSelectorOrder() - 1);
        }
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
                                      @NonNull TimelineType timelineType,
                                      MyAccount myAccount,
                                      Origin origin) {
        List<Timeline> timelines = new ArrayList<>();
        for (Timeline timeline : values()) {
            boolean include;
            if (isForSelector && timeline.isDisplayedInSelector() == DisplayedInSelector.ALWAYS) {
                include = true;
            } else if (isForSelector && timeline.isDisplayedInSelector() == DisplayedInSelector.NEVER) {
                include = false;
            } else {
                include = isTimelineCombined.isBoolean(timeline.isCombined())
                    && (timelineType.isAtOrigin() || myAccount == null || !myAccount.isValid() ||
                            timeline.isCombined() ||
                            timeline.getMyAccount().equals(myAccount) ||
                            (isForSelector && timeline.getTimelineType().isAtOrigin() &&
                                    myAccount.getOrigin().equals(timeline.getOrigin()))
                    )
                    && (!timelineType.isAtOrigin() || origin == null || !origin.isValid() ||
                            timeline.isCombined() || timeline.getOrigin().equals(origin)
                    )
                    && (timelineType == TimelineType.UNKNOWN || timelineType == timeline.getTimelineType()) ;
            }
            if (include) {
                timelines.add(timeline);
            }
        }
        return timelines;
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
        new TimelineSaver(myContext).executeNotOnUiThread();
    }

    public void addNew(Timeline timeline) {
        if (timeline.getId() != 0) {
            timelines.putIfAbsent(timeline.getId(), timeline);
        }
    }

    public void resetCounters(boolean all) {
        for (Timeline timeline : values()) {
            timeline.resetCounters(all);
        }
    }

    public void resetDefaultSelectorOrder() {
        for (Timeline timeline : values()) {
            timeline.setDefaultSelectorOrder();
        }
    }
}
