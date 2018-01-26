/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.account;

import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MyAccountTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testNewAccountCreation() {
       createAccountOfOriginType("", OriginType.TWITTER);
       createAccountOfOriginType("testUser1", OriginType.TWITTER);
       createAccountOfOriginType("", OriginType.PUMPIO);
       createAccountOfOriginType("test2User@somepipe.example.com", OriginType.PUMPIO);
       createAccountOfOriginType("PeterPom", OriginType.GNUSOCIAL);
    }
    
    private void createAccountOfOriginType(String userName, OriginType originType) {
        MyContext myContext = MyContextHolder.get();
        String logMsg = "Creating account '" + userName + "' for '" + originType + "'";
        MyLog.v(this, logMsg);
        Origin origin = myContext.persistentOrigins().firstOfType(originType);
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(myContext,
                userName + AccountName.ORIGIN_SEPARATOR + origin.getName(), TriState.UNKNOWN);
        assertEquals(logMsg, origin, builder.getAccount().getOrigin());
        assertEquals(logMsg, userName + AccountName.ORIGIN_SEPARATOR + origin.getName(), builder.getAccount().getAccountName());
    }

    @Test
    public void testUser() {
        MyAccount ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(demoData.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        Actor accountActor = ma.getActor();
        assertFalse("User is partial " + accountActor, accountActor.isPartiallyDefined());
    }

    public static void fixPersistentAccounts(MyContext myContext) {
        for (MyAccount ma : myContext.persistentAccounts().list()) {
            fixAccountByName(myContext, ma.getAccountName());
        }
    }
    
    private static void fixAccountByName(MyContext myContext, String accountName) {
        MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
        assertTrue("Account " + accountName + " is valid", ma.isValid());
        if (ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED ) {
            return;
        }
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(MyContextHolder.get(), accountName,
                TriState.UNKNOWN);
        builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED);
        builder.saveSilently();
    }

}
