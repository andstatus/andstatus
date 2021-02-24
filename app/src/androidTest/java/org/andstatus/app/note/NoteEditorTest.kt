/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.note

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ReplaceTextAction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.HelpActivity
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyContentType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Audience
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.ListScreenTestHelper
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyHtmlTest
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.ScreenshotOnFailure
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

import kotlin.jvm.Volatile
import kotlin.Throws

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
class NoteEditorTest : TimelineActivityTest<ActivityViewItem?>() {
    private var data: NoteEditorData? = null
    override fun getActivityIntent(): Intent? {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        if (NoteEditorTest.Companion.editingStep.get() != 1) {
            MyPreferences.setBeingEditedNoteId(0)
        }
        val ma: MyAccount = DemoData.Companion.demoData.getMyAccount(DemoData.Companion.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
         MyContextHolder.myContextHolder.getNow().accounts().setCurrentAccount(ma)
        data = getStaticData(ma)
        val timeline: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.HOME, ma.actor,  Origin.EMPTY)
        MyLog.i(this, "setUp ended, $timeline")
        return Intent(Intent.ACTION_VIEW, timeline.uri)
    }

    private fun getStaticData(ma: MyAccount?): NoteEditorData? {
        return NoteEditorData.Companion.newReplyTo(MyQuery.oidToId(OidEnum.NOTE_OID, ma.getOrigin().id,
                DemoData.Companion.demoData.conversationEntryNoteOid), ma)
                .addToAudience(MyQuery.oidToId(OidEnum.ACTOR_OID, ma.getOrigin().id,
                        DemoData.Companion.demoData.conversationEntryAuthorOid))
                .addMentionsToText()
                .setContent(MyHtmlTest.Companion.twitterBodyTypedPlain + " " + DemoData.Companion.demoData.testRunUid, TextMediaType.PLAIN)
    }

    @Test
    @Throws(InterruptedException::class)
    fun testEditing1() {
        Assert.assertTrue("MyService is available", MyServiceManager.Companion.isServiceAvailable())
        editingTester()
    }

    @Test
    @Throws(InterruptedException::class)
    fun testEditing2() {
        editingTester()
    }

    @Throws(InterruptedException::class)
    private fun editingTester() {
        TestSuite.waitForListLoaded(activity, 2)
        when (NoteEditorTest.Companion.editingStep.incrementAndGet()) {
            2 -> editingStep2()
            else -> {
                NoteEditorTest.Companion.editingStep.set(1)
                ActivityTestHelper.Companion.openEditor<ActivityViewItem?>("default", activity)
                editingStep1()
            }
        }
        MyLog.v(this, "After step " + NoteEditorTest.Companion.editingStep + " ended")
    }

    @Throws(InterruptedException::class)
    private fun editingStep1() {
        val method = "editingStep1"
        MyLog.v(this, "$method started")
        val editorView: View = ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        val editor = activity.noteEditor
        instrumentation.runOnMainSync { editor.startEditingNote(data) }
        TestSuite.waitForIdleSync()
        ActivityTestHelper.Companion.waitViewVisible(method, editorView)
        assertInitialText("Initial text")
        MyLog.v(this, "$method ended")
    }

    @Throws(InterruptedException::class)
    private fun editingStep2() {
        val method = "editingStep2"
        MyLog.v(this, "$method started")
        val helper = ActivityTestHelper<TimelineActivity<*>?>(activity)
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.Companion.waitViewVisible("$method; Restored note is visible", editorView)
        assertInitialText("Note restored")
        ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, activity)
        NoteEditorTest.Companion.assertTextCleared(this)
        helper.clickMenuItem("$method click Discard", R.id.discardButton)
        ActivityTestHelper.Companion.waitViewInvisible("$method; Editor hidden after discard", editorView)
        MyLog.v(this, "$method ended")
    }

    @Test
    @Throws(InterruptedException::class)
    fun attachOneImage() {
        NoteEditorTest.Companion.attachImages(this, 1, 1)
    }

    @Test
    @Throws(InterruptedException::class)
    fun attachTwoImages() {
        NoteEditorTest.Companion.attachImages(this, 2, 1)
    }

    @Throws(InterruptedException::class)
    private fun assertInitialText(description: String?) {
        val editor = activity.noteEditor
        val textView = activity.findViewById<TextView?>(R.id.noteBodyEditText)
        ActivityTestHelper.Companion.waitTextInAView(description, textView,
                MyHtml.fromContentStored(data.getContent(), TextMediaType.PLAIN))
        Assert.assertEquals(description, data.toTestSummary(), editor.data.toTestSummary())
    }

    /* We see crash in the test...
        java.lang.IllegalStateException: beginBroadcast() called while already in a broadcast
        at android.os.RemoteCallbackList.beginBroadcast(RemoteCallbackList.java:241)
        at com.android.server.clipboard.ClipboardService.setPrimaryClipInternal(ClipboardService.java:583)

        So we split clipboard copying functions into two tests
        ...but this doesn't help...
    */
    @Ignore("We see crash in the test...")
    @Test
    @Throws(InterruptedException::class)
    fun testContextMenuWhileEditing1() {
        val method = "testContextMenuWhileEditing"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity, ConversationActivity::class.java)
        val listItemId = helper.listItemIdOfLoadedReply
        var logMsg = "listItemId=$listItemId"
        val noteId = if (TimelineType.HOME.showsActivities()) MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId) else listItemId
        logMsg += ", noteId=$noteId"
        val content = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId)
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_TEXT, R.id.note_wrapper)
        Assert.assertEquals(logMsg, content, getClipboardText(method))
    }

    @Ignore("We see crash in the test...")
    @Test
    @Throws(InterruptedException::class)
    fun testContextMenuWhileEditing2() {
        val method = "testContextMenuWhileEditing"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity, ConversationActivity::class.java)
        val listItemId = helper.listItemIdOfLoadedReply
        val logMsg = "listItemId=$listItemId"
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_AUTHOR, R.id.note_wrapper)
        val text = getClipboardText(method)
        MatcherAssert.assertThat(text, CoreMatchers.startsWith("@"))
        Assert.assertTrue("$logMsg; Text: '$text'", text.startsWith("@") && text.lastIndexOf("@") > 1)
    }

    private fun getClipboardText(methodExt: String?): String? {
        val method = "getClipboardText"
        return try {
            MyLog.v(methodExt, "$method started")
            TestSuite.waitForIdleSync()
            val reader = ClipboardReader()
            instrumentation.runOnMainSync(reader)
            MyLog.v(methodExt, method + "; clip='" + reader.clip + "'")
            if (reader.clip == null) {
                return ""
            }
            val item = reader.clip.getItemAt(0)
            val text = (if (item.htmlText.isNullOrEmpty()) item.text else item.htmlText)
                    .toString()
            MyLog.v(methodExt, "$method ended. Text: $text")
            text
        } catch (e: Exception) {
            MyLog.e(method, e)
            "Exception: " + e.message
        }
    }

    private class ClipboardReader : Runnable {
        @Volatile
        var clip: ClipData? = null
        override fun run() {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            val clipboard =  MyContextHolder.myContextHolder.getNow().context()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip = clipboard.primaryClip
        }
    }

    @Test
    @Throws(InterruptedException::class)
    fun editLoadedNote() {
        val method = "editLoadedNote"
        TestSuite.waitForListLoaded(activity, 2)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity,
                ConversationActivity::class.java)
        val listItemId = helper.findListItemId("My loaded note, actorId:" + data.getMyAccount().actorId
        ) { item: BaseNoteViewItem<*>? ->
            (item.author.actorId == data.getMyAccount().actorId
                    && item.noteStatus == DownloadStatus.LOADED)
        }
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        var logMsg = ("itemId=" + listItemId + ", noteId=" + noteId + " text='"
                + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'")
        val invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.EDIT, R.id.note_wrapper)
        logMsg += ";" + if (invoked) "" else " failed to invoke Edit menu item,"
        Assert.assertTrue(logMsg, invoked)
        ActivityTestHelper.Companion.closeContextMenu(activity)
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.Companion.waitViewVisible("$method $logMsg", editorView)
        Assert.assertEquals("Loaded note should be in DRAFT state on Edit start: $logMsg", DownloadStatus.DRAFT,
                getDownloadStatus(noteId))
        val helper2 = ActivityTestHelper<TimelineActivity<*>?>(activity)
        helper2.clickMenuItem("$method clicker Discard $logMsg", R.id.discardButton)
        ActivityTestHelper.Companion.waitViewInvisible("$method $logMsg", editorView)
        Assert.assertEquals("Loaded note should be unchanged after Discard: $logMsg", DownloadStatus.LOADED,
                waitForDownloadStatus(noteId, DownloadStatus.LOADED))
    }

    private fun waitForDownloadStatus(noteId: Long, expected: DownloadStatus?): DownloadStatus? {
        var downloadStatus: DownloadStatus? = DownloadStatus.UNKNOWN
        for (i in 0..29) {
            downloadStatus = getDownloadStatus(noteId)
            if (downloadStatus == expected) return downloadStatus
            DbUtils.waitMs(this, 100)
        }
        return downloadStatus
    }

    private fun getDownloadStatus(noteId: Long): DownloadStatus? {
        return DownloadStatus.Companion.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId))
    }

    @Test
    @Throws(InterruptedException::class)
    fun replying() {
        val method = "replying"
        TestSuite.waitForListLoaded(activity, 2)
        val editorView: View = ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>?>(activity,
                ConversationActivity::class.java)
        val viewItem = helper.findListItem("Some others loaded note"
        ) { item: BaseNoteViewItem<*>? ->
            (item.author.actorId != data.getMyAccount().actorId
                    && item.noteStatus == DownloadStatus.LOADED)
        } as ActivityViewItem
        val listItemId = viewItem.id
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        var logMsg = ("itemId=" + listItemId + ", noteId=" + noteId + " text='"
                + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'")
        Assert.assertEquals(logMsg, viewItem.noteViewItem.id, noteId)
        val invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.REPLY, R.id.note_wrapper)
        logMsg += ";" + if (invoked) "" else " failed to invoke Reply menu item,"
        Assert.assertTrue(logMsg, invoked)
        ActivityTestHelper.Companion.closeContextMenu(activity)
        ActivityTestHelper.Companion.waitViewVisible("$method $logMsg", editorView)
        Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(ViewAssertions.matches(ViewMatchers.withText(CoreMatchers.startsWith("@"))))
        TestSuite.waitForIdleSync()
        val content = "Replying to you during " + DemoData.Companion.demoData.testRunUid
        val bodyText = editorView.findViewById<EditText?>(R.id.noteBodyEditText)
        // Espresso types in the centre, unfortunately, so we need to retype text
        Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(bodyText.text.toString().trim { it <= ' ' }
                + " " + content))
        TestSuite.waitForIdleSync()
        ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>("$method Save draft $logMsg", activity)
        val draftNoteId: Long = ActivityTestHelper.Companion.waitAndGetIdOfStoredNote("$method $logMsg", content)
        Assert.assertEquals("Saved note should be in DRAFT state: $logMsg", DownloadStatus.DRAFT,
                getDownloadStatus(draftNoteId))
        Assert.assertEquals("Wrong id of inReplyTo note of '$content': $logMsg", noteId,
                MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, draftNoteId))
        val audience: Audience = fromNoteId(data.getMyAccount().origin, draftNoteId)
        Assert.assertTrue("Audience of a reply to $viewItem\n $audience",
                audience.findSame(viewItem.noteViewItem.author.actor).isSuccess)
    }

    companion object {
        private val editingStep: AtomicInteger? = AtomicInteger()
        fun attachImages(test: TimelineActivityTest<ActivityViewItem?>?, toAdd: Int, toExpect: Int) {
            ScreenshotOnFailure.screenshotWrapper(test.getActivity()) { NoteEditorTest.Companion._attachImages(test, toAdd, toExpect) }
        }

        @Throws(InterruptedException::class)
        private fun _attachImages(test: TimelineActivityTest<ActivityViewItem?>?, toAdd: Int, toExpect: Int) {
            val method = "attachImages$toAdd"
            MyLog.v(test, "$method started")
            ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, test.getActivity())
            val editorView: View = ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, test.getActivity())
            NoteEditorTest.Companion.assertTextCleared(test)
            TestSuite.waitForIdleSync()
            val noteName = "A note " + toAdd + " " + test.javaClass.simpleName + " can have a title (name)"
            val content = "Note with " + toExpect + " attachment" +
                    (if (toExpect == 1) "" else "s") + " " +
                    DemoData.Companion.demoData.testRunUid
            Espresso.onView(ViewMatchers.withId(R.id.note_name_edit)).perform(ReplaceTextAction(noteName))
            Espresso.onView(ViewMatchers.withId(R.id.note_name_edit)).check(ViewAssertions.matches(ViewMatchers.withText(noteName)))
            Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(content))
            Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(ViewAssertions.matches(ViewMatchers.withText(content)))
            NoteEditorTest.Companion.attachImage(test, editorView, DemoData.Companion.demoData.localImageTestUri2)
            if (toAdd > 1) {
                NoteEditorTest.Companion.attachImage(test, editorView, DemoData.Companion.demoData.localGifTestUri)
            }
            val editor = test.getActivity().noteEditor
            Assert.assertEquals("All image attached " + editor.data.attachedImageFiles, toExpect.toLong(),
                    editor.data.attachedImageFiles.list.size.toLong())
            if (toAdd > 1) {
                val mediaFile = editor.data.attachedImageFiles.list[if (toExpect == 1) 0 else 1]
                Assert.assertEquals("Should be animated $mediaFile", MyContentType.ANIMATED_IMAGE, mediaFile.contentType)
            }
            Espresso.onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(ViewAssertions.matches(ViewMatchers.withText("$content ")))
            Espresso.onView(ViewMatchers.withId(R.id.note_name_edit)).check(ViewAssertions.matches(ViewMatchers.withText(noteName)))
            ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, test.getActivity())
            MyLog.v(test, "$method ended")
        }

        @Throws(InterruptedException::class)
        private fun attachImage(test: TimelineActivityTest<ActivityViewItem?>?, editorView: View?, imageUri: Uri?) {
            val method = "attachImage"
            TestSuite.waitForIdleSync()
            val helper = ActivityTestHelper<TimelineActivity<*>?>(test.getActivity())
            test.getActivity().setSelectorActivityMock(helper)
            helper.clickMenuItem("$method clicker attach_menu_id", R.id.attach_menu_id)
            Assert.assertNotNull(helper.waitForSelectorStart(method, ActivityRequestCode.ATTACH.id))
            test.getActivity().setSelectorActivityMock(null)
            val activityMonitor = test.getInstrumentation()
                    .addMonitor(HelpActivity::class.java.name, null, false)
            val intent1 = Intent(test.getActivity(), HelpActivity::class.java)
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            test.getActivity().applicationContext.startActivity(intent1)
            val selectorActivity = test.getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 25000)
            Assert.assertTrue(selectorActivity != null)
            ActivityTestHelper.Companion.waitViewInvisible(method, editorView)
            DbUtils.waitMs(method, 10000)
            selectorActivity.finish()
            MyLog.i(method, "Callback from a selector")
            val intent2 = Intent()
            intent2.setDataAndType(imageUri, MyContentType.Companion.uri2MimeType(test.getActivity().contentResolver, imageUri,
                    MyContentType.IMAGE.generalMimeType))
            test.getActivity().runOnUiThread { test.getActivity().onActivityResult(ActivityRequestCode.ATTACH.id, Activity.RESULT_OK, intent2) }
            val editor = test.getActivity().noteEditor
            for (attempt in 0..3) {
                ActivityTestHelper.Companion.waitViewVisible(method, editorView)
                // Due to a race the editor may open before this change first.
                if (editor.data.attachedImageFiles.forUri(imageUri).isPresent) {
                    break
                }
                if (DbUtils.waitMs(method, 2000)) {
                    break
                }
            }
            Assert.assertTrue("Image attached", editor.data.attachedImageFiles
                    .forUri(imageUri).isPresent)
        }

        private fun assertTextCleared(test: TimelineActivityTest<ActivityViewItem?>?) {
            val editor = test.getActivity().noteEditor
            Assert.assertTrue("Editor is not null", editor != null)
            Assert.assertEquals(NoteEditorData.Companion.newEmpty(
                    test.getActivity().myContext.accounts().currentAccount).toTestSummary(),
                    editor.data.toTestSummary())
        }
    }
}