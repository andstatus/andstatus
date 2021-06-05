/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.social.Actor
import org.andstatus.app.timeline.ListScreenTestHelper
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

class ActorsScreenWorkTest : ActivityTest<ActorsScreen>() {

    override fun getActivityClass(): Class<ActorsScreen> {
        return ActorsScreen::class.java
    }

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        val noteId = MyQuery.oidToId(OidEnum.NOTE_OID, DemoData.demoData.getPumpioConversationOrigin().id,
                DemoData.demoData.conversationMentionsNoteOid)
        Assert.assertTrue(noteId > 0)
        MyLog.i(this, "setUp ended")
        return Intent(MyAction.VIEW_ACTORS.action,
                MatchedUri.Companion.getActorsScreenUri(ActorsScreenType.ACTORS_OF_NOTE, DemoData.demoData.getPumpioConversationOrigin().id,
                        noteId, ""))
    }

    @Test
    fun testFriendsList() {
        val method = "testFriendsList"
        TestSuite.waitForListLoaded(activity, 2)
        val helper = ListScreenTestHelper(activity, FollowersScreen::class.java)
        val listItems = activity.getListLoader().getList()
        Assert.assertEquals(listItems.toString(), 5, listItems.size.toLong())
        val actor: Actor = ActorsScreenTest.getByActorOid(listItems, DemoData.demoData.conversationAuthorThirdActorOid)
        Assert.assertTrue("Not found " + DemoData.demoData.conversationAuthorThirdActorOid, actor.nonEmpty)
        Assert.assertTrue("Invoked Context menu for $actor", helper.invokeContextMenuAction4ListItemId(
                method, actor.actorId, ActorContextMenuItem.FRIENDS, 0))
        val followersScreen = helper.waitForNextActivity(method, 15000) as FollowersScreen
        TestSuite.waitForListLoaded(followersScreen, 1)
        val items = followersScreen.getListLoader().getList()
        val followersHelper = ListScreenTestHelper(followersScreen)
        followersHelper.clickListAtPosition(method,
                followersHelper.getPositionOfListItemId(items[0].getActorId()))
        DbUtils.waitMs(method, 500)
    }
}
