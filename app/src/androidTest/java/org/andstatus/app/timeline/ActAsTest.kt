/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.note.ConversationActivity
import org.andstatus.app.note.NoteContextMenuItem
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class ActAsTest : TimelineActivityTest<ActivityViewItem>() {

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        Assert.assertTrue(ma.isValid)
         MyContextHolder.myContextHolder.getNow().accounts().setCurrentAccount(ma)
        MyLog.i(this, "setUp ended")
        val timeline: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.EVERYTHING, Actor.EMPTY, ma.origin)
        timeline.forgetPositionsAndDates()
        return Intent(Intent.ACTION_VIEW, timeline.getUri())
    }

    @Test
    @Throws(InterruptedException::class)
    fun actAsActor() {
        TestSuite.waitForListLoaded(activity, 2)
        Assert.assertEquals("Default actor", MyAccount.EMPTY, activity.getContextMenu()?.getSelectedActingAccount())
        for (attempt in 1..4) {
            if (oneAttempt(attempt)) break
        }
    }

    @Throws(InterruptedException::class)
    private fun oneAttempt(attempt: Int): Boolean {
        val method = "actAsActor"
        val listData = activity.getListData()
        val helper = ListScreenTestHelper<TimelineActivity<*>>(activity,
                ConversationActivity::class.java)
        val listItemId = helper.getListItemIdOfLoadedReply()
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        val myContext: MyContext =  MyContextHolder.myContextHolder.getNow()
        val origin = myContext.origins().fromId(MyQuery.noteIdToOriginId(noteId))
        var logMsg = ("attempt=" + attempt + ", itemId=" + listItemId + ", noteId=" + noteId
                + ", origin=" + origin.name
                + ", text='" + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'")
        val invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT, R.id.note_wrapper)
        val actor1 = activity.getContextMenu()?.getSelectedActingAccount() ?: MyAccount.EMPTY
        logMsg += ";${if (invoked) "" else " failed to invoke context menu 1,"}\nactor1=$actor1"
        if (activity.getListData() !== listData) return false
        Assert.assertTrue("Actor is not valid. $logMsg", actor1.isValid)
        Assert.assertEquals(logMsg, origin, actor1.origin)
        ActivityTestHelper.closeContextMenu(activity)
        logMsg += "\nMyContext: $myContext"
        val firstOtherActor = myContext.accounts().firstOtherSucceededForSameOrigin(origin, actor1)
        logMsg += "\nfirstOtherActor=$firstOtherActor"
        if (activity.getListData() !== listData) return false
        Assert.assertNotEquals(logMsg, actor1, firstOtherActor)
        val invoked2 = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT, R.id.note_wrapper)
        val actor2 = activity.getContextMenu()?.getSelectedActingAccount()
        logMsg += ";${if (invoked2) "" else " failed to invoke context menu 2,"}\nactor2=$actor2"
        if (activity.getListData() !== listData) return false
        Assert.assertNotEquals(logMsg, actor1, actor2)
        return true
    }
}
