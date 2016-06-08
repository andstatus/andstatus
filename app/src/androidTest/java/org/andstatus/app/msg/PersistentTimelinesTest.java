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

package org.andstatus.app.msg;

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.Timeline;

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
        assertEquals(filtered.size(), filtered2.size());
    }
}
