package org.andstatus.app.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import org.andstatus.app.MessageCounters;
import org.andstatus.app.MyServiceManager;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;

import java.util.Set;

public class DataInserterTest extends InstrumentationTestCase {
    private Context context;
    private MbUser accountMbUser;
    private String accountUserOid = "acct:t131t@identi.ca";
    private String accountName;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = TestSuite.initialize(this);

        MyServiceManager.setServiceUnavailable();
        
        accountMbUser = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), accountUserOid);
        accountMbUser.userName = "t131t@identi.ca";
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName("/" + Origin.OriginEnum.PUMPIO.getName(), TriState.TRUE);
        builder.setUserTokenWithSecret("sampleUserTokenForT131t", "sampleUserSecretFort131tUser");
        builder.onVerifiedCredentials(accountMbUser, null);
        accountName = builder.getAccount().getAccountName();
        assertTrue("Account is persistent", builder.isPersistent());
        assertTrue("Account has UserId", builder.getAccount().getUserId() != 0);
        assertTrue("Account UserOid", builder.getAccount().getUserOid().equalsIgnoreCase(accountUserOid));
        builder = null;
        
        MyPreferences.onPreferencesChanged();
        MyPreferences.initialize(context, this);
    }
    
    public void testUserAdded() throws ConnectionException {
        MyAccount ma = MyAccount.fromAccountName(accountName);
        assertTrue("Account is persistent", ma != null);
        assertTrue("Account has UserId", ma.getUserId() != 0);
        assertTrue("Account UserOid", ma.getUserOid().equalsIgnoreCase(accountUserOid));
    }
    
    public void testFollowingUser() throws ConnectionException {
        MyAccount ma = MyAccount.fromAccountName(accountName);

        MessageCounters counters = new MessageCounters(ma, context, TimelineTypeEnum.HOME);
        DataInserter di = new DataInserter(counters);
        String username = "somebody@identi.ca";
        String userOid =  "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), userOid);
        somebody.userName = username;
        somebody.actor = accountMbUser;
        somebody.followedByActor = TriState.FALSE;
        di.insertOrUpdateUser(somebody);

        long somebodyId = MyProvider.oidToId(OidEnum.USER_OID, Origin.OriginEnum.PUMPIO.getId(), userOid);
        assertTrue( "User " + username + " added", somebodyId != 0);
        
        Set<Long> followedIds = MyProvider.getIdsOfUsersFollowedBy(ma.getUserId());
        assertFalse( "User " + username + " is not followed", followedIds.contains(somebodyId));

        MbMessage message = MbMessage.fromOriginAndOid(Origin.OriginEnum.PUMPIO.getId(), "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB");
        message.body = "The test message by Somebody";
        message.sentDate = 13312696000L;
        message.via = "MyCoolClient";
        message.sender = somebody;
        message.actor = accountMbUser;
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        
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

    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        MyAccount ma = MyAccount.fromAccountName(accountName);

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
        MyAccount ma = MyAccount.fromAccountName(accountName);

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
        MyAccount ma = MyAccount.fromAccountName(accountName);

        String username = "t131t@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), "acct:" + username);
        author.userName = username;
        author.actor = accountMbUser;
        
        MbMessage message = MbMessage.fromOriginAndOid(Origin.OriginEnum.PUMPIO.getId(), "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ");
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
            MsgOfUser.DIRECTED,
            MyDatabase.User.LINKED_USER_ID
            };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertTrue("Message is direct", cursor.getInt(0) == 1);
        assertTrue("Message is directed to AccountUser", cursor.getLong(1) == ma.getUserId());
        cursor.close();
    }
    
}
