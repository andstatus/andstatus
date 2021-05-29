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
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.social.Actor
import org.andstatus.app.service.MyServiceTestHelper
import org.andstatus.app.timeline.ListScreenTestHelper
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.junit.After
import org.junit.Assert
import org.junit.Test

class UnsentNotesTest : TimelineActivityTest<ActivityViewItem>() {
    private val mService: MyServiceTestHelper = MyServiceTestHelper()

    override fun getActivityIntent(): Intent {
        TestSuite.initializeWithAccounts(this)
        mService.setUp(null)
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        Assert.assertTrue(ma.isValid)
         MyContextHolder.myContextHolder.getNow().accounts().setCurrentAccount(ma)
        return Intent(Intent.ACTION_VIEW,
                 MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.EVERYTHING, Actor.EMPTY, ma.origin).getUri())
    }

    @After
    fun tearDown() {
        mService.tearDown()
    }

    @Test
    @Throws(InterruptedException::class)
    fun testEditUnsentNote() {
        val method = "testEditUnsentNote"
        var step = "Start editing a note"
        MyLog.v(this, "$method started")
        val editorView: View = ActivityTestHelper.openEditor<ActivityViewItem>("$method; $step", activity)
        val suffix = "unsent" + DemoData.demoData.testRunUid
        val body = "Test unsent note, which we will try to edit $suffix"
        TestSuite.waitForIdleSync()
        Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(body))
        TestSuite.waitForIdleSync()
        mService.serviceStopped = false
        ActivityTestHelper.clickSendButton<ActivityViewItem>(method, activity)
        mService.waitForServiceStopped(false)
        val condition = NoteTable.CONTENT + " LIKE('%" + suffix + "%')"
        val unsentMsgId = MyQuery.conditionToLongColumnValue(NoteTable.TABLE_NAME, BaseColumns._ID, condition)
        step = "Unsent note $unsentMsgId"
        Assert.assertTrue("$method; $step: $condition", unsentMsgId != 0L)
        Assert.assertEquals("$method; $step", DownloadStatus.SENDING, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, unsentMsgId)))
        step = "Start editing unsent note $unsentMsgId"
        activity.getNoteEditor()?.startEditingNote(NoteEditorData.Companion.load( MyContextHolder.myContextHolder.getNow(), unsentMsgId))
        ActivityTestHelper.waitViewVisible("$method; $step", editorView)
        TestSuite.waitForIdleSync()
        step = "Saving previously unsent note $unsentMsgId as a draft"
        ActivityTestHelper.hideEditorAndSaveDraft<ActivityViewItem>("$method; $step", activity)
        Assert.assertEquals("$method; $step", DownloadStatus.DRAFT, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, unsentMsgId)))
        MyLog.v(this, "$method ended")
    }

    @Test
    @Throws(InterruptedException::class)
    fun testGnuSocialReblog() {
        val method = "testGnuSocialReblog"
        MyLog.v(this, "$method started")
        TestSuite.waitForListLoaded(activity, 1)
        val helper = ListScreenTestHelper<TimelineActivity<*>>(activity)
        val itemId = helper.getListItemIdOfLoadedReply { item: BaseNoteViewItem<*> -> !item.visibility.isPrivate }
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, itemId)
        val noteOid = MyQuery.idToOid(activity.myContext, OidEnum.NOTE_OID, noteId, 0)
        val logMsg = MyQuery.noteInfoForLog(activity.myContext, noteId)
        Assert.assertTrue(logMsg, helper.invokeContextMenuAction4ListItemId(method, itemId, NoteContextMenuItem.ANNOUNCE,
                R.id.note_wrapper))
        mService.serviceStopped = false
        TestSuite.waitForIdleSync()
        mService.waitForServiceStopped(false)
        val results = mService.getHttp()?.getResults() ?: emptyList()
        Assert.assertTrue("No results in ${mService.getHttp()}\n$logMsg", !results.isEmpty())
        val urlFound = results
            .map { it.url?.toExternalForm() ?: "" }
            .find { it.contains("retweet") && it.contains(noteOid) }
        Assert.assertNotNull("No URL contains note oid $logMsg\nResults: $results", urlFound)
        MyLog.v(this, "$method ended")
    }
}
