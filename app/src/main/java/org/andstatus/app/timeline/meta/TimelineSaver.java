/*
 * Copyright (c) 2016-2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.TimelineTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.os.NonUiThreadExecutor;
import org.andstatus.app.util.TriState;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * Save changes to Timelines not on UI thread.
 * Optionally creates default timelines: for all or for one account.
 * @author yvolk@yurivolkov.com
 */
public class TimelineSaver {
    private static final AtomicBoolean executing = new AtomicBoolean(false);
    private boolean addDefaults = false;
    private MyAccount myAccount = MyAccount.EMPTY;

    public TimelineSaver() { }

    public TimelineSaver setAccount(@NonNull MyAccount myAccount) {
        this.myAccount = myAccount;
        return this;
    }

    public TimelineSaver setAddDefaults(boolean addDefaults) {
        this.addDefaults = addDefaults;
        return this;
    }

    public void execute(MyContext myContext) {
        if (MyAsyncTask.isUiThread()) {
            myContextHolder.with(future -> future.whenCompleteAsync(this::executeSynchronously, NonUiThreadExecutor.INSTANCE));
        } else {
            executeSynchronously(myContext, null);
        }
    }

    private void executeSynchronously(MyContext myContext, Throwable throwable) {
        for (long count = 30; count > 0; count--) {
            if (executing.compareAndSet(false, true)) {
                executeSequentially(myContext);
            }
            DbUtils.waitMs(this, 50);
        }
    }

    private void executeSequentially(MyContext myContext) {
        try {
            if (addDefaults) {
               if (myAccount == MyAccount.EMPTY) {
                   addDefaultTimelinesIfNoneFound(myContext);
               } else {
                   addDefaultMyAccountTimelinesIfNoneFound(myContext, myAccount);
               }
            }
            for (Timeline timeline : myContext.timelines().values()) {
                timeline.save(myContext);
            }
        } finally {
            executing.set(false);
        }
    }

    private void addDefaultTimelinesIfNoneFound(MyContext myContext) {
        myContext.accounts().get().forEach(ma -> addDefaultMyAccountTimelinesIfNoneFound(myContext, ma));
    }

    private void addDefaultMyAccountTimelinesIfNoneFound(MyContext myContext, MyAccount ma) {
        if (ma.isValid() && myContext.timelines().filter(false, TriState.FALSE,
                TimelineType.UNKNOWN, ma.getActor(), Origin.EMPTY).count() == 0) {
            addDefaultCombinedTimelinesIfNoneFound(myContext);
            addDefaultOriginTimelinesIfNoneFound(myContext, ma.getOrigin());

            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID, TimelineTable.ACTOR_ID + "=" + ma.getActorId());
            if (timelineId == 0) addDefaultForMyAccount(myContext, ma);
        }
    }

    private void addDefaultCombinedTimelinesIfNoneFound(MyContext myContext) {
        if (myContext.timelines().filter(false, TriState.TRUE,
                TimelineType.UNKNOWN, Actor.EMPTY, Origin.EMPTY).count() == 0) {
            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID, TimelineTable.ACTOR_ID + "=0 AND " + TimelineTable.ORIGIN_ID + "=0");
            if (timelineId == 0) addDefaultCombined(myContext);
        }
    }

    private void addDefaultOriginTimelinesIfNoneFound(MyContext myContext, Origin origin) {
        if (!origin.isValid()) return;
        long timelineId = MyQuery.conditionToLongColumnValue(myContext.getDatabase(),
                "Any timeline for " + origin.getName(),
                TimelineTable.TABLE_NAME, TimelineTable._ID,
                TimelineTable.ORIGIN_ID + "=" + origin.getId());
        if (timelineId == 0) addDefaultForOrigin(myContext, origin);
    }

    public void addDefaultForMyAccount(MyContext myContext, MyAccount myAccount) {
        for (TimelineType timelineType : myAccount.getActor().getDefaultMyAccountTimelineTypes()) {
            myContext.timelines().forUser(timelineType, myAccount.getActor()).save(myContext);
        }
    }

    private void addDefaultForOrigin(MyContext myContext, Origin origin) {
        for (TimelineType timelineType : TimelineType.getDefaultOriginTimelineTypes()) {
            if (origin.getOriginType().isTimelineTypeSyncable(timelineType)
                    || timelineType.equals(TimelineType.EVERYTHING)) {
                myContext.timelines().get(timelineType, Actor.EMPTY, origin).save(myContext);
            }
        }
    }

    public void addDefaultCombined(MyContext myContext) {
        for (TimelineType timelineType : TimelineType.values()) {
            if (timelineType.isCombinedRequired()) {
                myContext.timelines().get(timelineType, Actor.EMPTY, Origin.EMPTY).save(myContext);
            }
        }
    }
}
