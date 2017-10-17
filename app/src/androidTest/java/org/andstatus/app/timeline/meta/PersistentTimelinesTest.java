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

package org.andstatus.app.timeline.meta;

import org.andstatus.app.account.DemoAccountInserter;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.DemoOriginInserter;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class PersistentTimelinesTest {
    MyContext myContext;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        myContext = MyContextHolder.get();
    }

    @Test
    public void testList() throws Exception {
        Collection<Timeline> timelines = myContext.persistentTimelines().values();
        assertTrue(timelines.size() > 0);
    }

    @Test
    public void testFilteredList() throws Exception {
        Collection<Timeline> timelines = myContext.persistentTimelines().values();
        List<Timeline> filtered = myContext.persistentTimelines().getFiltered(
                false, TriState.UNKNOWN, TimelineType.UNKNOWN, null, null);
        assertEquals(timelines.size(), filtered.size());

        filtered = myContext.persistentTimelines().getFiltered(
                true, TriState.FALSE, TimelineType.UNKNOWN, null, null);
        assertTrue(timelines.size() > filtered.size());

        filtered = myContext.persistentTimelines().getFiltered(
                true, TriState.TRUE, TimelineType.UNKNOWN, null, null);
        assertTrue(!filtered.isEmpty());
        assertTrue(timelines.size() > filtered.size());

        ensureAtLeastOneNotDisplayedTimeline();
        List<Timeline> filtered2 = myContext.persistentTimelines().getFiltered(
                true, TriState.UNKNOWN, TimelineType.UNKNOWN, null, null);
        assertTrue(timelines.size() > filtered2.size());
        assertTrue(filtered2.size() > filtered.size());

        MyAccount myAccount = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        filtered = myContext.persistentTimelines().getFiltered(
                true, TriState.FALSE, TimelineType.UNKNOWN, myAccount, null);
        assertTrue(!filtered.isEmpty());

        filtered = myContext.persistentTimelines().getFiltered(
                true, TriState.FALSE, TimelineType.UNKNOWN, null, myAccount.getOrigin());
        assertTrue(!filtered.isEmpty());

        filtered = myContext.persistentTimelines().getFiltered(true, TriState.FALSE,
                TimelineType.EVERYTHING, null, myAccount.getOrigin());
        assertTrue(!filtered.isEmpty());
        assertThat(filtered.get(0).getTimelineType(), is(TimelineType.EVERYTHING));
    }

    private void ensureAtLeastOneNotDisplayedTimeline() {
        Collection<Timeline> timelines = myContext.persistentTimelines().values();
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
            myContext.persistentTimelines().saveChanged();
        }
    }

    @Test
    public void testDefaultTimelinesForAccounts() {
        new DemoAccountInserter(myContext).checkDefaultTimelinesForAccounts();
    }

    @Test
    public void testDefaultTimelinesForOrigins() {
        new DemoOriginInserter(myContext).checkDefaultTimelinesForOrigins();
    }

    @Test
    public void testDefaultTimeline() {
        Timeline defaultStored = myContext.persistentTimelines().getDefault();

        myContext.persistentTimelines().resetDefaultSelectorOrder();
        Timeline timeline = myContext.persistentTimelines().getDefault();
        assertTrue(timeline.toString(), timeline.isValid());
        assertEquals(timeline.toString(), TimelineType.HOME, timeline.getTimelineType());
        assertFalse(timeline.toString(), timeline.isCombined());

        Origin origin = myContext.persistentOrigins().fromName(demoData.GNUSOCIAL_TEST_ORIGIN_NAME);
        MyAccount myAccount = myContext.persistentAccounts().getFirstSucceededForOrigin(origin);
        assertTrue(myAccount.isValid());
        timeline = myContext.persistentTimelines().
                getFiltered(false, TriState.FALSE, TimelineType.UNKNOWN, myAccount, null).get(2);
        myContext.persistentTimelines().setDefault(timeline);

        Timeline timeline2 = myContext.persistentTimelines().getDefault();
        assertEquals(timeline, timeline2);

        myContext.persistentTimelines().setDefault(defaultStored);
    }
}
