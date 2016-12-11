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

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Save changes to Timelines not on UI thread.
 * Optionally creates default timelines: for all or for one account.
 * @author yvolk@yurivolkov.com
 */
public class TimelineSaver extends MyAsyncTask<Void, Void, Void> {
    private static final AtomicBoolean executing = new AtomicBoolean(false);
    private final MyContext myContext;
    private boolean addDefaults = false;
    private MyAccount myAccount = null;

    public TimelineSaver(MyContext myContext) {
        super(PoolEnum.QUICK_UI);
        this.myContext = myContext;
    }

    public TimelineSaver setAccount(MyAccount myAccount) {
        this.myAccount = myAccount;
        return this;
    }

    public TimelineSaver setAddDefaults(boolean addDefaults) {
        this.addDefaults = addDefaults;
        return this;
    }

    @Override
    protected Void doInBackground2(Void... params) {
        executeSynchronously();
        return null;
    }

    public void executeNotOnUiThread() {
        if (isUiThread()) {
            AsyncTaskLauncher.execute(this, false, this);
        } else {
            executeSynchronously();
        }
    }

    private void executeSynchronously() {
        if (executing.compareAndSet(false, true)) {
            try {
                if (addDefaults) {
                   if (myAccount == null) {
                       addDefaultTimelinesIfNoneFound();
                   } else {
                       addDefaultMyAccountTimelinesIfNoneFound(myAccount);
                   }
                }
                // TODO: Better duplication checks
                for (Timeline timeline : timelines().values()) {
                    timeline.save(myContext);
                }
            } finally {
                executing.set(false);
            }
        }
    }

    private void addDefaultTimelinesIfNoneFound() {
        for (MyAccount ma : myContext.persistentAccounts().list()) {
            addDefaultMyAccountTimelinesIfNoneFound(ma);
        }
    }

    private void addDefaultMyAccountTimelinesIfNoneFound(MyAccount ma) {
        if (ma.isValid() && timelines().getFiltered(false, TriState.FALSE, ma, null).isEmpty()) {
            addDefaultCombinedTimelinesIfNoneFound();
            addDefaultOriginTimelinesIfNoneFound(ma.getOrigin());

            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID, TimelineTable.ACCOUNT_ID + "=" + ma.getUserId());
            if (timelineId == 0) {
                addDefaultForAccount(myContext, ma);
            }
        }
    }

    private void addDefaultCombinedTimelinesIfNoneFound() {
        if (timelines().getFiltered(false, TriState.TRUE, null, null).isEmpty()) {
            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID,
                    TimelineTable.ACCOUNT_ID + "=0 AND " + TimelineTable.ORIGIN_ID + "=0");
            if (timelineId == 0) {
                addDefaultCombined(myContext);
            }
        }
    }

    private void addDefaultOriginTimelinesIfNoneFound(Origin origin) {
        if (origin.isValid()) {
            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID,
                    TimelineTable.ORIGIN_ID + "=" + origin.getId() + " AND " +
                            TimelineTable.TIMELINE_TYPE + "='" + TimelineType.EVERYTHING.save() + "'");
            if (timelineId == 0) {
                addDefaultForOrigin(myContext, origin);
            }
        }
    }

    private List<Timeline> addDefaultForAccount(MyContext myContext, MyAccount myAccount) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.getDefaultMyAccountTimelineTypes()) {
            saveNewDefaultTimeline(Timeline.getTimeline(myContext, 0, timelineType, myAccount, 0, null, ""));
        }
        return timelines;
    }

    private Collection<Timeline> addDefaultForOrigin(MyContext myContext, Origin origin) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.getDefaultOriginTimelineTypes()) {
            if (origin.getOriginType().isTimelineTypeSyncable(timelineType)) {
                saveNewDefaultTimeline(Timeline.getTimeline(myContext, 0, timelineType, null, 0, origin, ""));
            }
        }
        return timelines;
    }

    private List<Timeline> addDefaultCombined(MyContext myContext) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.values()) {
            if (timelineType.isSelectable()) {
                saveNewDefaultTimeline(Timeline.getTimeline(myContext, 0, timelineType, null, 0, null, ""));
            }
        }
        return timelines;
    }

    private void saveNewDefaultTimeline(Timeline timeline) {
        timeline.setDisplayedInSelector(DisplayedInSelector.IN_CONTEXT);
        timeline.setSyncedAutomatically(timeline.getTimelineType().isSyncedAutomaticallyByDefault());
        timeline.save(myContext);
    }

    @NonNull
    private PersistentTimelines timelines() {
        return myContext.persistentTimelines();
    }

}
