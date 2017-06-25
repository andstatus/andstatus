package org.andstatus.app.msg;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.context.DemoData;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageEditorDataTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testMessageEditorDataConversation() {
        MyAccount ma = DemoData.getMyAccount(DemoData.CONVERSATION_ACCOUNT_NAME);
        long entryMsgId = MyQuery.oidToId(OidEnum.MSG_OID, MyContextHolder.get()
                .persistentOrigins()
                .fromName(DemoData.CONVERSATION_ORIGIN_NAME).getId(),
                DemoData.CONVERSATION_ENTRY_MESSAGE_OID);
        long entryUserId = MyQuery.oidToId(OidEnum.USER_OID, ma.getOrigin().getId(),
                DemoData.CONVERSATION_ENTRY_USER_OID);
        long memberUserId = MyQuery.oidToId(OidEnum.USER_OID, ma.getOrigin().getId(),
                DemoData.CONVERSATION_MEMBER_USER_OID);
        assertData(ma, entryMsgId, entryUserId, 0, memberUserId, false);
        assertData(ma, entryMsgId, entryUserId, 0, memberUserId, true);
        assertData(ma,          0,           0, memberUserId, 0, false);
        assertData(ma,          0,           0, memberUserId, 0, true);
    }

    private void assertData(MyAccount ma, long inReplyToMsgId, long inReplyToUserId, long recipientId,
            long memberUserId, boolean replyAll) {
        Uri uri = Uri.parse("http://example.com/" + DemoData.TESTRUN_UID + "/some.png");
        MessageEditorData data = MessageEditorData.newEmpty(ma)
                .setInReplyToId(inReplyToMsgId)
                .setRecipientId(recipientId)
                .setReplyToConversationParticipants(replyAll)
                .setBody("Some text here " + DemoData.TESTRUN_UID);
        assertFalse(data.toString(), data.body.contains("@"));
        data.addMentionsToText();
        assertEquals(recipientId, data.recipientId);
        assertMentionedUser(data, inReplyToUserId, true);
        assertMentionedUser(data, memberUserId, replyAll);
        assertEquals(data.toString(), Uri.EMPTY, data.getMediaUri());
    }

    private void assertMentionedUser(MessageEditorData data, long mentionedUserId,
            boolean isMentioned_in) {
        if (mentionedUserId == 0) {
            return;
        }
        String expectedName = MyQuery.userIdToStringColumnValue(
                data.ma.getOrigin().isMentionAsWebFingerId() ? UserTable.WEBFINGER_ID
                        : UserTable.USERNAME, mentionedUserId);
        assertTrue(!TextUtils.isEmpty(expectedName));
        boolean isMentioned = data.body.contains("@" + expectedName);
        assertEquals(data.toString() + "; expected name:" + expectedName, isMentioned_in, isMentioned);
    }

}
