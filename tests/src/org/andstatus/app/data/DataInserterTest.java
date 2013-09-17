package org.andstatus.app.data;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.TriState;

import java.util.Set;

public class DataInserterTest extends InstrumentationTestCase {
    Context context;
    MbUser accountMbUser;
    String accountName;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = TestSuite.initialize(this);
    }

    public void testFollowingUser() throws ConnectionException {
        MbUser accountMbUser = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), "acct:t131t@identi.ca");
        accountMbUser.userName = "t131t@identi.ca";
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName("/" + Origin.OriginEnum.PUMPIO.getName(), TriState.TRUE);
        builder.onVerifiedCredentials(accountMbUser, null);
        accountName = builder.getAccount().getAccountName();
        assertTrue("Account has UserId", builder.getAccount().getUserId() != 0);
        assertTrue("Account is persistent", builder.isPersistent());
        assertTrue("Account has UserOid", !TextUtils.isEmpty(builder.getAccount().getUserOid()));
        builder = null;
        
        MyPreferences.onPreferencesChanged();
        MyPreferences.initialize(context, this);

        MyAccount ma = MyAccount.fromAccountName(accountName);
        assertTrue("Account has UserId", ma.getUserId() != 0);
        assertTrue("Account is persistent", ma != null);
        assertTrue("Account has UserOid", !TextUtils.isEmpty(ma.getUserOid()));

        DataInserter di = new DataInserter(ma, context, TimelineTypeEnum.HOME);
        String username = "somebody@identi.ca";
        String userOid =  "acct:" + username;
        MbUser mbUser = MbUser.fromOriginAndUserOid(Origin.OriginEnum.PUMPIO.getId(), userOid);
        mbUser.userName = username;
        mbUser.reader = accountMbUser;
        mbUser.followedByReader = TriState.TRUE;
        di.insertOrUpdateUser(mbUser);

        long userId = MyProvider.oidToId(OidEnum.USER_OID, Origin.OriginEnum.PUMPIO.getId(), userOid);
        assertTrue( "User " + username + " added", userId != 0);
        
        Set<Long> followedIds = MyProvider.getIdsOfUsersFollowedBy(ma.getUserId());
        assertTrue( "User " + username + ", id=" + userId + " is followed", followedIds.contains(userId));

        mbUser.followedByReader = TriState.FALSE;
        di.insertOrUpdateUser(mbUser);

        followedIds = MyProvider.getIdsOfUsersFollowedBy(ma.getUserId());
        assertFalse( "User " + username + " is not followed", followedIds.contains(userId));
    }
    
}
