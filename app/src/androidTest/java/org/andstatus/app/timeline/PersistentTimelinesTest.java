/**
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.Origin;

import java.util.List;

public class PersistentTimelinesTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testList() throws Exception {
        List<Timeline> timelines = MyContextHolder.get().persistentTimelines().getList();
        assertTrue(timelines.size() > 0);
    }

    public void testFilteredList() throws Exception {
        List<Timeline> timelines = MyContextHolder.get().persistentTimelines().getList();
        List<Timeline> filtered = MyContextHolder.get().persistentTimelines().getFiltered(true, false, null, null);
        assertTrue(filtered.isEmpty());

        filtered = MyContextHolder.get().persistentTimelines().getFiltered(true, true, null, null);
        assertTrue(!filtered.isEmpty());
        assertTrue(timelines.size() > filtered.size());

        filtered = MyContextHolder.get().persistentTimelines().getFiltered(false, true, null, null);
        assertEquals(timelines.size(), filtered.size());

        MyAccount myAccount = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        filtered = MyContextHolder.get().persistentTimelines().getFiltered(true, false, myAccount, null);
        assertTrue(!filtered.isEmpty());

        List<Timeline> filtered2 = MyContextHolder.get().persistentTimelines().getFiltered(true, false, null, myAccount.getOrigin());
        assertTrue(!filtered2.isEmpty());

    }

    public void testDefaultMyAccountTimelinesCreation() {
        for (MyAccount myAccount : MyContextHolder.get().persistentAccounts().collection()) {
            for (TimelineType timelineType : TimelineType.defaultMyAccountTimelineTypes) {
                long count = 0;
                for (Timeline timeline : MyContextHolder.get().persistentTimelines().getList()) {
                    if (timeline.getMyAccount().equals(myAccount) && timeline.getTimelineType().equals(timelineType)) {
                        count++;
                    }
                }
                assertEquals( myAccount.toString() + " " + timelineType , 1, count);
            }
        }
    }

    public void testDefaultOriginTimelinesCreation() {
        for (Origin origin : MyContextHolder.get().persistentOrigins().collection()) {
            MyAccount myAccount = MyContextHolder.get().persistentAccounts().getFirstSucceededForOriginId(origin.getId());
            for (TimelineType timelineType : TimelineType.defaultOriginTimelineTypes) {
                long count = 0;
                for (Timeline timeline : MyContextHolder.get().persistentTimelines().getList()) {
                    if (timeline.getOrigin().equals(origin) && timeline.getTimelineType().equals(timelineType)) {
                        count++;
                    }
                }
                assertEquals( origin.toString() + " " + timelineType , myAccount.isValid() ? 1 : 0, count);
            }
        }
    }
}
