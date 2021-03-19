/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline

import android.content.Intent
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

class ActorTimelineTest : TimelineActivityTest<ActivityViewItem?>() {
    override fun getActivityIntent(): Intent? {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        val myContext: MyContext =  MyContextHolder.myContextHolder.getBlocking()
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        myContext.accounts().setCurrentAccount(ma)
        val actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, ma.originId, DemoData.demoData.conversationAuthorSecondActorOid)
        val actor: Actor = Actor.Companion.fromId(ma.origin, actorId)
        Assert.assertNotEquals("Actor " + DemoData.demoData.conversationAuthorSecondActorOid + " id=" + actorId + " -> "
                + actor, 0, actor.actorId)
        val timeline = myContext.timelines()[TimelineType.SENT, actor, ma.origin]
        Assert.assertFalse("Timeline $timeline", timeline.isCombined)
        timeline.forgetPositionsAndDates()
        MyLog.i(this, "setUp ended, $timeline")
        return Intent(Intent.ACTION_VIEW, timeline.uri)
    }

    @Test
    fun openSecondAuthorTimeline() {
        wrap { _openSecondAuthorTimeline() }
    }

    @Throws(InterruptedException::class)
    private fun _openSecondAuthorTimeline() {
        val method = "openSecondAuthorTimeline"
        TestSuite.waitForListLoaded(activity, 10)
        val timelineData = activity.listData
        var followItem: ActivityViewItem? = ActivityViewItem.Companion.EMPTY
        for (position in 0 until timelineData.size()) {
            val item = timelineData.getItem(position)
            if (item.activityType == ActivityType.FOLLOW) {
                followItem = item
            }
        }
        Assert.assertNotEquals("No follow action by " + DemoData.demoData.conversationAuthorSecondActorOid
                + " in " + timelineData,
                ActivityViewItem.Companion.EMPTY, followItem)
    }
}