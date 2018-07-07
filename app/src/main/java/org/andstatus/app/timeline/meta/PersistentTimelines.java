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

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.TimelineTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

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
        timelines.clear();
        MyQuery.get(myContext, "SELECT * FROM " + TimelineTable.TABLE_NAME,
                cursor -> Timeline.fromCursor(myContext, cursor)
        ).forEach(timeline -> {
            if (timeline.isValid()) {
                timelines.put(timeline.getId(), timeline);
                if (MyLog.isVerboseEnabled() && timelines.size() < 5) {
                    MyLog.v(PersistentTimelines.class, method + "; " + timeline);
                }
            } else MyLog.e(PersistentTimelines.class, method + "; invalid skipped " + timeline);
        });
        MyLog.v(this, () -> "Timelines initialized, " + timelines.size() + " timelines");
        return this;
    }

    @NonNull
    public Timeline fromId(long id) {
        return Optional.ofNullable(timelines.get(id)).orElseGet(() -> addNew(Timeline.fromId(myContext, id)));
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
    public Timeline forUserAtHomeOrigin(@NonNull TimelineType timelineType, Actor actor) {
        return forUser(timelineType, myContext.users().toHomeOrigin(actor).actorId);
    }

    @NonNull
    public Timeline forUser(@NonNull TimelineType timelineType, long actorId) {
        return get(0, timelineType, actorId, Origin.EMPTY, "");
    }

    @NonNull
    public Timeline get(@NonNull TimelineType timelineType, long actorId, @NonNull Origin origin) {
        return get(0, timelineType, actorId, origin, "");
    }

    @NonNull
    public Timeline get(@NonNull TimelineType timelineType, long actorId, @NonNull Origin origin, String searchQuery) {
        return get(0, timelineType, actorId, origin, searchQuery);
    }

    @NonNull
    public Timeline get(long id, @NonNull TimelineType timelineType,
                        long actorId, @NonNull Origin origin, String searchQuery) {
        Timeline newTimeline = new Timeline(myContext, id, timelineType, actorId, origin, searchQuery);
        return values().stream().filter(timeline -> newTimeline.getId() == 0
                ? newTimeline.duplicates(timeline)
                : timeline.getId() == newTimeline.getId())
                .findAny().orElseGet(() -> newTimeline);
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
                        ((!timeline.getTimelineType().isAtOrigin() && timeline.myAccountToSync.equals(ma)) ||
                                timeline.getTimelineType().isAtOrigin() && timeline.getOrigin().equals(ma.getOrigin())) &&
                        timeline.isTimeToAutoSync()) {
                    timelines.add(timeline);
                }
            }
        }
        return timelines;
    }

    @NonNull
    public Stream<Timeline> toTimelinesToSync(Timeline timelineToSync) {
        if (timelineToSync.isSyncableForOrigins()) {
            return myContext.origins().originsToSync(
                    timelineToSync.myAccountToSync.getOrigin(), true, timelineToSync.hasSearchQuery())
                    .stream().map(origin -> timelineToSync.cloneForOrigin(myContext, origin));
        } else if (timelineToSync.isSyncableForAccounts()) {
            return myContext.accounts().accountsToSync()
                    .stream().map(account -> timelineToSync.cloneForAccount(myContext, account));
        } else if (timelineToSync.isSyncable()) {
            return Stream.of(timelineToSync);
        } else {
            return Stream.empty();
        }
    }

    @NonNull
    public Stream<Timeline> filter(boolean isForSelector,
                                   TriState isTimelineCombined,
                                   @NonNull TimelineType timelineType,
                                   @NonNull Actor actor,
                                   @NonNull Origin origin) {
        return values().stream().filter(
                timeline -> timeline.match(isForSelector, isTimelineCombined, timelineType, actor, origin));
    }

    public void onAccountDelete(MyAccount ma) {
        List<Timeline> toRemove = new ArrayList<>();
        for (Timeline timeline : values()) {
            if (timeline.myAccountToSync.equals(ma)) {
                timeline.delete(myContext);
                toRemove.add(timeline);
            }
        }
        for (Timeline timeline : toRemove) {
            timelines.remove(timeline.getId());
        }
    }

    public void delete(Timeline timeline) {
        if (myContext.isReady()) {
            timeline.delete(myContext);
            timelines.remove(timeline.getId());
        }
    }

    public void saveChanged() {
        new TimelineSaver(myContext).executeNotOnUiThread();
    }

    public Timeline addNew(Timeline timeline) {
        if (timeline.isValid() && timeline.getId() != 0) timelines.putIfAbsent(timeline.getId(), timeline);
        return timelines.getOrDefault(timeline.getId(), timeline);
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
