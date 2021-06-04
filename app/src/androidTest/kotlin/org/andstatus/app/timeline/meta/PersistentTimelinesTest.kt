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
package org.andstatus.app.timeline.meta

import org.andstatus.app.account.DemoAccountInserter
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.DemoOriginInserter
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.TriState
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.hamcrest.core.Is
import org.hamcrest.core.IsNot
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.stream.Collectors
import kotlin.properties.Delegates

class PersistentTimelinesTest {
    private var myContext: MyContext by Delegates.notNull()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithData(this)
        myContext =  MyContextHolder.myContextHolder.getNow()
    }

    @Test
    @Throws(Exception::class)
    fun testList() {
        val timelines = myContext.timelines().values()
        Assert.assertTrue(timelines.size > 0)
    }

    @Test
    @Throws(Exception::class)
    fun testFilteredList() {
        val timelines = myContext.timelines().values()
        var count = myContext.timelines().filter(
                false, TriState.UNKNOWN, TimelineType.UNKNOWN, Actor.EMPTY,  Origin.EMPTY).count()
        Assert.assertEquals(timelines.size.toLong(), count)
        count = myContext.timelines().filter(
                true, TriState.FALSE, TimelineType.UNKNOWN, Actor.EMPTY,  Origin.EMPTY).count()
        Assert.assertTrue(timelines.size > count)
        count = myContext.timelines().filter(
                true, TriState.TRUE, TimelineType.UNKNOWN, Actor.EMPTY,  Origin.EMPTY).count()
        Assert.assertTrue(count > 0)
        Assert.assertTrue(timelines.size > count)
        ensureAtLeastOneNotDisplayedTimeline()
        val count2 = myContext.timelines().filter(
                true, TriState.UNKNOWN, TimelineType.UNKNOWN, Actor.EMPTY,  Origin.EMPTY).count()
        Assert.assertTrue("count2:$count2; $timelines", timelines.size > count2)
        Assert.assertTrue(count2 > count)
        val myAccount: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        count = myContext.timelines().filter(
                true, TriState.FALSE, TimelineType.UNKNOWN, myAccount.actor,  Origin.EMPTY).count()
        Assert.assertTrue(count > 0)
        count = myContext.timelines().filter(
                true, TriState.FALSE, TimelineType.UNKNOWN, Actor.EMPTY, myAccount.origin).count()
        Assert.assertTrue(count > 0)
        val filtered = myContext.timelines().filter(true, TriState.FALSE,
                TimelineType.EVERYTHING, Actor.EMPTY, myAccount.origin).collect(Collectors.toList())
        Assert.assertTrue(filtered.size > 0)
        Assert.assertTrue(filtered.stream()
                .filter { timeline: Timeline -> timeline.timelineType == TimelineType.EVERYTHING }.count() > 0)
    }

    private fun ensureAtLeastOneNotDisplayedTimeline() {
        val timelines = myContext.timelines().values()
        var found = false
        var timeline1: Timeline = Timeline.EMPTY
        for (timeline in timelines) {
            if (timeline.isDisplayedInSelector() == DisplayedInSelector.NEVER) {
                found = true
                break
            }
            if (timeline1 === Timeline.EMPTY && timeline.timelineType == TimelineType.INTERACTIONS) {
                timeline1 = timeline
            }
        }
        if (!found && timeline1.nonEmpty) {
            timeline1.setDisplayedInSelector(DisplayedInSelector.NEVER)
            myContext.timelines().saveChanged()
        }
    }

    @Test
    fun testDefaultTimelinesForAccounts() {
        DemoAccountInserter(myContext)
        DemoAccountInserter.Companion.assertDefaultTimelinesForAccounts()
    }

    @Test
    fun testDefaultTimelinesForOrigins() {
        DemoOriginInserter(myContext)
        DemoOriginInserter.Companion.assertDefaultTimelinesForOrigins()
    }

    @Test
    fun testDefaultTimeline() {
        val defaultStored = myContext.timelines().getDefault()
        myContext.timelines().resetDefaultSelectorOrder()
        val timeline1 = myContext.timelines().getDefault()
        Assert.assertTrue(timeline1.toString(), timeline1.isValid())
        Assert.assertEquals(timeline1.toString(), TimelineType.HOME, timeline1.timelineType)
        Assert.assertFalse(timeline1.toString(), timeline1.isCombined)
        val origin = myContext.origins().fromName(DemoData.demoData.gnusocialTestOriginName)
        val myAccount = myContext.accounts().getFirstPreferablySucceededForOrigin(origin)
        Assert.assertTrue(myAccount.isValid)
        val timeline2 = myContext.timelines()
                .filter(false, TriState.FALSE, TimelineType.UNKNOWN, myAccount.actor,  Origin.EMPTY)
                .filter { timeline: Timeline? -> timeline !== timeline1 }.findFirst().orElse(Timeline.EMPTY)
        myContext.timelines().setDefault(timeline2)
        Assert.assertNotEquals(timeline1, myContext.timelines().getDefault())
        myContext.timelines().setDefault(defaultStored)
    }

    @Test
    fun testFromIsCombined() {
        val myAccount: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        oneFromIsCombined(myAccount, TimelineType.PUBLIC)
        oneFromIsCombined(myAccount, TimelineType.EVERYTHING)
        oneFromIsCombined(myAccount, TimelineType.HOME)
        oneFromIsCombined(myAccount, TimelineType.NOTIFICATIONS)
    }

    private fun oneFromIsCombined(myAccount: MyAccount, timelineType: TimelineType) {
        val combined = myContext.timelines()
                .filter(true, TriState.TRUE, timelineType, myAccount.actor,  Origin.EMPTY)
                .findFirst().orElse(Timeline.EMPTY)
        val notCombined = combined.fromIsCombined(myContext, false)
        Assert.assertEquals("Should be not combined $notCombined", false, notCombined.isCombined)
        val combined2 = notCombined.fromIsCombined(myContext, true)
        Assert.assertEquals("Should be combined $notCombined", true, combined2.isCombined)
        Assert.assertEquals(combined, combined2)
    }

    @Test
    fun testFromMyAccount() {
        val myAccount1: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.pumpioTestAccountName)
        val myAccount2: MyAccount = DemoData.demoData.getGnuSocialAccount()
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.PUBLIC)
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.EVERYTHING)
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.HOME)
        oneFromMyAccount(myAccount1, myAccount2, TimelineType.NOTIFICATIONS)
    }

    private fun oneFromMyAccount(ma1: MyAccount, ma2: MyAccount, timelineType: TimelineType) {
        val timeline1 = myContext.timelines()
                .filter(true, TriState.FALSE, timelineType, ma1.actor, ma1.origin)
                .findFirst().orElseGet { myContext.timelines().get(timelineType, ma1.actor, ma1.origin) }
        Assert.assertEquals("Should be not combined $timeline1", false, timeline1.isCombined)
        val timeline2 = timeline1.fromMyAccount(myContext, ma2)
        Assert.assertEquals("Should be not combined \n${timeline2}from:\n$timeline1",
                false, timeline2.isCombined)
        if (timelineType.isForUser()) {
            Assert.assertEquals("Account should change $timeline2", ma2, timeline2.myAccountToSync)
        } else {
            Assert.assertEquals("Origin should change $timeline2", ma2.origin, timeline2.getOrigin())
        }
        val timeline3 = timeline2.fromMyAccount(myContext, ma1)
        Assert.assertEquals(timeline1, timeline3)
    }

    @Test
    fun testUserTimelines() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        myContext.accounts().setCurrentAccount(ma)
        val actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, ma.originId, DemoData.demoData.conversationAuthorSecondActorOid)
        val actor: Actor = Actor.Companion.fromId(ma.origin, actorId)
        val timeline = myContext.timelines()[TimelineType.SENT, actor, ma.origin]
        Assert.assertEquals("Should not be combined: $timeline", false, timeline.isCombined)
        val timelines: MutableCollection<Timeline?>? = myContext.timelines().stream()
                .filter { timeline1: Timeline -> timeline1.timelineType == TimelineType.SENT && timeline1.isCombined && timeline1.getActorId() != 0L }.collect(Collectors.toList())
        MatcherAssert.assertThat(timelines, Is.`is`(Matchers.empty()))
    }

    @Test
    fun syncForAllAccounts() {
        val combined = myContext.timelines().forUser(TimelineType.NOTIFICATIONS, Actor.EMPTY)
        Assert.assertEquals("Should be combined: $combined", true, combined.isCombined)
        Assert.assertNotEquals("Should exist: $combined", 0, combined.getId())
        Assert.assertEquals("Should not have account: $combined", MyAccount.EMPTY, combined.myAccountToSync)
        val accountsToSync = myContext.accounts().accountsToSync()
        MatcherAssert.assertThat(accountsToSync, Is.`is`(IsNot.not(Matchers.empty())))
        var syncableFound = false
        for (accountToSync in accountsToSync) {
            val forOneAccount = combined.cloneForAccount(myContext, accountToSync)
            Assert.assertEquals("Should have selected account: $forOneAccount", accountToSync, forOneAccount.myAccountToSync)
            Assert.assertEquals("Timeline type: $forOneAccount", combined.timelineType, forOneAccount.timelineType)
            if (accountToSync.origin.originType.isTimelineTypeSyncable(forOneAccount.timelineType)) {
                Assert.assertEquals("Should be syncable: $forOneAccount", true, forOneAccount.isSyncable())
                syncableFound = true
            }
        }
        Assert.assertTrue("No syncable timelines for $accountsToSync", syncableFound)
    }
}
