/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.origin;

import android.text.TextUtils;

import org.andstatus.app.data.MyPreferences;


/**
 * CLient Keys, obtained dynamically.
 * @author yvolk
 */
public class OAuthClientKeysDynamic implements OAuthClientKeysStrategy {
    private static String KEY_OAUTH_CLIENT_KEY = "oauth_client_key";
    private static String KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret";

    long originId = 0;
    String consumerKey = "";
    String consumerSecret = "";

    @Override
    public void setOrigin(long originId_in) {
        originId = originId_in;
        consumerKey = MyPreferences.getDefaultSharedPreferences().getString(KEY_OAUTH_CLIENT_KEY + originId, "");
        consumerSecret = MyPreferences.getDefaultSharedPreferences().getString(KEY_OAUTH_CLIENT_SECRET + originId, "");
    }

    @Override
    public String getConsumerKey() {
        return consumerKey;
    }
    @Override
    public String getConsumerSecret() {
        return consumerSecret;
    }
    
    @Override
    public void setConsumerKeyAndSecret(String consumerKey_in, String consumerSecret_in) {
        if (TextUtils.isEmpty(consumerKey_in) || TextUtils.isEmpty(consumerSecret_in)) {
            consumerKey = "";
            consumerSecret = "";
            MyPreferences.getDefaultSharedPreferences().edit()
            .remove(KEY_OAUTH_CLIENT_KEY + originId)
            .remove(KEY_OAUTH_CLIENT_SECRET + originId)
            .commit();
        } else {
            consumerKey = consumerKey_in;
            consumerSecret = consumerSecret_in;
            MyPreferences.getDefaultSharedPreferences().edit()
            .putString(KEY_OAUTH_CLIENT_KEY + originId, consumerKey_in)
            .putString(KEY_OAUTH_CLIENT_SECRET + originId, consumerSecret_in)
            .commit();
        }
    }
}
