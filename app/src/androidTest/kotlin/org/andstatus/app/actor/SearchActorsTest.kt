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
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.net.social.Actor
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

class SearchActorsTest : ActivityTest<ActorsScreen>() {
    override fun getActivityClass(): Class<ActorsScreen> {
        return ActorsScreen::class.java
    }

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithAccounts(this)
        MyLog.i(this, "setUp ended")
        return Intent(MyAction.VIEW_ACTORS.action,
                MatchedUri.Companion.getActorsScreenUri(ActorsScreenType.ACTORS_AT_ORIGIN, 0, 0, DemoData.demoData.t131tUsername))
    }

    @Test
    @Throws(InterruptedException::class)
    fun testSearchActor() {
        TestSuite.waitForListLoaded(activity, 2)
        val listItems = activity.getListLoader().getList()
        Assert.assertTrue("Found only ${listItems.size}items\n$listItems", listItems.size > 1)
        val actor: Actor = ActorsScreenTest.getByActorOid(listItems, DemoData.demoData.pumpioTestAccountActorOid)
        Assert.assertEquals("Actor was not found\n$listItems", DemoData.demoData.pumpioTestAccountActorOid, actor.oid)
    }
}
