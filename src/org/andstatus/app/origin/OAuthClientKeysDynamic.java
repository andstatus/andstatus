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

import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * CLient Keys, obtained dynamically.
 * @author yvolk
 */
public class OAuthClientKeysDynamic implements OAuthClientKeysStrategy {
    private static String KEY_OAUTH_CLIENT_KEY = "oauth_client_key";
    private static String KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret";

    SharedPreferences sharedPreferences;
    String consumerKey = "";
    String consumerSecret = "";

    public OAuthClientKeysDynamic(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        consumerKey = sharedPreferences.getString(KEY_OAUTH_CLIENT_KEY, "");
        consumerSecret = sharedPreferences.getString(KEY_OAUTH_CLIENT_SECRET, "");
    }

    @Override
    public void setOrigin(long originId_in) { }

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
            sharedPreferences.edit()
            .remove(KEY_OAUTH_CLIENT_KEY)
            .remove(KEY_OAUTH_CLIENT_SECRET)
            .commit();
        } else {
            consumerKey = consumerKey_in;
            consumerSecret = consumerSecret_in;
            sharedPreferences.edit()
            .putString(KEY_OAUTH_CLIENT_KEY, consumerKey_in)
            .putString(KEY_OAUTH_CLIENT_SECRET, consumerSecret_in)
            .commit();
        }
    }
}
