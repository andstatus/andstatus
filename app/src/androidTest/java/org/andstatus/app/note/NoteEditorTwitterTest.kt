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
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
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

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
class NoteEditorTwitterTest : TimelineActivityTest<ActivityViewItem?>() {
    private var data: NoteEditorData? = null
    override fun getActivityIntent(): Intent? {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithAccounts(this)
        if (NoteEditorTwitterTest.Companion.editingStep.get() != 1) {
            MyPreferences.setBeingEditedNoteId(0)
        }
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName)
        Assert.assertTrue(ma.isValid)
        Assert.assertEquals("Account should be in Twitter: $ma", OriginType.TWITTER, ma.origin.originType)
         MyContextHolder.myContextHolder.getNow().accounts().setCurrentAccount(ma)
        data = getStaticData(ma)
        MyLog.i(this, "setUp ended")
        return Intent(Intent.ACTION_VIEW,
                 MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.HOME, ma.actor,  Origin.EMPTY).getUri())
    }

    private fun getStaticData(ma: MyAccount): NoteEditorData? {
        return NoteEditorData.Companion.newEmpty(ma)
                .setContent(MyHtmlTest.Companion.twitterBodyTypedPlain + " " + DemoData.demoData.testRunUid, TextMediaType.PLAIN)
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
        when (NoteEditorTwitterTest.Companion.editingStep.incrementAndGet()) {
            2 -> editingStep2()
            else -> {
                NoteEditorTwitterTest.Companion.editingStep.set(1)
                ActivityTestHelper.Companion.openEditor<ActivityViewItem?>("default", activity)
                editingStep1()
            }
        }
        MyLog.v(this, "After step " + NoteEditorTwitterTest.Companion.editingStep + " ended")
    }

    @Throws(InterruptedException::class)
    private fun editingStep1() {
        val method = "editingStep1"
        MyLog.v(this, "$method started")
        val editorView: View = ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        val editor = activity.noteEditor
        instrumentation.runOnMainSync { editor.startEditingNote(data) }
        ActivityTestHelper.Companion.waitViewVisible(method, editorView)
        assertInitialText("Initial text")
        MyLog.v(this, "$method ended")
    }

    @Throws(InterruptedException::class)
    private fun editingStep2() {
        val method = "editingStep2"
        MyLog.v(this, "$method started")
        val editorView = activity.findViewById<View?>(R.id.note_editor)
        ActivityTestHelper.Companion.waitViewVisible("$method; Restored note is visible", editorView)
        assertInitialText("Note restored")
        ActivityTestHelper.Companion.hideEditorAndSaveDraft<ActivityViewItem?>(method, activity)
        ActivityTestHelper.Companion.openEditor<ActivityViewItem?>(method, activity)
        assertTextCleared()
        val helper = ActivityTestHelper<TimelineActivity<*>?>(activity)
        helper.clickMenuItem("$method click Discard", R.id.discardButton)
        ActivityTestHelper.Companion.waitViewInvisible("$method; Editor hidden after discard", editorView)
        MyLog.v(this, "$method ended")
    }

    @Throws(InterruptedException::class)
    private fun assertInitialText(description: String?) {
        val editor = activity.noteEditor
        val textView = activity.findViewById<TextView?>(R.id.noteBodyEditText)
        ActivityTestHelper.Companion.waitTextInAView(description, textView,
                MyHtml.fromContentStored(data.getContent(), TextMediaType.PLAIN))
        Assert.assertEquals(description, data.toTestSummary(), editor.data.toTestSummary())
    }

    private fun assertTextCleared() {
        val editor = activity.noteEditor
        Assert.assertTrue("Editor is not null", editor != null)
        Assert.assertEquals(NoteEditorData.Companion.newEmpty(
                activity.myContext.accounts().currentAccount).toTestSummary(),
                editor.data.toTestSummary())
    }

    companion object {
        private val editingStep: AtomicInteger? = AtomicInteger()
    }
}