package org.andstatus.app.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import org.andstatus.app.MessageCounters;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;

import java.util.Set;

public class DataInserterTest extends InstrumentationTestCase {
    private Context context;
    private MbUser accountMbUser;
    private final String accountUserOid = "acct:t131t@identi.ca";
    private String accountName;
    private long accountUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = TestSuite.initialize(this);
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));

        String firstUserName = "firstTestUser@identi.ca";
        MbUser firstMbUser  = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), 
                "acct:" + firstUserName);
        firstMbUser.userName = firstUserName;
        addAccount(firstMbUser);

        long accountUserId_existing = MyProvider.oidToId(OidEnum.USER_OID, firstMbUser.originId, firstMbUser.oid);
        accountMbUser = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), accountUserOid);
        accountMbUser.userName = "t131t@identi.ca";
        accountMbUser.url = "http://identi.ca/t131t";
        MyAccount.Builder builder = addAccount(accountMbUser);
        accountName = builder.getAccount().getAccountName();
        accountUserId = builder.getAccount().getUserId();
        if (accountUserId_existing == 0) {
            assertTrue("AccountUserId != 1", accountUserId != 1);
        } else {
            assertTrue("AccountUserId != 0", accountUserId != 0);
        }
        builder = null;
        
        MyPreferences.onPreferencesChanged();
        MyContextHolder.initialize(context, this);
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
    }

    private MyAccount.Builder addAccount(MbUser mbUser) throws ConnectionException {
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName("/" + Origin.OriginEnum.PUMPIO.getName(), TriState.TRUE);
        builder.setUserTokenWithSecret("sampleUserTokenFor" + mbUser.userName, "sampleUserSecretFor" + mbUser.userName);
        builder.onCredentialsVerified(mbUser, null);
        assertTrue("Account is persistent", builder.isPersistent());
        MyAccount ma = builder.getAccount();
        assertEquals("Credentials of " + mbUser.userName + " successfully verified", 
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        long userId = builder.getAccount().getUserId();
        assertTrue("Account " + mbUser.userName + " has UserId", userId != 0);
        assertEquals("Account UserOid", ma.getUserOid(), mbUser.oid);
        assertEquals("User in the database for id=" + userId, 
                mbUser.oid,
                MyProvider.idToOid(OidEnum.USER_OID, userId, 0));
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getUserId());
        return builder;
    }
    
    public void testUserAdded() throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
        assertTrue("Account " + accountName + " is persistent", ma != null);
        assertTrue("Account has UserId", ma.getUserId() != 0);
        assertTrue("Account UserOid", ma.getUserOid().equalsIgnoreCase(accountUserOid));
    }
    
    public void testFollowingUser() throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        String messageOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        deleteOldMessage(Origin.OriginEnum.PUMPIO.getId(), messageOid);
        
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
        assertEquals("Account name", ma.getAccountName(), accountName);
        assertEquals("UserId of " + ma.getAccountName(), ma.getUserId(), accountUserId);

        MessageCounters counters = new MessageCounters(ma, context, TimelineTypeEnum.HOME);
        DataInserter di = new DataInserter(counters);
        String username = "somebody@identi.ca";
        String userOid =  "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), userOid);
        somebody.userName = username;
        somebody.actor = accountMbUser;
        somebody.followedByActor = TriState.FALSE;
        somebody.url = "http://identi.ca/somebody";
        di.insertOrUpdateUser(somebody);

        long somebodyId = MyProvider.oidToId(OidEnum.USER_OID, Origin.OriginEnum.PUMPIO.getId(), userOid);
        assertTrue( "User " + username + " added", somebodyId != 0);
        
        Set<Long> followedIds = MyProvider.getIdsOfUsersFollowedBy(ma.getUserId());
        assertFalse( "User " + username + " is not followed", followedIds.contains(somebodyId));

        MbMessage message = MbMessage.fromOriginAndOid(Origin.OriginEnum.PUMPIO.getId(), messageOid);
        message.body = "The test message by Somebody";
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
            sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg._ID + " = ?", new String[] {
                String.valueOf(messageIdOld)
            });
            int deleted = context.getContentResolver().delete(MyDatabase.Msg.CONTENT_URI, sa.selection,
                    sa.selectionArgs);
            assertEquals( "Old message id=" + messageIdOld + " deleted", 1, deleted);
        }
    }
    
    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);

        String username = "anybody@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), "acct:" + username);
        author.userName = username;
        author.actor = accountMbUser;

        username = "firstreader@identi.ca";
        MbUser firstReader = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), "acct:" + username);
        firstReader.userName = username;
        firstReader.actor = accountMbUser;
        
        MbMessage message = MbMessage.fromOriginAndOid(Origin.OriginEnum.PUMPIO.getId(), "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED");
        message.body = "The test message by Anybody from http://pumpity.net";
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
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);

        String username = "example@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), "acct:" + username);
        author.userName = username;
        author.actor = accountMbUser;

        MbMessage message = MbMessage.fromOriginAndOid(Origin.OriginEnum.PUMPIO.getId(), "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123");
        message.body = "The test message by Example from the http://pumpity.net";
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
        deleteOldMessage(Origin.OriginEnum.PUMPIO.getId(), messageOid);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);

        String username = "t131t@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), "acct:" + username);
        author.userName = username;
        author.actor = accountMbUser;
        
        MbMessage message = MbMessage.fromOriginAndOid(Origin.OriginEnum.PUMPIO.getId(), messageOid);
        message.body = "Hello, this is a test Direct message by your namesake from http://pumpity.net";
        message.sentDate = 13312699000L;
        message.via = "AnyOtherClient";
        message.sender = author;
        message.actor = accountMbUser;
        message.recipient = accountMbUser;

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
    
}
