package org.andstatus.app.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.MessageCounters;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.MyServiceManager;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionPumpio;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.OAuthClientKeysTest;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;

import java.util.Set;

public class DataInserterTest extends InstrumentationTestCase {
    private static volatile int iteration = 0;
    private Context context;

    private MbUser accountMbUser;
    private MyAccount ma;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        iteration++;
        context = TestSuite.initialize(this);
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));

        OAuthClientKeysTest.insertTestKeys();
        
        addPumpIoAccount("acct:firstTestUser@identi.ca");
        addPumpIoAccount("acct:t131t@identi.ca");

        String userOid = "acct:" + TestSuite.CONVERSATION_ACCOUNT_NAME.substring(0, TestSuite.CONVERSATION_ACCOUNT_NAME.indexOf('/'));
        accountMbUser = userFromPumpioOid(userOid);
        accountMbUser.avatarUrl = TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL;
        MyAccount ma1 = addPumpIoAccount(userOid);
        
        MyPreferences.onPreferencesChanged();
        MyContextHolder.initialize(context, this);
        assertTrue(MyContextHolder.get().initialized());
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(ma1.getAccountName()); 
        assertTrue(ma1.getAccountName(), ma != null);
        assertEquals(ma1.getAccountName(), ma1.getUserId(), ma.getUserId());
        
        MyServiceManager.setServiceUnavailable();
    }
    
    public void testFollowingUser() throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        String messageOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        deleteOldMessage(OriginType.PUMPIO.getId(), messageOid);
        
        MessageCounters counters = new MessageCounters(ma, context, TimelineTypeEnum.HOME);
        DataInserter di = new DataInserter(counters);
        String username = "somebody@identi.ca";
        String userOid =  "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(OriginType.PUMPIO.getId(), userOid);
        somebody.userName = username;
        somebody.actor = accountMbUser;
        somebody.followedByActor = TriState.FALSE;
        somebody.url = "http://identi.ca/somebody";
        di.insertOrUpdateUser(somebody);

        long somebodyId = MyProvider.oidToId(OidEnum.USER_OID, OriginType.PUMPIO.getId(), userOid);
        assertTrue( "User " + username + " added", somebodyId != 0);
        
        Set<Long> followedIds = MyProvider.getIdsOfUsersFollowedBy(ma.getUserId());
        assertFalse( "User " + username + " is not followed", followedIds.contains(somebodyId));

        MbMessage message = MbMessage.fromOriginAndOid(OriginType.PUMPIO.getId(), messageOid);
        message.setBody("The test message by Somebody");
        message.sentDate = 13312696000L;
        message.via = "MyCoolClient";
        message.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        message.sender = somebody;
        message.actor = accountMbUser;
        TestSuite.clearAssertionData();
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        AssersionData data = TestSuite.getMyContextForTest().takeDataByKey("insertOrUpdateMsg");
        assertFalse( "Data put", data.isEmpty());
        assertEquals("Message Oid", messageOid, data.getValues().getAsString(MyDatabase.Msg.MSG_OID));
        assertEquals("Message permalink before storage", message.url, data.getValues().getAsString(MyDatabase.Msg.URL));
        assertEquals("Message permalink", message.url, ma.messagePermalink(message.sender.userName, messageId));

        long authorId = MyProvider.msgIdToLongColumnValue(Msg.AUTHOR_ID, messageId);
        assertEquals("Author of the message", somebodyId, authorId);
        String url = MyProvider.msgIdToStringColumnValue(Msg.URL, messageId);
        assertEquals("Url of the message", message.url, url);
        long senderId = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, messageId);
        assertEquals("Sender of the message", somebodyId, senderId);
        url = MyProvider.userIdToStringColumnValue(User.URL, senderId);
        assertEquals("Url of the sender " + somebody.userName , somebody.url, url);
        
        Uri contentUri = MyProvider.getTimelineUri(ma.getUserId(), TimelineTypeEnum.FOLLOWING_USER, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.FollowingUser.FOLLOWING_USER_ID + " = ?",
                new String[] {
                        Long.toString(somebodyId)
                });
        String[] PROJECTION = new String[] {
            Msg._ID
            };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("No messages of this user in the Following timeline", cursor.getCount() == 0);
        cursor.close();
        
        somebody.followedByActor = TriState.TRUE;
        di.insertOrUpdateUser(somebody);

        followedIds = MyProvider.getIdsOfUsersFollowedBy(ma.getUserId());
        assertTrue( "User " + username + ", id=" + somebodyId + " is followed", followedIds.contains(somebodyId));

        cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Message by user=" + somebodyId + " is in the Following timeline", cursor.getCount() > 0);
        cursor.close();
    }

    private void deleteOldMessage(long originId, String messageOid) {
        long messageIdOld = MyProvider.oidToId(OidEnum.MSG_OID, originId, messageOid);
        if (messageIdOld != 0) {
            SelectionAndArgs sa = new SelectionAndArgs();
            sa.addSelection(Msg.TABLE_NAME + "." + MyDatabase.Msg._ID + " = ?", new String[] {
                String.valueOf(messageIdOld)
            });
            int deleted = context.getContentResolver().delete(MyProvider.MSG_CONTENT_URI, sa.selection,
                    sa.selectionArgs);
            assertEquals( "Old message id=" + messageIdOld + " deleted", 1, deleted);
        }
    }
    
    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));

        String username = "anybody@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(OriginType.PUMPIO.getId(), "acct:" + username);
        author.userName = username;
        author.actor = accountMbUser;

        username = "firstreader@identi.ca";
        MbUser firstReader = MbUser.fromOriginAndUserOid(OriginType.PUMPIO.getId(), "acct:" + username);
        firstReader.userName = username;
        firstReader.actor = accountMbUser;
        
        MbMessage message = MbMessage.fromOriginAndOid(OriginType.PUMPIO.getId(), "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED");
        message.setBody("The test message by Anybody from http://pumpity.net");
        message.sentDate = 13312697000L;
        message.via = "SomeOtherClient";
        message.sender = author;
        message.actor = firstReader;
        message.favoritedByActor = TriState.TRUE;

        DataInserter di = new DataInserter(ma, context, TimelineTypeEnum.HOME);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        
        Uri contentUri = MyProvider.getTimelineUri(ma.getUserId(), TimelineTypeEnum.HOME, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[] {
                        Long.toString(messageId)
                });
        String[] PROJECTION = new String[] {
            MsgOfUser.FAVORITED,
            MyDatabase.User.LINKED_USER_ID
            };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertTrue("Message not favorited", cursor.getInt(0) == 0);
        assertTrue("Message not favorited by AccountUser", cursor.getLong(1) == ma.getUserId());
        cursor.close();
    }

    public void testMessageFavoritedByAccountUser() throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));

        String username = "example@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(OriginType.PUMPIO.getId(), "acct:" + username);
        author.userName = username;
        author.actor = accountMbUser;

        MbMessage message = MbMessage.fromOriginAndOid(OriginType.PUMPIO.getId(), "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123");
        message.setBody("The test message by Example from the http://pumpity.net");
        message.sentDate = 13312795000L;
        message.via = "UnknownClient";
        message.sender = author;
        message.actor = accountMbUser;
        message.favoritedByActor = TriState.TRUE;

        DataInserter di = new DataInserter(ma, context, TimelineTypeEnum.HOME);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        
        Uri contentUri = MyProvider.getTimelineUri(ma.getUserId(), TimelineTypeEnum.HOME, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[] {
                        Long.toString(messageId)
                });
        String[] PROJECTION = new String[] {
            MsgOfUser.FAVORITED,
            MyDatabase.User.LINKED_USER_ID
            };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertTrue("Message favorited", cursor.getInt(0) == 1);
        assertTrue("Message not favorited by AccountUser", cursor.getLong(1) == ma.getUserId());
        cursor.close();
    }
    
    public void testDirectMessageToMyAccount() throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        String messageOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ";
        deleteOldMessage(OriginType.PUMPIO.getId(), messageOid);

        String username = "t131t@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(OriginType.PUMPIO.getId(), "acct:" + username);
        author.userName = username;
        author.actor = accountMbUser;
        
        MbMessage message = MbMessage.fromOriginAndOid(OriginType.PUMPIO.getId(), messageOid);
        message.setBody("Hello, this is a test Direct message by your namesake from http://pumpity.net");
        message.sentDate = 13312699000L;
        message.via = "AnyOtherClient";
        message.sender = author;
        message.actor = accountMbUser;
        message.recipient = accountMbUser;
        long messageId = addMessage(message);
        
        Uri contentUri = MyProvider.getTimelineUri(ma.getUserId(), TimelineTypeEnum.HOME, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[] {
                        Long.toString(messageId)
                });
        String[] PROJECTION = new String[] {
            Msg.RECIPIENT_ID,
            MyDatabase.User.LINKED_USER_ID,
            MsgOfUser.DIRECTED
            };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertEquals("Recipient " + ma.getAccountName() + "; Id", ma.getUserId(), cursor.getLong(0));
        assertEquals("Message is directed to AccountUser", ma.getUserId(), cursor.getLong(1));
        assertTrue("Message " + messageId + " is direct", cursor.getInt(2) == 1);
        cursor.close();
    }
    
    public void testConversation() throws ConnectionException {
        assertEquals("Only PumpIo supported in this test", OriginType.PUMPIO, TestSuite.CONVERSATION_ORIGIN_TYPE  );
        
        MbUser author1 = userFromPumpioOid("acct:first@example.net");
        author1.avatarUrl = "https://raw.github.com/andstatus/andstatus/master/res/drawable/splash_logo.png";
        MbUser author2 = userFromPumpioOid("acct:second@identi.ca");
        author2.avatarUrl = "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png";
        MbUser author3 = userFromPumpioOid("acct:third@pump.example.com");
        author3.avatarUrl = "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif";
        MbUser author4 = userFromPumpioOid("acct:fourthWithoutAvatar@pump.example.com");
        
        MbMessage minus1 = buildPumpIoMessage(author2, "Older one message", null, null);
        MbMessage selected = buildPumpIoMessage(author1, "Selected message", minus1, TestSuite.CONVERSATION_ENTRY_MESSAGE_OID);
        MbMessage reply1 = buildPumpIoMessage(author3, "Reply 1 to selected", selected, null);
        MbMessage reply2 = buildPumpIoMessage(author2, "Reply 2 to selected", selected, null);
        MbMessage reply3 = buildPumpIoMessage(author1, "Reply 3 to selected by the same author", selected, null);
        addMessage(selected);
        addMessage(reply3);
        addMessage(reply1);
        addMessage(reply2);
        MbMessage reply4 = buildPumpIoMessage(author4, "Reply 4 to Reply 1 other author", reply1, null);
        addMessage(reply4);
        addMessage(buildPumpIoMessage(author2, "Reply 5 to Reply 4", reply4, null));
        addMessage(buildPumpIoMessage(author3, "Reply 6 to Reply 4 - the second", reply4, null));

        MbMessage reply7 = buildPumpIoMessage(author1, "Reply 7 to Reply 2", reply2, null);
        MbMessage reply8 = buildPumpIoMessage(author4, "<b>Reply 8</b> to Reply 7", reply7, null);
        MbMessage reply9 = buildPumpIoMessage(author2, "Reply 9 to Reply 7", reply7, null);
        addMessage(reply9);
        MbMessage reply10 = buildPumpIoMessage(author3, "Reply 10 to Reply 8", reply8, null);
        addMessage(reply10);
    }

    private MbMessage buildPumpIoMessage(MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn) {
        String messageOid = messageOidIn;
        if (TextUtils.isEmpty(messageOid)) {
            messageOid = author.url  + "/" + (inReplyToMessage == null ? "note" : "comment") + "thisisfakeuri" + System.nanoTime();
        }
        MbMessage message = MbMessage.fromOriginAndOid(OriginType.PUMPIO.getId(), messageOid);
        message.setBody(body + (inReplyToMessage != null ? " it" + iteration : "" ));
        message.sentDate = System.currentTimeMillis();
        message.via = "AndStatus";
        message.sender = author;
        message.actor = accountMbUser;
        message.inReplyToMessage = inReplyToMessage;
        try {
            Thread.sleep(2);
        } catch (InterruptedException ignored) {
        }
        return message;
    }
    
    private long addMessage(MbMessage message) {
        DataInserter di = new DataInserter(ma, context, TimelineTypeEnum.HOME);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added " + message.oid, messageId != 0);
        return messageId;
    }
    
    
    private MyAccount addPumpIoAccount(String userOid) throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));

        Origin origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.PUMPIO);
        long accountUserId_existing = MyProvider.oidToId(OidEnum.USER_OID, origin.getId(), userOid);
        MbUser mbUser = userFromPumpioOid(userOid);
        MyAccount ma = addPumpIoAccount(mbUser);
        long accountUserId = ma.getUserId();
        if (accountUserId_existing == 0 && !userOid.contains("firstTestUser")) {
            assertTrue("AccountUserId != 1", accountUserId != 1);
        } else {
            assertTrue("AccountUserId != 0", accountUserId != 0);
        }
        assertTrue("Account " + userOid + " is persistent", ma != null);
        assertTrue("Account UserOid", ma.getUserOid().equalsIgnoreCase(userOid));
        assertTrue("Account is successfully verified", ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED);
        return ma;
    }
    
    private MbUser userFromPumpioOid(String userOid) {
        ConnectionPumpio connection = new ConnectionPumpio();
        String userName = connection.userOidToUsername(userOid);
        MbUser mbUser = MbUser.fromOriginAndUserOid(OriginType.PUMPIO.getId(), userOid);
        mbUser.userName = userName;
        mbUser.url = "http://" + connection.usernameToHost(userName)  + "/" + ConnectionPumpio.userOidToNickname(userOid);
        if (accountMbUser != null) {
            mbUser.actor = accountMbUser;
        }
        return mbUser;
    }

    private MyAccount addPumpIoAccount(MbUser mbUser) throws ConnectionException {
        assertTrue(MyContextHolder.get().initialized());
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(mbUser.originId);
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(mbUser.userName + "/" + origin.getName(), TriState.TRUE);
        builder.setUserTokenWithSecret("sampleUserTokenFor" + mbUser.userName, "sampleUserSecretFor" + mbUser.userName);
        assertTrue("Credentials of " + mbUser.userName + " are present", builder.getAccount().getCredentialsPresent());
        builder.onCredentialsVerified(mbUser, null);
        assertTrue("Account is persistent", builder.isPersistent());
        MyAccount ma = builder.getAccount();
        assertEquals("Credentials of " + mbUser.userName + " successfully verified", 
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        long userId = ma.getUserId();
        assertTrue("Account " + mbUser.userName + " has UserId", userId != 0);
        assertEquals("Account UserOid", ma.getUserOid(), mbUser.oid);
        assertEquals("User in the database for id=" + userId, 
                mbUser.oid,
                MyProvider.idToOid(OidEnum.USER_OID, userId, 0));
        assertEquals("Account name", mbUser.userName + "/" + origin.getName(), ma.getAccountName());
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getUserId());
        return ma;
    }
    
    public void testHtmlContent() {
        boolean htmlEnabledStored = MyPreferences.getHtmlContentEnabled(); 
        MbUser author1 = userFromPumpioOid("acct:html@example.com");
        author1.avatarUrl = "http://png-5.findicons.com/files/icons/2198/dark_glass/128/html.png";

        String bodyString = "<h4>This is a message with HTML content</h4>" 
                + "<p>This is a second line, <b>Bold</b> formatting." 
                + "<br /><i>This is italics</i>. <b>And this is bold</b> <u>The text is underlined</u>.</p>"
                + "<p>A separate paragraph.</p>";
        assertFalse("HTML removed", MbMessage.stripHtml(bodyString).contains("<"));
        assertHtmlMessage(author1, bodyString);

        String bodyImgString = "A message with <b>HTML</b> <i>img</i> tag: " 
                + "<img src='http://static.fsf.org/dbd/hollyweb.jpeg' alt='Stop DRM in HTML5' />"
                + ", <a href='http://www.fsf.org/'>the link in 'a' tag</a> <br/>" 
                + "and a plain text link to the issue 60: https://github.com/andstatus/andstatus/issues/60";
        assertHtmlMessage(author1, bodyImgString);
        
        MyPreferences.setHhmlContentEnabled(htmlEnabledStored);
    }
    
    private void assertHtmlMessage(MbUser author, String bodyString) {
        MyPreferences.setHhmlContentEnabled(true);
        MbMessage msg1 = buildPumpIoMessage(author, bodyString, null, null);
        long msgId1 = addMessage(msg1);
        String body1 = MyProvider.msgIdToStringColumnValue(Msg.BODY, msgId1);
        assertEquals("HTML preserved", bodyString, body1);
        
        MyPreferences.setHhmlContentEnabled(false);
        MbMessage msg2 = buildPumpIoMessage(author, bodyString, null, null);
        long msgId2 = addMessage(msg2);
        String body2 = MyProvider.msgIdToStringColumnValue(Msg.BODY, msgId2);
        assertEquals("HTML removed", MbMessage.stripHtml(bodyString), body2);
    }
}
