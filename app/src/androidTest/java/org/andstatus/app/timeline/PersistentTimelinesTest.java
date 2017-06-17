/*
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

import org.andstatus.app.account.DemoAccountInserter;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.DemoOriginInserter;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersistentTimelinesTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testList() throws Exception {
        Collection<Timeline> timelines = MyContextHolder.get().persistentTimelines().values();
        assertTrue(timelines.size() > 0);
    }

    @Test
    public void testFilteredList() throws Exception {
        Collection<Timeline> timelines = MyContextHolder.get().persistentTimelines().values();
        List<Timeline> filtered = MyContextHolder.get().persistentTimelines().getFiltered(false, TriState.UNKNOWN, null, null);
        assertEquals(timelines.size(), filtered.size());

        filtered = MyContextHolder.get().persistentTimelines().getFiltered(true, TriState.FALSE, null, null);
        assertTrue(timelines.size() > filtered.size());

        filtered = MyContextHolder.get().persistentTimelines().getFiltered(true, TriState.TRUE, null, null);
        assertTrue(!filtered.isEmpty());
        assertTrue(timelines.size() > filtered.size());

        ensureAtLeastOneNotDisplayedTimeline();
        List<Timeline> filtered2 = MyContextHolder.get().persistentTimelines().getFiltered(true, TriState.UNKNOWN, null, null);
        assertTrue(timelines.size() > filtered2.size());
        assertTrue(filtered2.size() > filtered.size());

        MyAccount myAccount = MyContextHolder.get().persistentAccounts().fromAccountName(DemoData.CONVERSATION_ACCOUNT_NAME);
        filtered = MyContextHolder.get().persistentTimelines().getFiltered(true, TriState.FALSE, myAccount, null);
        assertTrue(!filtered.isEmpty());

        filtered = MyContextHolder.get().persistentTimelines().getFiltered(true, TriState.FALSE, null, myAccount.getOrigin());
        assertTrue(!filtered.isEmpty());

    }

    private void ensureAtLeastOneNotDisplayedTimeline() {
        Collection<Timeline> timelines = MyContextHolder.get().persistentTimelines().values();
        boolean found = false;
        Timeline timeline1 = null;
        for (Timeline timeline : timelines) {
            if (timeline.isDisplayedInSelector().equals(DisplayedInSelector.NEVER)) {
                found = true;
                break;
            }
            if (timeline1 == null && timeline.getTimelineType().equals(TimelineType.MY_FOLLOWERS)) {
                timeline1 = timeline;
            }
        }
        if (!found && timeline1 != null) {
            timeline1.setDisplayedInSelector(DisplayedInSelector.NEVER);
            MyContextHolder.get().persistentTimelines().saveChanged();
        }
    }

    @Test
    public void testDefaultTimelinesForAccounts() {
        DemoAccountInserter.checkDefaultTimelinesForAccounts();
    }

    @Test
    public void testDefaultTimelinesForOrigins() {
        DemoOriginInserter.checkDefaultTimelinesForOrigins();
    }

    @Test
    public void testDefaultTimeline() {
        MyContextHolder.get().persistentTimelines().resetDefaultSelectorOrder();
        Timeline timeline = MyContextHolder.get().persistentTimelines().getDefault();
        assertTrue(timeline.toString(), timeline.isValid());
        assertEquals(timeline.toString(), TimelineType.HOME, timeline.getTimelineType());
        assertFalse(timeline.toString(), timeline.isCombined());

        Origin origin = MyContextHolder.get().persistentOrigins().fromName(DemoData.GNUSOCIAL_TEST_ORIGIN_NAME);
        MyAccount myAccount = MyContextHolder.get().persistentAccounts().getFirstSucceededForOrigin(origin);
        assertTrue(myAccount.isValid());
        timeline = MyContextHolder.get().persistentTimelines().
                getFiltered(false, TriState.FALSE, myAccount, null).get(2);
        MyContextHolder.get().persistentTimelines().setDefault(timeline);

        Timeline timeline2 = MyContextHolder.get().persistentTimelines().getDefault();
        assertEquals(timeline, timeline2);
    }
}
