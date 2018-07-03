/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data.checker;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineSaver;
import org.andstatus.app.util.MyLog;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
class CheckTimelines extends DataChecker {

    long fixInternal(boolean countOnly) {
        return addDefaultTimelines() + removeDuplicatedTimelines();
    }

    private long addDefaultTimelines() {
        logger.logProgress("Checking if all default timelines are present");
        long size1 = myContext.timelines().values().size();
        try {
            new TimelineSaver(myContext).addDefaultCombined();
            for (MyAccount myAccount: myContext.accounts().get()) {
                new TimelineSaver(myContext).addDefaultForMyUser(myAccount);
            }
        } catch (Exception e) {
            String logMsg = "Error: " + e.getMessage();
            logger.logProgress(logMsg);
            MyLog.e(this, logMsg, e);
        }
        myContext.timelines().saveChanged();
        final int size2 = myContext.timelines().values().size();
        long addedCount = size2 - size1;
        logger.logProgress(addedCount == 0
                ? "No new timelines were added. " + size2 + " timelines"
                : "Added " + addedCount + " of " + size2 + " timelines");
        DbUtils.waitMs(this, addedCount == 0 ? 1000 : 3000);
        return addedCount;
    }

    private long removeDuplicatedTimelines() {
        logger.logProgress("Checking if duplicated timelines are present");
        long size1 = myContext.timelines().values().size();
        Set<Timeline> toDelete = new HashSet<>();
        try {
            for (Timeline timeline1 : myContext.timelines().values()) {
                for (Timeline timeline2 : myContext.timelines().values()) {
                    if (timeline2.duplicates(timeline1)) toDelete.add(timeline2);
                }
            }
            toDelete.forEach(myContext.timelines()::delete);
        } catch (Exception e) {
            String logMsg = "Error: " + e.getMessage();
            logger.logProgress(logMsg);
            MyLog.e(this, logMsg, e);
        }
        myContext.timelines().saveChanged();
        final int size2 = myContext.timelines().values().size();
        long deletedCount = size1 - size2;
        logger.logProgress(deletedCount == 0
                ? "No duplicated timelines found. " + size2 + " timelines"
                : "Deleted " + deletedCount + " duplicates of " + size1 + " timelines");
        DbUtils.waitMs(this, deletedCount == 0 ? 1000 : 3000);
        return deletedCount;
    }
}
