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
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ReplaceTextAction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.HelpActivity
import org.andstatus.app.R
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
import org.andstatus.app.net.social.Audience.Companion.fromNoteId
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.ListActivityTestHelper
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyHtmlTest
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.ScreenshotOnFailure
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
class NoteEditorTest : TimelineActivityTest<ActivityViewItem>() {
    private var data: NoteEditorData by Delegates.notNull()

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        if (editingStep.get() != 1) {
            MyPreferences.setBeingEditedNoteId(0)
        }
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        MyContextHolder.myContextHolder.getNow().accounts.setCurrentAccount(ma)
        data = getStaticData(ma)
        val timeline: Timeline =
            MyContextHolder.myContextHolder.getNow().timelines.get(TimelineType.HOME, ma.actor, Origin.EMPTY)
        MyLog.i(this, "setUp ended, $timeline")
        return Intent(Intent.ACTION_VIEW, timeline.getUri())
    }

    private fun getStaticData(ma: MyAccount): NoteEditorData {
        return NoteEditorData.Companion.newReplyTo(
            MyQuery.oidToId(
                OidEnum.NOTE_OID, ma.origin.id,
                DemoData.demoData.conversationEntryNoteOid
            ), ma
        )
            .addToAudience(
                MyQuery.oidToId(
                    OidEnum.ACTOR_OID, ma.origin.id,
                    DemoData.demoData.conversationEntryAuthorOid
                )
            )
            .addMentionsToText()
            .setContent(MyHtmlTest.twitterBodyTypedPlain + " " + DemoData.demoData.testRunUid, TextMediaType.PLAIN)
    }

    @Test
    fun testEditing1() {
        Assert.assertTrue("MyService is available", MyServiceManager.Companion.isServiceAvailable())
        editingTester()
    }

    @Test
    fun testEditing2() {
        editingTester()
    }

    private fun editingTester() {
        TestSuite.waitForListLoaded(activity, 2)
        when (editingStep.incrementAndGet()) {
            2 -> editingStep2()
            else -> {
                editingStep.set(1)
                ActivityTestHelper.openEditor<ActivityViewItem>("default", activity)
                editingStep1()
            }
        }
        MyLog.v(this, "After step " + editingStep + " ended")
    }

    private fun editingStep1() {
        val method = "editingStep1"
        MyLog.v(this, "$method started")
        val editorView: View = ActivityTestHelper.hideEditorAndSaveDraft<ActivityViewItem>(method, activity)
        val editor = activity.getNoteEditor() ?: throw IllegalStateException("No editor")
        getInstrumentation().runOnMainSync { editor.startEditingNote(data) }
        EspressoUtils.waitForIdleSync()
        ActivityTestHelper.waitViewVisible(method, editorView)
        assertInitialText("Initial text")
        MyLog.v(this, "$method ended")
    }

    private fun editingStep2() {
        val method = "editingStep2"
        MyLog.v(this, "$method started")
        val helper = ActivityTestHelper<TimelineActivity<*>>(activity)
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.waitViewVisible("$method; Restored note is visible", editorView)
        assertInitialText("Note restored")
        ActivityTestHelper.hideEditorAndSaveDraft<ActivityViewItem>(method, activity)
        ActivityTestHelper.openEditor<ActivityViewItem>(method, activity)
        assertTextCleared(this)
        helper.clickMenuItem("$method click Discard", R.id.discardButton)
        ActivityTestHelper.waitViewInvisible("$method; Editor hidden after discard", editorView)
        MyLog.v(this, "$method ended")
    }

    @Test
    fun attachOneImage() {
        attachImages(this, 1, 1)
    }

    @Test
    fun attachTwoImages() {
        attachImages(this, 2, 1)
    }

    private fun assertInitialText(description: String) {
        val editor = activity.getNoteEditor() ?: throw IllegalStateException("No editor")
        val textView = activity.findViewById<TextView?>(R.id.noteBodyEditText)
        ActivityTestHelper.waitTextInAView(
            description, textView,
            MyHtml.fromContentStored(data.getContent(), TextMediaType.PLAIN)
        )
        Assert.assertEquals(description, data.toTestSummary(), editor.getData().toTestSummary())
    }

    @Test
    fun editLoadedNote() {
        val method = "editLoadedNote"
        TestSuite.waitForListLoaded(activity, 2)
        val helper = ListActivityTestHelper<TimelineActivity<*>>(
            activity,
            ConversationActivity::class.java
        )
        val listItemId = helper.findListItemId(
            "My loaded note, actorId:" + data.getMyAccount().actorId
        ) { item: BaseNoteViewItem<*> ->
            (item.author.getActorId() == data.getMyAccount().actorId
                && item.noteStatus == DownloadStatus.LOADED)
        }
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        var logMsg = ("itemId=" + listItemId + ", noteId=" + noteId + " text='"
            + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'")
        val invoked = helper.invokeContextMenuAction4ListItemId(
            method, listItemId,
            NoteContextMenuItem.EDIT, R.id.note_wrapper
        )
        logMsg += ";" + if (invoked) "" else " failed to invoke Edit menu item,"
        Assert.assertTrue(logMsg, invoked)
        ActivityTestHelper.closeContextMenu(activity)
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.waitViewVisible("$method $logMsg", editorView)
        Assert.assertEquals(
            "Loaded note should be in DRAFT state on Edit start: $logMsg", DownloadStatus.DRAFT,
            getDownloadStatus(noteId)
        )
        val helper2 = ActivityTestHelper<TimelineActivity<*>>(activity)
        helper2.clickMenuItem("$method clicker Discard $logMsg", R.id.discardButton)
        ActivityTestHelper.waitViewInvisible("$method $logMsg", editorView)
        Assert.assertEquals(
            "Loaded note should be unchanged after Discard: $logMsg", DownloadStatus.LOADED,
            waitForDownloadStatus(noteId, DownloadStatus.LOADED)
        )
    }

    private fun waitForDownloadStatus(noteId: Long, expected: DownloadStatus): DownloadStatus {
        var downloadStatus: DownloadStatus = DownloadStatus.UNKNOWN
        for (i in 0..29) {
            downloadStatus = getDownloadStatus(noteId)
            if (downloadStatus == expected) return downloadStatus
            DbUtils.waitMs(this, 100)
        }
        return downloadStatus
    }

    private fun getDownloadStatus(noteId: Long): DownloadStatus {
        return DownloadStatus.Companion.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId))
    }

    @Test
    fun replying() {
        val method = "replying"
        TestSuite.waitForListLoaded(activity, 2)
        val editorView: View = ActivityTestHelper.hideEditorAndSaveDraft<ActivityViewItem>(method, activity)
        val helper = ListActivityTestHelper<TimelineActivity<*>>(
            activity,
            ConversationActivity::class.java
        )
        val viewItem = helper.findListItem(
            "Some others loaded note"
        ) { item: BaseNoteViewItem<*> ->
            (item.author.getActorId() != data.getMyAccount().actorId
                && item.noteStatus == DownloadStatus.LOADED)
        } as ActivityViewItem
        val listItemId = viewItem.getId()
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId)
        var logMsg = ("itemId=" + listItemId + ", noteId=" + noteId + " text='"
            + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'")
        Assert.assertEquals(logMsg, viewItem.noteViewItem.getId(), noteId)
        val invoked = helper.invokeContextMenuAction4ListItemId(
            method, listItemId,
            NoteContextMenuItem.REPLY, R.id.note_wrapper
        )
        logMsg += ";" + if (invoked) "" else " failed to invoke Reply menu item,"
        Assert.assertTrue(logMsg, invoked)
        ActivityTestHelper.closeContextMenu(activity)
        ActivityTestHelper.waitViewVisible("$method $logMsg", editorView)
        onView(ViewMatchers.withId(R.id.noteBodyEditText))
            .check(ViewAssertions.matches(ViewMatchers.withText(CoreMatchers.startsWith("@"))))
        EspressoUtils.waitForIdleSync()
        val content = "Replying to you during " + DemoData.demoData.testRunUid
        val bodyText = editorView.findViewById<EditText?>(R.id.noteBodyEditText)
        // Espresso types in the centre, unfortunately, so we need to retype text
        onView(ViewMatchers.withId(R.id.noteBodyEditText))
            .perform(ReplaceTextAction(bodyText.text.toString().trim { it <= ' ' }
                + " " + content))
        EspressoUtils.waitForIdleSync()
        ActivityTestHelper.hideEditorAndSaveDraft<ActivityViewItem>("$method Save draft $logMsg", activity)
        val draftNoteId: Long = ActivityTestHelper.waitAndGetIdOfStoredNote("$method $logMsg", content)
        Assert.assertEquals(
            "Saved note should be in DRAFT state: $logMsg", DownloadStatus.DRAFT,
            getDownloadStatus(draftNoteId)
        )
        Assert.assertEquals(
            "Wrong id of inReplyTo note of '$content': $logMsg", noteId,
            MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, draftNoteId)
        )
        val audience: Audience = fromNoteId(data.getMyAccount().origin, draftNoteId)
        Assert.assertTrue(
            "Audience of a reply to $viewItem\n $audience",
            audience.findSame(viewItem.noteViewItem.author.actor).isSuccess
        )
    }

    companion object {
        private val editingStep: AtomicInteger = AtomicInteger()

        fun attachImages(test: TimelineActivityTest<ActivityViewItem>, toAdd: Int, toExpect: Int) {
            ScreenshotOnFailure.screenshotWrapper(test.activity) {
                attachImagesInternal(test, toAdd, toExpect)
            }
        }

        private fun attachImagesInternal(test: TimelineActivityTest<ActivityViewItem>, toAdd: Int, toExpect: Int) {
            val method = "attachImages$toAdd"
            MyLog.v(test, "$method started")
            ActivityTestHelper.hideEditorAndSaveDraft(method, test.activity)
            val editorView: View = ActivityTestHelper.openEditor(method, test.activity)
            assertTextCleared(test)
            EspressoUtils.waitForIdleSync()
            val noteName = "A note " + toAdd + " " + test::class.simpleName + " can have a title (name)"
            val content = "Note with " + toExpect + " attachment" +
                (if (toExpect == 1) "" else "s") + " " +
                DemoData.demoData.testRunUid
            onView(ViewMatchers.withId(R.id.note_name_edit)).perform(ReplaceTextAction(noteName))
            onView(ViewMatchers.withId(R.id.noteBodyEditText)).perform(ReplaceTextAction(content))
            onView(ViewMatchers.withId(R.id.note_name_edit)).check(ViewAssertions.matches(ViewMatchers.withText(noteName)))
            onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(
                ViewAssertions.matches(
                    ViewMatchers.withText(
                        content
                    )
                )
            )
            attachImage(test, editorView, DemoData.demoData.localImageTestUri2)
            if (toAdd > 1) {
                attachImage(test, editorView, DemoData.demoData.localGifTestUri)
            }
            val editor = test.activity.getNoteEditor() ?: throw IllegalStateException("No editor")
            Assert.assertEquals(
                "All image attached " + editor.getData().getAttachedImageFiles(), toExpect.toLong(),
                editor.getData().getAttachedImageFiles().list.size.toLong()
            )
            if (toAdd > 1) {
                val mediaFile = editor.getData().getAttachedImageFiles().list[if (toExpect == 1) 0 else 1]
                Assert.assertEquals(
                    "Should be animated $mediaFile",
                    MyContentType.ANIMATED_IMAGE,
                    mediaFile.contentType
                )
            }
            onView(ViewMatchers.withId(R.id.noteBodyEditText)).check(ViewAssertions.matches(ViewMatchers.withText("$content ")))
            onView(ViewMatchers.withId(R.id.note_name_edit)).check(ViewAssertions.matches(ViewMatchers.withText(noteName)))
            ActivityTestHelper.hideEditorAndSaveDraft(method, test.activity)
            EspressoUtils.waitForIdleSync()
            MyLog.v(test, "$method ended")
        }

        private fun attachImage(test: TimelineActivityTest<ActivityViewItem>, editorView: View, imageUri: Uri) {
            val method = "attachImage"
            EspressoUtils.waitForIdleSync()
            val helper = ActivityTestHelper<TimelineActivity<*>>(test.activity)
            test.activity.setSelectorActivityStub(helper)
            helper.clickMenuItem("$method clicker attach_menu_id", R.id.attach_menu_id)
            Assert.assertNotNull(helper.waitForSelectorStart(method, ActivityRequestCode.ATTACH.id))
            test.activity.setSelectorActivityStub(null)
            val activityMonitor = test.getInstrumentation()
                .addMonitor(HelpActivity::class.java.name, null, false)
            val intent1 = Intent(test.activity, HelpActivity::class.java)
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            test.activity.applicationContext.startActivity(intent1)
            val selectorActivity = test.getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 25000)
            Assert.assertTrue(selectorActivity != null)
            ActivityTestHelper.waitViewInvisible(method, editorView)
            EspressoUtils.waitForIdleSync()
            selectorActivity.finish()
            EspressoUtils.waitForIdleSync()
            MyLog.i(method, "Callback from a selector")
            val intent2 = Intent()
            intent2.setDataAndType(
                imageUri, MyContentType.Companion.uri2MimeType(
                    test.activity.contentResolver, imageUri,
                    MyContentType.IMAGE.generalMimeType
                )
            )
            test.activity.runOnUiThread {
                test.activity.onActivityResult(
                    ActivityRequestCode.ATTACH.id,
                    Activity.RESULT_OK,
                    intent2
                )
            }
            val editor = test.activity.getNoteEditor() ?: throw IllegalStateException("No editor")
            for (attempt in 0..3) {
                ActivityTestHelper.waitViewVisible(method, editorView)
                // Due to a race the editor may open before this change first.
                if (editor.getData().getAttachedImageFiles().forUri(imageUri).isPresent) {
                    break
                }
            }
            Assert.assertTrue(
                "Image attached", editor.getData().getAttachedImageFiles()
                    .forUri(imageUri).isPresent
            )
        }

        private fun assertTextCleared(test: TimelineActivityTest<ActivityViewItem>) {
            val editor = test.activity.getNoteEditor()
            Assert.assertTrue("Editor is not null", editor != null)
            Assert.assertEquals(
                NoteEditorData.Companion.newEmpty(
                    test.activity.myContext.accounts.currentAccount
                ).toTestSummary(),
                editor?.getData()?.toTestSummary()
            )
        }
    }
}
