package org.andstatus.app.origin;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.andstatus.app.account.MyAccountTest;
import org.andstatus.app.data.MyPreferences;

public class OAuthClientKeysTest extends InstrumentationTestCase {
    private static final String TAG = MyAccountTest.class.getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context targetContext = this.getInstrumentation().getTargetContext();
        if (targetContext == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        MyPreferences.forget();
        MyPreferences.initialize(targetContext, this);
    }

    public void testKeysSave() {
       Origin origin = Origin.fromOriginId(Origin.ORIGIN_ID_PUMPIO);
       OriginConnectionData connectionData = origin.getConnectionData();
       connectionData.host = "identi.ca";
       connectionData.oauthClientKeys = OAuthClientKeys.fromConnectionData(connectionData);
       connectionData.oauthClientKeys.clear();
       assertEquals("Keys are cleared", false, connectionData.oauthClientKeys.areKeysPresent());

       connectionData.oauthClientKeys.setConsumerKeyAndSecret("thisiskey", "secret2348");
       OAuthClientKeys keys2 = OAuthClientKeys.fromConnectionData(connectionData);
       assertEquals("Keys are loaded", true, keys2.areKeysPresent());
       assertEquals("thisiskey", keys2.getConsumerKey());
       assertEquals("secret2348", keys2.getConsumerSecret());
       keys2.clear();

       OAuthClientKeys keys3 = OAuthClientKeys.fromConnectionData(connectionData);
       assertEquals("Keys are cleared", false, keys3.areKeysPresent());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}
