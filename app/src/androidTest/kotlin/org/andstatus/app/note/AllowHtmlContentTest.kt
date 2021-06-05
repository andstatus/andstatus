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
package org.andstatus.app.note

import android.content.Intent
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.HtmlContentTester
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.ConnectionMock
import org.andstatus.app.net.social.Note
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RawResourceUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class AllowHtmlContentTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)

    @Test
    @Throws(Exception::class)
    fun testAllowHtlContent() {
        val isAllowedInPumpIoStored: Boolean = DemoData.demoData.getPumpioConversationOrigin().isHtmlContentAllowed()
        val isAllowedInGnuSocialStored: Boolean = DemoData.demoData.getGnuSocialOrigin().isHtmlContentAllowed()
        HtmlContentTester().insertPumpIoHtmlContent()
        oneGnuSocialTest(isAllowedInGnuSocialStored)
        setHtmlContentAllowed(!isAllowedInPumpIoStored, !isAllowedInGnuSocialStored)
        HtmlContentTester().insertPumpIoHtmlContent()
        oneGnuSocialTest(!isAllowedInGnuSocialStored)

        // Return setting back
        setHtmlContentAllowed(isAllowedInPumpIoStored, isAllowedInGnuSocialStored)
        testShareHtml()
    }

    private fun testShareHtml() {
        val origin: Origin = DemoData.demoData.getPumpioConversationOrigin()
        Assert.assertNotNull(DemoData.demoData.conversationOriginName + " exists", origin)
        val noteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.id, DemoData.demoData.htmlNoteOid)
        val note: Note = Note.Companion.loadContentById( myContext, noteId)
        Assert.assertTrue("origin=" + origin.id + "; oid=" + DemoData.demoData.htmlNoteOid, noteId != 0L)
        val noteShare = NoteShare(origin, noteId, NoteDownloads.Companion.fromNoteId(myContext, noteId))
        val intent = noteShare.intentToViewAndShare(true)
        Assert.assertTrue(intent.hasExtra(Intent.EXTRA_TEXT))
        Assert.assertTrue(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_TEXT)?.contains(
                        MyHtml.htmlToPlainText(HtmlContentTester.HTML_BODY_IMG_STRING)) == true)
        if (origin.isHtmlContentAllowed()) {
            Assert.assertTrue(note.content, intent.hasExtra(Intent.EXTRA_HTML_TEXT))
            MatcherAssert.assertThat(note.content,
                    intent.getStringExtra(Intent.EXTRA_HTML_TEXT),
                    CoreMatchers.containsString(HtmlContentTester.HTML_BODY_IMG_STRING))
        }
    }

    @Test
    fun testSharePlainText() {
        val body = "Posting as a plain Text " + DemoData.demoData.testRunUid
        val myAccount: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName)
        val activity: AActivity = DemoNoteInserter.Companion.addNoteForAccount(myAccount, body,
                DemoData.demoData.plainTextNoteOid, DownloadStatus.LOADED)
        val noteShare = NoteShare(myAccount.origin, activity.getNote().noteId,
                NoteDownloads.Companion.fromNoteId(myContext, activity.getNote().noteId))
        val intent = noteShare.intentToViewAndShare(true)
        Assert.assertTrue(intent.extras?.containsKey(Intent.EXTRA_TEXT) == true)
        Assert.assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_TEXT)?.contains(body) == true)
        Assert.assertTrue(intent.extras?.containsKey(Intent.EXTRA_HTML_TEXT) == true)
        DemoData.demoData.assertConversations()
    }

    private fun setHtmlContentAllowed(allowedInPumpIo: Boolean, allowedInGnuSocial: Boolean) {
        Origin.Builder(DemoData.demoData.getPumpioConversationOrigin()).setHtmlContentAllowed(allowedInPumpIo).save()
        Origin.Builder(DemoData.demoData.getGnuSocialOrigin()).setHtmlContentAllowed(allowedInGnuSocial).save()
        TestSuite.forget()
        TestSuite.initialize(this)
        Assert.assertEquals("is HTML content allowed in PumpIo", allowedInPumpIo,
                DemoData.demoData.getPumpioConversationOrigin().isHtmlContentAllowed())
    }

    @Throws(Exception::class)
    private fun oneGnuSocialTest(isHtmlAllowed: Boolean) {
        Assert.assertEquals("is HTML content allowed in GnuSocial", isHtmlAllowed,
                DemoData.demoData.getGnuSocialOrigin().isHtmlContentAllowed())
        val mock: ConnectionMock = ConnectionMock.newFor(DemoData.demoData.gnusocialTestAccountName)
        mock.addResponse(org.andstatus.app.tests.R.raw.gnusocial_note_with_html)
        val noteOid = "4453144"
        val activity = mock.connection.getNote(noteOid).get()
        Assert.assertEquals("Received a note $activity", AObjectType.NOTE, activity.getObjectType())
        Assert.assertEquals("Should be UPDATE $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Note Oid", noteOid, activity.getNote().oid)
        Assert.assertEquals("Actor Username", "vaeringjar", activity.getActor().getUsername())
        Assert.assertEquals("Author should be Actor", activity.getActor(), activity.getAuthor())
        Assert.assertTrue("inReplyTo should not be empty $activity", activity.getNote().getInReplyTo().nonEmpty)
        val jso = JSONObject(RawResourceUtils.getString(org.andstatus.app.tests.R.raw.gnusocial_note_with_html))
        val expectedContent = jso.getString(if (isHtmlAllowed) "statusnet_html" else "text")
        val actualContent = if (isHtmlAllowed) activity.getNote().content else MyHtml.htmlToPlainText(activity.getNote().content)
        Assert.assertEquals(if (isHtmlAllowed) "HTML content allowed" else "No HTML content", expectedContent, actualContent)
        activity.getNote().updatedDate = MyLog.uniqueCurrentTimeMS()
        activity.setUpdatedNow(0)
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        val executionContext = CommandExecutionContext(
                 myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, ma, 123))
        DataUpdater(executionContext).onActivity(activity)
    }
}
