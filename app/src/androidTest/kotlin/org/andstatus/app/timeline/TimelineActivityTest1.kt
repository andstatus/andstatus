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
import android.view.View
import android.widget.CheckBox
import android.widget.ListView
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.note.ConversationActivity
import org.andstatus.app.note.NoteContextMenuItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceEvent
import org.andstatus.app.service.MyServiceEventsBroadcaster
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.SelectorDialog
import org.junit.Assert
import org.junit.Test

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
class TimelineActivityTest1 : TimelineActivityTest<ActivityViewItem>() {
    private var ma: MyAccount = MyAccount.EMPTY

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        ma = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        myContextHolder.getNow().accounts.setCurrentAccount(ma)
        MyLog.i(this, "setUp ended")
        return Intent(
            Intent.ACTION_VIEW, myContextHolder.getNow().timelines
                .get(TimelineType.HOME, ma.actor, Origin.EMPTY).getUri()
        )
    }

    @Test
    fun testOpeningConversationActivity() {
        val method = "testOpeningConversationActivity"
        TestSuite.waitForListLoaded(activity, 7)
        Assert.assertTrue("MyService is available", MyServiceManager.Companion.isServiceAvailable())
        val helper = ListActivityTestHelper<TimelineActivity<*>>(
            activity,
            ConversationActivity::class.java
        )
        val noteId = helper.getListItemIdOfLoadedReply()
        helper.selectListPosition(method, helper.getPositionOfListItemId(noteId))
        helper.invokeContextMenuAction4ListItemId(
            method,
            noteId,
            NoteContextMenuItem.OPEN_CONVERSATION,
            R.id.note_wrapper
        )
        val nextActivity = helper.waitForNextActivity(method, 40000)
        DbUtils.waitMs(method, 500)
        nextActivity?.finish()
    }

    private fun getListView(): ListView? {
        return activity.findViewById<View?>(android.R.id.list) as ListView?
    }

    /** It really makes difference if we are near the end of the list or not
     * This is why we have two similar methods
     */
    @Test
    fun testPositionOnContentChange1() {
        onePositionOnContentChange(5, 1)
    }

    @Test
    fun testPositionOnContentChange2() {
        onePositionOnContentChange(0, 2)
    }

    private fun onePositionOnContentChange(position0: Int, iterationId: Int) {
        val method = "testPositionOnContentChange$iterationId"
        TestSuite.waitForListLoaded(activity, 1)
        getInstrumentation().runOnMainSync { activity.showList(WhichPage.TOP) }
        TestSuite.waitForListLoaded(activity, position0 + 2)
        val timelineData = activity.getListData()
        for (ind in 0 until timelineData.size()) {
            val item = timelineData.getItem(ind)
            Assert.assertEquals("Origin of the Item $ind $item", ma.origin, item.origin)
        }
        val collapseDuplicates = MyPreferences.isCollapseDuplicates()
        Assert.assertEquals(
            collapseDuplicates,
            (activity.findViewById<View?>(R.id.collapseDuplicatesToggle) as CheckBox).isChecked
        )
        Assert.assertEquals(collapseDuplicates, activity.getListData().isCollapseDuplicates())
        getCurrentListPosition().logV("$method; before selecting position $position0")
        ListActivityTestHelper(activity).selectListPosition(method, position0)
        var itemIdOfSelected: Long = 0
        for (attempt in 0..9) {
            EspressoUtils.waitForIdleSync()
            if (LoadableListPosition.Companion.getViewOfPosition(getListView(), position0) != null) {
                itemIdOfSelected = activity.getListAdapter().getItemId(position0)
                if (itemIdOfSelected > 0) break
            }
        }
        Assert.assertTrue("Position $position0 should be selected: " + getCurrentListPosition(), itemIdOfSelected > 0)
        getCurrentListPosition().logV("$method; after selecting position $position0 itemId=$itemIdOfSelected")
        val pos1 = getCurrentListPosition().logV("$method; stored pos1 before adding new content")
        val updatedAt1 = activity.getListData().updatedAt
        DemoData.demoData.insertPumpIoConversation("p$iterationId")
        broadcastCommandExecuted()
        var pos2 = getCurrentListPosition().logV("$method; just after adding new content")
        var updatedAt2: Long = 0
        for (attempt in 0..9) {
            EspressoUtils.waitForIdleSync()
            updatedAt2 = activity.getListData().updatedAt
            if (updatedAt2 > updatedAt1) break
        }
        Assert.assertTrue("Timeline data should be updated: " + activity.getListData(), updatedAt2 > updatedAt1)
        var positionOfItem = -1
        var found = false
        var attempt = 0
        while (attempt < 10 && !found) {
            EspressoUtils.waitForIdleSync()
            pos2 = getCurrentListPosition().logV("$method; waiting for list reposition $attempt")
            positionOfItem = activity.getListAdapter().getPositionById(pos1.itemId)
            if (positionOfItem >= pos2.position - 1 && positionOfItem <= pos2.position + 1) {
                found = true
            }
            attempt++
        }
        val logText = (method + " The item id=" + pos1.itemId + " was " + (if (found) "" else " not") + " found. "
            + "pos1=" + pos1 + "; pos2=" + pos2
            + (if (positionOfItem >= 0) " foundAt=$positionOfItem" else "")
            + ", updated in " + (updatedAt2 - updatedAt1) + "ms")
        MyLog.v(this, logText)
        Assert.assertTrue(logText, found)
        Assert.assertEquals(
            collapseDuplicates,
            (activity.findViewById<View?>(R.id.collapseDuplicatesToggle) as CheckBox).isChecked
        )
        Assert.assertEquals(collapseDuplicates, activity.getListData().isCollapseDuplicates())
        if (collapseDuplicates) {
            found = false
            for (ind in 0 until activity.getListData().size()) {
                val item: ViewItem<*> = activity.getListData().getItem(ind)
                if (item.isCollapsed) {
                    found = true
                    break
                }
            }
            Assert.assertTrue("Collapsed not found", found)
        }
    }

    private fun getCurrentListPosition(): LoadableListPosition<*> {
        return activity.getCurrentListPosition()
    }

    private fun broadcastCommandExecuted() {
        val commandData: CommandData = CommandData.Companion.newAccountCommand(
            CommandEnum.LIKE,
            DemoData.demoData.getPumpioConversationAccount()
        )
        MyServiceEventsBroadcaster.Companion.newInstance(myContextHolder.getNow(), MyServiceState.RUNNING)
            .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND)
            .broadcast()
    }

    @Test
    fun testOpeningAccountSelector() {
        val method = "testOpeningAccountSelector"
        TestSuite.waitForListLoaded(activity, 7)
        val helper: ListActivityTestHelper<TimelineActivity<*>> =
            ListActivityTestHelper.newForSelectorDialog<TimelineActivity<*>>(
                activity,
                SelectorDialog.Companion.dialogTag
            )
        helper.clickView(method, R.id.selectAccountButton)
        val selectorDialog = helper.waitForSelectorDialog(method, 15000)
        DbUtils.waitMs(method, 500)
        selectorDialog?.dismiss()
    }

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
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.ACT_AS, R.id.note_wrapper)
        val account2 = myContext.accounts.firstOtherSucceededForSameOrigin(origin, account1)
        logMsg += ", account 2:" + account2.accountName
        Assert.assertNotSame(logMsg, account1, account2)
        helper.selectIdFromSelectorDialog(logMsg, account2.actorId)
        DbUtils.waitMs(method, 500)
        val account3 = activity.getContextMenu()?.getSelectedActingAccount() ?: MyAccount.EMPTY
        logMsg += ", actor2Actual:" + account3.accountName
        Assert.assertEquals(logMsg, account2, account3)
    }
}
