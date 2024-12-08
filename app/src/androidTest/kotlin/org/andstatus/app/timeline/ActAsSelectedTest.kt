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
import kotlinx.coroutines.runBlocking
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.note.NoteContextMenuItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.SelectorDialog
import org.junit.Assert
import org.junit.Test

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
class ActAsSelectedTest : TimelineActivityTest<ActivityViewItem>() {

    override fun getActivityIntent(): Intent = runBlocking {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        val myContext: MyContext = myContextHolder.getCompleted()
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        myContext.accounts.setCurrentAccount(ma)
        MyLog.i(this, "setUp ended")
        Intent(Intent.ACTION_VIEW, myContext.timelines.get(TimelineType.HOME, ma.actor, Origin.EMPTY).getUri())
    }

    /** TODO: Fix corresponding code and this test */
    @Test
    fun testActAs() {
        val method = "testActAs"
        TestSuite.waitForListLoaded(activity, 2)
        val helper: ListActivityTestHelper<TimelineActivity<*>> =
            ListActivityTestHelper.newForSelectorDialog<TimelineActivity<*>>(
                activity,
                SelectorDialog.Companion.dialogTag
            )
        val listItemId = helper.getListItemIdOfLoadedReply()
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        val myContext: MyContext = myContextHolder.getNow()
        val origin = myContext.origins.fromId(MyQuery.noteIdToOriginId(noteId))
        val accounts = myContext.accounts.succeededForSameOrigin(origin)
        var logMsg = ("itemId=" + listItemId + ", noteId=" + noteId
            + ", origin=" + origin.name
            + ", text='" + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'"
            + ", " + accounts.size + " accounts found: " + accounts)

        MyLog.v(method, "Before invoking nonexistent invokeContextMenuAction4ListItemId ACT_AS_FIRST_OTHER_ACCOUNT")
        // This context menu item doesn't exist
        Assert.assertTrue(
            logMsg, helper.invokeContextMenuAction4ListItemId(
                method, listItemId,
                NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT, R.id.note_wrapper
            )
        )
        val account1 = activity.getContextMenu()?.getActingAccount() ?: MyAccount.EMPTY
        logMsg = "\nActing account 1: $account1, $logMsg"
        Assert.assertTrue(logMsg, accounts.contains(account1))
        Assert.assertEquals(logMsg, origin, account1.origin)
        ActivityTestHelper.closeContextMenu(activity)
        MyLog.v(method, "After closeContextMenu 1")
        DbUtils.waitMs(method, 1000)

        MyLog.v(method, "Before invoking invokeContextMenuAction4ListItemId ACT_AS")
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.ACT_AS, R.id.note_wrapper)
        MyLog.v(method, "After invoking invokeContextMenuAction4ListItemId ACT_AS")
        DbUtils.waitMs(method, 1000)

        val account2 = myContext.accounts.firstOtherSucceededForSameOrigin(origin, account1)
        val account2Text = "account 2:${account2.accountName} id:${account2.actorId}"
        logMsg += ",\n $account2Text"
        Assert.assertNotSame(logMsg, account1, account2)

        MyLog.v(method, "Before clicking on $account2Text")
        helper.selectIdFromSelectorDialog(logMsg, account2.actorId)
        MyLog.v(method, "After clicking on $account2Text")
        DbUtils.waitMs(method, 1000)

        MyLog.v(method, "After waiting on $account2Text")
        val account3 = activity.getContextMenu()?.getSelectedActingAccount()
        logMsg += ",\n actor2Actual:${account3?.accountName}\n"
        Assert.assertEquals(logMsg, account2, account3)
    }
}
