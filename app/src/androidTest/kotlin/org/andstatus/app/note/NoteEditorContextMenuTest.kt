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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.ListScreenTestHelper
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class NoteEditorContextMenuTest : TimelineActivityTest<ActivityViewItem>() {

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initialize(this)
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
         MyContextHolder.myContextHolder.getNow().accounts().setCurrentAccount(ma)
        val timeline: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.HOME, ma.actor,  Origin.EMPTY)
        MyLog.i(this, "setUp ended, $timeline")
        return Intent(Intent.ACTION_VIEW, timeline.getUri())
    }

    /* We see a crash in the test...
        java.lang.IllegalStateException: beginBroadcast() called while already in a broadcast
        at android.os.RemoteCallbackList.beginBroadcast(RemoteCallbackList.java:241)
        at com.android.server.clipboard.ClipboardService.setPrimaryClipInternal(ClipboardService.java:583)

        So we split clipboard copying functions into two tests... but this doesn't help.

        Updates:
        2021-04-25 This works. Many changes done...
        2021-05-18 Fails sometimes. Before the actual crash I see:
          E/ClipboardService: Denying clipboard access to com.android.chrome, application is not in focus nor is it a system service for user 0
        - so maybe WebView was not fully closed yet...
    */
    @Ignore
    @Test
    @Throws(InterruptedException::class)
    fun testContextMenuWhileEditing1() {
        val method = "testContextMenuWhileEditing"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.openEditor<ActivityViewItem>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>>(activity, ConversationActivity::class.java)
        val listItemId = helper.getListItemIdOfLoadedReply()
        var logMsg = "listItemId=$listItemId"
        val noteId = if (TimelineType.HOME.showsActivities()) MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId) else listItemId
        logMsg += ", noteId=$noteId"
        val content = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId)
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_TEXT, R.id.note_wrapper)
        Assert.assertEquals(logMsg, content, getClipboardText(method))
    }

    @Ignore
    @Test
    @Throws(InterruptedException::class)
    fun testContextMenuWhileEditing2() {
        val method = "testContextMenuWhileEditing"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.openEditor<ActivityViewItem>(method, activity)
        val helper = ListScreenTestHelper<TimelineActivity<*>>(activity, ConversationActivity::class.java)
        val listItemId = helper.getListItemIdOfLoadedReply()
        val logMsg = "listItemId=$listItemId"
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_AUTHOR, R.id.note_wrapper)
        val text = getClipboardText(method)
        MatcherAssert.assertThat(text, CoreMatchers.startsWith("@"))
        Assert.assertTrue("$logMsg; Text: '$text'", text.startsWith("@") && text.lastIndexOf("@") > 1)
    }

    private fun getClipboardText(methodExt: String): String {
        val method = "getClipboardText"
        return try {
            MyLog.v(methodExt, "$method started")
            TestSuite.waitForIdleSync()
            val reader = ClipboardReader()
            getInstrumentation().runOnMainSync(reader)
            MyLog.v(methodExt, method + "; clip='" + reader.clip + "'")
            val clip = reader.clip ?: return ""

            val item = clip.getItemAt(0)
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
            val clipboard =  MyContextHolder.myContextHolder.getNow().context
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip = clipboard.primaryClip
        }
    }
}
