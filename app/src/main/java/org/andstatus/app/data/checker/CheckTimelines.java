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
import org.andstatus.app.timeline.meta.TimelineSaver;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
class CheckTimelines extends DataChecker {

    long fixInternal(boolean countOnly) {
        logger.logProgress("Checking if all default timelines are present");
        long rowsCount = 0;
        long changedCount = 0;
        try {
            changedCount += new TimelineSaver(myContext).addDefaultCombined().size();
            for (MyAccount myAccount: myContext.persistentAccounts().list()) {
                changedCount += new TimelineSaver(myContext).addDefaultForAccount(myContext, myAccount).size();
            }
        } catch (Exception e) {
            String logMsg = "Error: " + e.getMessage();
            logger.logProgress(logMsg);
            MyLog.e(this, logMsg, e);
        }
        myContext.persistentTimelines().saveChanged();
        logger.logProgress(changedCount == 0
                ? "No changes to timelines were needed. " + rowsCount + " timelines"
                : "Changed " + changedCount + " of " + myContext.persistentTimelines().values().size() + " timelines");
        DbUtils.waitMs(this, changedCount == 0 ? 1000 : 3000);
        return changedCount;
    }
}
