/*
 * Copyright (C) 2022 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.actor

import android.content.Intent
import org.andstatus.app.MyAction
import org.andstatus.app.actor.ActorsScreenTest.Companion.actorByOid
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.GroupMembership
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.net.social.Actor
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

class ListMembersTest : ActivityTest<GroupMembersScreen>() {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private val accountActor = myContext.accounts.fromAccountName(DemoData.demoData.mastodonTestAccountName)
    private val listOfUser: Actor = GroupMembership.getSingleGroupMemberIds(myContext, accountActor.actorId, GroupType.LISTS)
        .map { Actor.load(myContext, it, true, Actor::EMPTY) }
        .find { it.oid.endsWith("13578") } ?: Actor.EMPTY


    override fun getActivityClass(): Class<GroupMembersScreen> {
        return GroupMembersScreen::class.java
    }

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp ended")
        return Intent(
            MyAction.VIEW_ACTORS.action,
            MatchedUri.Companion.getActorsScreenUri(
                ActorsScreenType.LIST_MEMBERS, accountActor.originId,
                listOfUser.actorId, ""
            )
        )
    }

    @Test
    fun testListMembersScreen() {
        val method = this::testListMembersScreen.name
        if (listOfUser.isEmpty) {
            MyLog.i(this, "$method; Skipped: no List of user found")
            return
        }
        assertKnownMastodonListMembers(activity, listOfUser)
        DbUtils.waitMs(method, 500)
    }

    companion object {
        fun assertKnownMastodonListMembers(activity: ActorsScreen, listOfUser: Actor) {
            TestSuite.waitForListLoaded(activity, 2)

            val memberItems = activity.getListLoader().getList()
            Assert.assertEquals("Incorrect number of members in $listOfUser.\nMembers: $memberItems", 3, memberItems.size)
            val expectedOid = "179473"
            val member: Actor = memberItems.actorByOid(expectedOid)
            Assert.assertTrue("Not found member $expectedOid in $memberItems", member.nonEmpty)
            Assert.assertEquals("Member of $listOfUser: $member", "andstatus@mstdn.io", member.getWebFingerId())
        }
    }
}
