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
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.origin.DemoOriginInserter;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.andstatus.app.context.DemoData.demoData;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class PersistentTimelinesTest {
    private MyContext myContext;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        myContext = MyContextHolder.get();
    }

    @Test
    public void testList() throws Exception {
        Collection<Timeline> timelines = myContext.timelines().values();
        assertTrue(timelines.size() > 0);
    }

    @Test
    public void testFilteredList() throws Exception {
        Collection<Timeline> timelines = myContext.timelines().values();
        Set<Timeline> filtered = myContext.timelines().filter(
                false, TriState.UNKNOWN, TimelineType.UNKNOWN, MyAccount.EMPTY, Origin.EMPTY);
        assertEquals(timelines.size(), filtered.size());

        filtered = myContext.timelines().filter(
                true, TriState.FALSE, TimelineType.UNKNOWN, MyAccount.EMPTY, Origin.EMPTY);
        assertTrue(timelines.size() > filtered.size());

        filtered = myContext.timelines().filter(
                true, TriState.TRUE, TimelineType.UNKNOWN, MyAccount.EMPTY, Origin.EMPTY);
        assertTrue(!filtered.isEmpty());
        assertTrue(timelines.size() > filtered.size());

        ensureAtLeastOneNotDisplayedTimeline();
        Set<Timeline> filtered2 = myContext.timelines().filter(
                true, TriState.UNKNOWN, TimelineType.UNKNOWN, MyAccount.EMPTY, Origin.EMPTY);
        assertTrue(timelines.size() > filtered2.size());
        assertTrue(filtered2.size() > filtered.size());

        MyAccount myAccount = demoData.getMyAccount(demoData.conversationAccountName);
        filtered = myContext.timelines().filter(
                true, TriState.FALSE, TimelineType.UNKNOWN, myAccount, Origin.EMPTY);
        assertTrue(!filtered.isEmpty());

        filtered = myContext.timelines().filter(
                true, TriState.FALSE, TimelineType.UNKNOWN, MyAccount.EMPTY, myAccount.getOrigin());
        assertTrue(!filtered.isEmpty());

        filtered = myContext.timelines().filter(true, TriState.FALSE,
                TimelineType.EVERYTHING, MyAccount.EMPTY, myAccount.getOrigin());
        assertTrue(!filtered.isEmpty());
        assertThat(filtered.stream().filter(timeline -> timeline.getTimelineType() == TimelineType.EVERYTHING)
                .collect(Collectors.toList()), not(empty()));
    }

    private void ensureAtLeastOneNotDisplayedTimeline() {
        Collection<Timeline> timelines = myContext.timelines().values();
        boolean found = false;
        Timeline timeline1 = Timeline.EMPTY;
        for (Timeline timeline : timelines) {
            if (timeline.isDisplayedInSelector().equals(DisplayedInSelector.NEVER)) {
                found = true;
                break;
            }
            if (timeline1 == Timeline.EMPTY && timeline.getTimelineType().equals(TimelineType.FOLLOWERS)) {
                timeline1 = timeline;
            }
        }
        if (!found && timeline1.nonEmpty()) {
            timeline1.setDisplayedInSelector(DisplayedInSelector.NEVER);
            myContext.timelines().saveChanged();
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
        Timeline defaultStored = myContext.timelines().getDefault();

        myContext.timelines().resetDefaultSelectorOrder();
        Timeline timeline1 = myContext.timelines().getDefault();
        assertTrue(timeline1.toString(), timeline1.isValid());
        assertEquals(timeline1.toString(), TimelineType.HOME, timeline1.getTimelineType());
        assertFalse(timeline1.toString(), timeline1.isCombined());

        Origin origin = myContext.origins().fromName(demoData.gnusocialTestOriginName);
        MyAccount myAccount = myContext.accounts().getFirstSucceededForOrigin(origin);
        assertTrue(myAccount.isValid());
        Timeline timeline2 = myContext.timelines().
                filter(false, TriState.FALSE, TimelineType.UNKNOWN, myAccount, Origin.EMPTY)
                .stream().filter(timeline -> timeline != timeline1).findFirst().orElse(Timeline.EMPTY);
        myContext.timelines().setDefault(timeline2);

        assertNotEquals(timeline1, myContext.timelines().getDefault());

        myContext.timelines().setDefault(defaultStored);
    }

    @Test
    public void testFromIsCombined() {
        MyAccount myAccount = demoData.getMyAccount(demoData.conversationAccountName);
        oneFromIsCombined(myAccount, TimelineType.PUBLIC);
        oneFromIsCombined(myAccount, TimelineType.EVERYTHING);
        oneFromIsCombined(myAccount, TimelineType.HOME);
        oneFromIsCombined(myAccount, TimelineType.NOTIFICATIONS);
    }

    private void oneFromIsCombined(MyAccount myAccount, TimelineType timelineType) {
        Timeline combinedTimeline = myContext.timelines().
                filter(true, TriState.TRUE, timelineType, myAccount, Origin.EMPTY).iterator().next();
        Timeline notCombinedTimeline = combinedTimeline.fromIsCombined(myContext, false);
        assertEquals("Should be not combined " + notCombinedTimeline, false, notCombinedTimeline.isCombined());
        Timeline combinedTimeline2 = notCombinedTimeline.fromIsCombined(myContext, true);
        assertEquals("Should be combined " + notCombinedTimeline, true, combinedTimeline2.isCombined());
        assertEquals(combinedTimeline, combinedTimeline2);
    }

    @Test
    public void testFromMyAccount() {
        MyAccount myAccount1 = demoData.getMyAccount(demoData.pumpioTestAccountName);
        MyAccount myAccount2 = demoData.getMyAccount(demoData.gnusocialTestAccountName);
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.PUBLIC);
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.EVERYTHING);
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.HOME);
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.NOTIFICATIONS);
    }

    private void oneFromMyAccount(MyAccount myAccount1, MyAccount myAccount2, TimelineType timelineType) {
        final Set<Timeline> timelines = myContext.timelines().
                filter(true, TriState.FALSE, timelineType, myAccount1, myAccount1.getOrigin());
        Timeline timeline1 = timelines.isEmpty()
                ? myContext.timelines().get(timelineType, myAccount1.getActorId(), myAccount1.getOrigin(), "")
                : timelines.iterator().next();
        Timeline timeline2 = timeline1.fromMyAccount(myContext, myAccount2);
        assertEquals("Should be not combined " + timeline2, false, timeline2.isCombined());
        if (timelineType.isForUser()) {
            assertEquals("Account should change " + timeline2, myAccount2, timeline2.getMyAccount());
        } else {
            assertEquals("Origin should change " + timeline2, myAccount2.getOrigin(), timeline2.getOrigin());
        }
        Timeline timeline3 = timeline2.fromMyAccount(myContext, myAccount1);
        assertEquals(timeline1, timeline3);
    }

    @Test
    public void testUserTimelines() throws Exception {
        final MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(ma.isValid());
        MyContextHolder.get().accounts().setCurrentAccount(ma);
        long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, ma.getOriginId(), demoData.conversationAuthorSecondActorOid);

        final Timeline timeline = Timeline.getTimeline(TimelineType.SENT, actorId, ma.getOrigin());
        assertEquals("Should not be combined: " + timeline, false, timeline.isCombined());

        Collection<Timeline> timelines = myContext.timelines().values().stream()
                .filter(timeline1 -> timeline1.getTimelineType() == TimelineType.SENT
                        && timeline1.isCombined() && timeline1.getActorId() != 0).collect(Collectors.toList());
        assertThat(timelines, is(empty()));
    }

}
