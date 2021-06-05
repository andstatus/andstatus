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

import android.content.Intent
import android.view.View
import android.widget.TextView
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyHtmlTest
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
class NoteEditorTwitterTest : TimelineActivityTest<ActivityViewItem>() {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var data: NoteEditorData by Delegates.notNull()

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        if (editingStep.get() != 1) {
            MyPreferences.setBeingEditedNoteId(0)
        }
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName)
        Assert.assertTrue(ma.isValid)
        Assert.assertEquals("Account should be in Twitter: $ma", OriginType.TWITTER, ma.origin.originType)
         myContext.accounts.setCurrentAccount(ma)
        data = getStaticData(ma)
        MyLog.i(this, "setUp ended")
        return Intent(Intent.ACTION_VIEW,
                 myContext.timelines.get(TimelineType.HOME, ma.actor,  Origin.EMPTY).getUri())
    }

    private fun getStaticData(ma: MyAccount): NoteEditorData {
        return NoteEditorData.Companion.newEmpty(ma)
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
                ActivityTestHelper.openEditor("default", activity)
                editingStep1()
            }
        }
        MyLog.v(this, "After step $editingStep ended")
    }

    private fun editingStep1() {
        val method = "editingStep1"
        MyLog.v(this, "$method started")
        val editorView: View = ActivityTestHelper.hideEditorAndSaveDraft(method, activity)
        val editor = activity.getNoteEditor() ?: throw IllegalStateException("No editor")
        getInstrumentation().runOnMainSync { editor.startEditingNote(data) }
        ActivityTestHelper.waitViewVisible(method, editorView)
        assertInitialText("Initial text")
        MyLog.v(this, "$method ended")
    }

    private fun editingStep2() {
        val method = "editingStep2"
        MyLog.v(this, "$method started")
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.waitViewVisible("$method; Restored note is visible", editorView)
        assertInitialText("Note restored")
        ActivityTestHelper.hideEditorAndSaveDraft(method, activity)
        ActivityTestHelper.openEditor(method, activity)
        assertTextCleared()
        val helper = ActivityTestHelper<TimelineActivity<*>>(activity)
        helper.clickMenuItem("$method click Discard", R.id.discardButton)
        ActivityTestHelper.waitViewInvisible("$method; Editor hidden after discard", editorView)
        MyLog.v(this, "$method ended")
    }

    private fun assertInitialText(description: String) {
        val editor = activity.getNoteEditor() ?: throw IllegalStateException("No editor")
        val textView = activity.findViewById<TextView?>(R.id.noteBodyEditText)
        ActivityTestHelper.waitTextInAView(description, textView,
                MyHtml.fromContentStored(data.getContent(), TextMediaType.PLAIN))
        Assert.assertEquals(description, data.toTestSummary(), editor.getData().toTestSummary())
    }

    private fun assertTextCleared() {
        val editor = activity.getNoteEditor() ?: throw IllegalStateException("No editor")
        Assert.assertEquals(NoteEditorData.Companion.newEmpty(
                activity.myContext.accounts.currentAccount).toTestSummary(),
                editor.getData().toTestSummary())
    }

    companion object {
        private val editingStep: AtomicInteger = AtomicInteger()
    }
}
