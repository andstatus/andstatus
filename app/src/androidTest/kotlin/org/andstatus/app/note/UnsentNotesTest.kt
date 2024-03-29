package org.andstatus.app.note

import android.content.Intent
import android.provider.BaseColumns
import android.view.View
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ReplaceTextAction
import androidx.test.espresso.matcher.ViewMatchers
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.service.MyServiceTestHelper
import org.andstatus.app.timeline.ListActivityTestHelper
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.util.MyLog
import org.junit.After
import org.junit.Assert
import org.junit.Test

class UnsentNotesTest : TimelineActivityTest<ActivityViewItem>() {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private val mService: MyServiceTestHelper = MyServiceTestHelper()

    override fun getActivityIntent(): Intent {
        mService.setUp(null)
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        Assert.assertTrue(ma.isValid)
        myContext.accounts.setCurrentAccount(ma)
        return Intent(
            Intent.ACTION_VIEW,
            myContext.timelines.get(TimelineType.EVERYTHING, Actor.EMPTY, ma.origin).getUri()
        )
    }

    @After
    fun tearDown() {
        mService.tearDown()
    }

    @Test
    fun testEditUnsentNote() {
        val method = "testEditUnsentNote"
        var step = "Start editing a note"
        MyLog.v(this, "$method started")
        val editorView: View = ActivityTestHelper.openEditor("$method; $step", activity)
        val suffix = "unsent" + DemoData.demoData.testRunUid
        val body = "Test unsent note, which we will try to edit $suffix"
        EspressoUtils.waitForIdleSync()
        Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(body))
        EspressoUtils.waitForIdleSync()
        mService.serviceStopped = false
        ActivityTestHelper.clickSendButton(method, activity)
        val found = mService.waitForCondition {
            getHttp().substring2PostedPath("statuses/update").isNotEmpty()
        }
        val condition = NoteTable.CONTENT + " LIKE('%" + suffix + "%')"
        val unsentMsgId = MyQuery.conditionToLongColumnValue(NoteTable.TABLE_NAME, BaseColumns._ID, condition)
        step = "Unsent note $unsentMsgId"
        Assert.assertTrue("$method; $step: $condition", unsentMsgId != 0L)
        Assert.assertEquals(
            "$method; $step", DownloadStatus.SENDING, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, unsentMsgId)
            )
        )
        step = "Start editing unsent note $unsentMsgId"
        activity.getNoteEditor()?.startEditingNote(NoteEditorData.Companion.load(myContext, unsentMsgId))
        ActivityTestHelper.waitViewVisible("$method; $step", editorView)
        EspressoUtils.waitForIdleSync()
        step = "Saving previously unsent note $unsentMsgId as a draft"
        ActivityTestHelper.hideEditorAndSaveDraft("$method; $step", activity)
        Assert.assertEquals(
            "$method; $step", DownloadStatus.DRAFT, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, unsentMsgId)
            )
        )
        MyLog.v(this, "$method ended")
    }

    @Test
    fun testGnuSocialReblog() {
        val method = "testGnuSocialReblog"
        MyLog.v(this, "$method started")
        TestSuite.waitForListLoaded(activity, 1)
        val helper = ListActivityTestHelper<TimelineActivity<*>>(activity)
        val itemId = helper.getListItemIdOfLoadedReply { item: BaseNoteViewItem<*> -> !item.visibility.isPrivate }
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, itemId)
        val noteOid = MyQuery.idToOid(activity.myContext, OidEnum.NOTE_OID, noteId, 0)
        val logMsg = MyQuery.noteInfoForLog(activity.myContext, noteId)
        Assert.assertTrue(
            logMsg, helper.invokeContextMenuAction4ListItemId(
                method, itemId, NoteContextMenuItem.ANNOUNCE,
                R.id.note_wrapper
            )
        )
        mService.serviceStopped = false
        EspressoUtils.waitForIdleSync()
        val urlFound = mService.waitForCondition {
            getHttp().getResults().map { it.url?.toExternalForm() ?: "" }
                .any { it.contains("retweet") && it.contains(noteOid) }
        }
        val results = mService.getHttp().getResults()
        Assert.assertTrue("No results in ${mService.getHttp()}\n$logMsg", results.isNotEmpty())
        Assert.assertNotNull("No URL contains note oid $logMsg\nResults: $results", urlFound)
        MyLog.v(this, "$method ended")
    }
}
