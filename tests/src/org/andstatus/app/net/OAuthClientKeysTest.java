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

package org.andstatus.app.net;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.andstatus.app.account.MyAccountTest;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.OAuthClientKeys;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.Origin.OriginEnum;
import org.andstatus.app.util.TriState;

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
       Origin origin = OriginEnum.PUMPIO.newOrigin();
       HttpConnectionData connectionData = HttpConnectionData.fromConnectionData(origin.getConnectionData(TriState.UNKNOWN));
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
