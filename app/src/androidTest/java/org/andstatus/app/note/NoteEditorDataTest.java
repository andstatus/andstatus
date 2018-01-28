package org.andstatus.app.note;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.origin.Origin;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteEditorDataTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void noteEditorDataConversation() {
        MyAccount ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        final Origin origin = MyContextHolder.get().persistentOrigins().fromName(demoData.CONVERSATION_ORIGIN_NAME);
        assertEquals(origin, ma.getOrigin());
        long entryMsgId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.getId(),
                demoData.CONVERSATION_ENTRY_NOTE_OID);
        long entryActorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.getId(),
                demoData.CONVERSATION_ENTRY_AUTHOR_OID);
        long memberActorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.getId(),
                demoData.CONVERSATION_AUTHOR_THIRD_ACTOR_OID);
        assertData(ma, entryMsgId, entryActorId, 0, memberActorId, false);
        assertData(ma, entryMsgId, entryActorId, 0, memberActorId, true);
        assertData(ma,          0,           0, memberActorId, 0, false);
        assertData(ma,          0,           0, memberActorId, 0, true);
    }

    private void assertData(MyAccount ma, long inReplyToMsgId, long inReplyToActorId, long recipientId,
            long memberActorId, boolean replyAll) {
        Uri uri = Uri.parse("http://example.com/" + demoData.TESTRUN_UID + "/some.png");
        NoteEditorData data = NoteEditorData.newEmpty(ma)
                .setInReplyToNoteId(inReplyToMsgId)
                .addRecipientId(recipientId)
                .setReplyToConversationParticipants(replyAll)
                .setBody("Some text here " + demoData.TESTRUN_UID);
        assertFalse(data.toString(), data.body.contains("@"));
        data.addMentionsToText();
        assertEquals(recipientId, data.recipients.getFirst().actorId);
        assertMentionedActor(data, inReplyToActorId, true);
        assertMentionedActor(data, memberActorId, replyAll);
        assertEquals(data.toString(), Uri.EMPTY, data.getMediaUri());
    }

    private void assertMentionedActor(NoteEditorData data, long mentionedActorId, boolean isMentioned_in) {
        if (mentionedActorId == 0) {
            return;
        }
        String expectedName = MyQuery.actorIdToStringColumnValue(
                data.ma.getOrigin().isMentionAsWebFingerId() ? ActorTable.WEBFINGER_ID
                        : ActorTable.USERNAME, mentionedActorId);
        assertTrue(!TextUtils.isEmpty(expectedName));
        boolean isMentioned = data.body.contains("@" + expectedName);
        assertEquals(data.toString() + "; expected name:" + expectedName, isMentioned_in, isMentioned);
    }

}
