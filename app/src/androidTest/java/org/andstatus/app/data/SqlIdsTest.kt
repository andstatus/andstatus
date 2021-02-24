/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.TriState
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class SqlIdsTest {
    @Before
    fun setUp() {
        TestSuite.initializeWithData(this)
    }

    @Test
    fun fromTimeline() {
        val myAccount: MyAccount =  MyContextHolder.myContextHolder.getNow().accounts().fromAccountName(DemoData.Companion.demoData.conversationAccountName)
        Assert.assertTrue("account is not valid: " + DemoData.Companion.demoData.conversationAccountName, myAccount.isValid)
        val timeline: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().filter(false, TriState.FALSE,
                TimelineType.SENT, myAccount.actor, myAccount.origin).findFirst().orElse(Timeline.Companion.EMPTY)
        Assert.assertNotEquals(0, SqlIds.Companion.actorIdsOfTimelineActor(timeline).size().toLong())
        val timelineCombined: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().filter(false, TriState.TRUE,
                TimelineType.SENT, Actor.Companion.EMPTY,  Origin.EMPTY).findFirst().orElse(Timeline.Companion.EMPTY)
        Assert.assertNotEquals("No actors for $timelineCombined", 0, SqlIds.Companion.actorIdsOfTimelineActor(timelineCombined).size().toLong())
        val actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, myAccount.originId, DemoData.Companion.demoData.conversationAuthorSecondActorOid)
        val actor: Actor = Actor.Companion.load( MyContextHolder.myContextHolder.getNow(), actorId)
        Assert.assertNotEquals("No actor for " + DemoData.Companion.demoData.conversationAuthorSecondActorOid, 0, actorId)
        val timelineUser: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.SENT, actor, myAccount.origin)
        Assert.assertNotEquals("No actors for $timelineUser", 0, SqlIds.Companion.actorIdsOfTimelineActor(timelineCombined).size().toLong())
    }
}