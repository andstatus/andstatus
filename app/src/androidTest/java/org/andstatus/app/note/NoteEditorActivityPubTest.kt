/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ReplaceTextAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ConnectionMock
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.note.NoteEditorTest
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test
import kotlin.properties.Delegates

class NoteEditorActivityPubTest : TimelineActivityTest<ActivityViewItem>() {
    private var mock: ConnectionMock by Delegates.notNull()

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithAccounts(this)
        mock = ConnectionMock.newFor(DemoData.demoData.activityPubTestAccountName)
        val ma = mock.getData().getMyAccount()
         MyContextHolder.myContextHolder.getBlocking().accounts().setCurrentAccount(ma)
        Assert.assertTrue("isValidAndSucceeded $ma", ma.isValidAndSucceeded())
        MyLog.i(this, "setUp ended")
        return Intent(Intent.ACTION_VIEW,
                 MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.HOME, ma.actor,  Origin.EMPTY).getUri())
    }

    @Test
    @Throws(InterruptedException::class)
    fun sendingPublic() {
        val method = "sendingPublic"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.hideEditorAndSaveDraft(method, activity)
        ActivityTestHelper.openEditor(method, activity)
        val actorUniqueName = "me" + DemoData.demoData.testRunUid + "@mastodon.example.com"
        val content = "Sending note to the unknown yet Actor @$actorUniqueName"
        // TypeTextAction doesn't work here due to auto-correction
        Espresso.onView(ViewMatchers.withId(org.andstatus.app.R.id.noteBodyEditText)).perform(ReplaceTextAction(content))
        TestSuite.waitForIdleSync()
        ActivityTestHelper.clickSendButton(method, activity)
        val noteId: Long = ActivityTestHelper.waitAndGetIdOfStoredNote(method, content)
        val note: Note = Note.Companion.loadContentById(mock.connection.myContext(), noteId)
        Assert.assertEquals("Note $note", DownloadStatus.SENDING, note.getStatus())
        Assert.assertEquals("Visibility $note", Visibility.PUBLIC_AND_TO_FOLLOWERS, note.audience().visibility)
        Assert.assertFalse("Not sensitive $note", note.isSensitive())
        Assert.assertTrue("Audience should contain $actorUniqueName\n $note",
                note.audience().getNonSpecialActors().stream().anyMatch { a: Actor -> actorUniqueName == a.uniqueName })
    }

    @Test
    fun sendingSensitive() {
        wrap { _sendingSensitive() }
    }

    @Throws(Exception::class)
    private fun _sendingSensitive() {
        val method = "sendingSensitive"
        TestSuite.waitForListLoaded(activity, 2)
        ActivityTestHelper.hideEditorAndSaveDraft(method, activity)
        ActivityTestHelper.openEditor(method, activity)
        val content = "Sending sensitive note " + DemoData.demoData.testRunUid
        Espresso.onView(ViewMatchers.withId(org.andstatus.app.R.id.is_sensitive))
                .check(ViewAssertions.matches(ViewMatchers.isNotChecked()))
                .perform(ViewActions.scrollTo(), ViewActions.click())
        // TypeTextAction doesn't work here due to auto-correction
        Espresso.onView(ViewMatchers.withId(org.andstatus.app.R.id.noteBodyEditText)).perform(ReplaceTextAction(content))
        TestSuite.waitForIdleSync()
        ActivityTestHelper.clickSendButton(method, activity)
        val noteId: Long = ActivityTestHelper.waitAndGetIdOfStoredNote(method, content)
        val note: Note = Note.Companion.loadContentById(mock.connection.myContext(), noteId)
        Assert.assertEquals("Note $note", DownloadStatus.SENDING, note.getStatus())
        Assert.assertEquals("Visibility $note", Visibility.PUBLIC_AND_TO_FOLLOWERS, note.audience().visibility)
        Assert.assertTrue("Sensitive $note", note.isSensitive())
        val result = mock.getHttpMock().waitForPostContaining(content)
        val postedObject = result.request.postParams.get()
        val jso = postedObject.getJSONObject("object")
        Assert.assertFalse("No name $postedObject", jso.has("name"))
        Assert.assertEquals("Note content $postedObject", content,
                MyHtml.htmlToPlainText(jso.getString("content")))
        Assert.assertEquals("Sensitive $postedObject", "true",
                JsonUtils.optString(jso, "sensitive", "(not found)"))
    }

    @Test
    @Throws(InterruptedException::class)
    fun attachOneImage() {
        NoteEditorTest.attachImages(this, 1, 1)
    }

    @Test
    @Throws(InterruptedException::class)
    fun attachTwoImages() {
        NoteEditorTest.attachImages(this, 2, 2)
    }
}
