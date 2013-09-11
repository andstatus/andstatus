package org.andstatus.app.account;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.TriState;

public class MyAccountTest  extends InstrumentationTestCase {
    private static final String TAG = MyAccountTest.class.getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context targetContext = this.getInstrumentation().getTargetContext();
        if (targetContext == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        MyPreferences.initialize(targetContext, this);
    }

    public void testNewAccountCreation() {
       MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(AccountName.ORIGIN_SEPARATOR + Origin.ORIGIN_NAME_TWITTER, TriState.UNKNOWN);
       assertEquals("Creating account for '" + Origin.ORIGIN_NAME_TWITTER + "'", Origin.ORIGIN_ID_TWITTER, builder.getAccount().getOriginId());
       assertEquals("Creating account for '" + Origin.ORIGIN_NAME_TWITTER + "'", "/twitter", builder.getAccount().getAccountName());
       builder = MyAccount.Builder.newOrExistingFromAccountName(AccountName.ORIGIN_SEPARATOR + Origin.ORIGIN_NAME_PUMPIO, TriState.UNKNOWN);
       assertEquals("Creating account for '" + Origin.ORIGIN_NAME_PUMPIO + "'", Origin.ORIGIN_ID_PUMPIO, builder.getAccount().getOriginId());
       assertEquals("Creating account for '" + Origin.ORIGIN_NAME_PUMPIO + "'", "/pump.io", builder.getAccount().getAccountName());
    }
}
