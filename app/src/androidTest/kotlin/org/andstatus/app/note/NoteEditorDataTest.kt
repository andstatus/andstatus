package org.andstatus.app.note

import android.net.Uri
import android.provider.BaseColumns
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData.Companion.demoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.AttachedImageFiles
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.origin.Origin
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class NoteEditorDataTest {

    @Before
    fun setUp() {
        TestSuite.initializeWithData(this)
    }

    @Test
    fun noteEditorDataConversation() {
        val ma: MyAccount = demoData.getMyAccount(demoData.conversationAccountName)
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins.fromName(demoData.conversationOriginName)
        Assert.assertEquals(origin, ma.origin)
        val entryNoteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.id, demoData.conversationEntryNoteOid)
        val entryActorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.id, demoData.conversationEntryAuthorOid)
        val memberActorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.id,
                demoData.conversationAuthorThirdActorOid)
        assertData(ma, entryNoteId, entryActorId, 0, memberActorId, false)
        assertData(ma, entryNoteId, entryActorId, 0, memberActorId, true)
        assertData(ma, 0, 0, memberActorId, 0, false)
        assertData(ma, 0, 0, memberActorId, 0, true)
    }

    private fun assertData(ma: MyAccount, inReplyToMsgId: Long, inReplyToActorId: Long, recipientId: Long,
                           memberActorId: Long, replyAll: Boolean) {
        val uri = Uri.parse("http://example.com/" + demoData.testRunUid + "/some.png")
        val data: NoteEditorData = NoteEditorData.Companion.newReplyTo(inReplyToMsgId, ma)
                .addToAudience(recipientId)
                .setReplyToConversationParticipants(replyAll)
                .setContent("Some text here " + demoData.testRunUid, TextMediaType.UNKNOWN)
        Assert.assertFalse(data.toString(), data.getContent().contains("@"))
        data.addMentionsToText()
        Assert.assertEquals(recipientId, data.activity.getNote().audience().getFirstNonSpecial().actorId)
        assertMentionedActor(data, inReplyToActorId, true)
        assertMentionedActor(data, memberActorId, replyAll)
        Assert.assertEquals(data.toString(), AttachedImageFiles.Companion.EMPTY, data.getAttachedImageFiles())
    }

    private fun assertMentionedActor(data: NoteEditorData, mentionedActorId: Long, isMentionedExpected: Boolean) {
        if (mentionedActorId == 0L) {
            return
        }
        val expectedName = MyQuery.actorIdToStringColumnValue(
                if (data.ma.origin.isMentionAsWebFingerId()) ActorTable.WEBFINGER_ID else ActorTable.USERNAME, mentionedActorId)
        Assert.assertTrue(expectedName.isNotEmpty())
        val isMentioned = data.getContent().contains("@$expectedName")
        Assert.assertEquals(data.toString() + "; expected name:" + expectedName, isMentionedExpected, isMentioned)
    }

    @Test
    fun testAddMentionsWhenNobodyIsMentioned() {
        val myAccount: MyAccount = demoData.getMyAccount(demoData.conversationAccountName)
        val noteId = MyQuery.getLongs(myAccount.myContext, "SELECT " + BaseColumns._ID
                + " FROM " + NoteTable.TABLE_NAME
                + " WHERE " + NoteTable.ORIGIN_ID + "=" + myAccount.origin.id
                + " AND " + NoteTable.CONTENT + "='Older one note'").stream()
                .findFirst().orElse(0L)
        val data: NoteEditorData = NoteEditorData.Companion.newReplyTo(noteId, myAccount)
                .setReplyToMentionedActors(true)
                .addMentionsToText()
        MatcherAssert.assertThat(data.getContent(), CoreMatchers.containsString("@second@pump1.example.com "))
        MatcherAssert.assertThat(data.getContent(), CoreMatchers.not(CoreMatchers.containsString("@t131t")))
    }
}
