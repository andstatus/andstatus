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

package org.andstatus.app.net.http;

import android.text.TextUtils;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

/**
 * CLient Keys, obtained dynamically for each host and Origin.
 * @author yvolk@yurivolkov.com
 */
public class OAuthClientKeysDynamic implements OAuthClientKeysStrategy {
    private static String KEY_OAUTH_CLIENT_KEY = "oauth_client_key";
    private static String KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret";

    String keySuffix = ""; 
    String keyConsumerKey = "";
    String keyConsumerSecret = "";
    String consumerKey = "";
    String consumerSecret = "";

    @Override
    public void initialize(HttpConnectionData connectionData) {
        if (connectionData.originUrl == null) {
            MyLog.v(this, "OriginUrl is null; " + connectionData.toString());
            return;
        }
        keySuffix = Long.toString(connectionData.originId) + "-" + connectionData.originUrl.getHost(); 
        keyConsumerKey = KEY_OAUTH_CLIENT_KEY + keySuffix;
        keyConsumerSecret = KEY_OAUTH_CLIENT_SECRET + keySuffix;
        consumerKey = MyPreferences.getDefaultSharedPreferences().getString(keyConsumerKey, "");
        consumerSecret = MyPreferences.getDefaultSharedPreferences().getString(keyConsumerSecret, "");
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
    public void setConsumerKeyAndSecret(String consumerKeyIn, String consumerSecretIn) {
        if (TextUtils.isEmpty(consumerKeyIn) || TextUtils.isEmpty(consumerSecretIn)) {
            consumerKey = "";
            consumerSecret = "";
            MyPreferences.getDefaultSharedPreferences().edit()
            .remove(keyConsumerKey)
            .remove(keyConsumerSecret)
            .commit();
        } else {
            consumerKey = consumerKeyIn;
            consumerSecret = consumerSecretIn;
            MyPreferences.getDefaultSharedPreferences().edit()
            .putString(keyConsumerKey, consumerKey)
            .putString(keyConsumerSecret, consumerSecret)
            .commit();
        }
    }

    @Override
    public String toString() {
        return OAuthClientKeysDynamic.class.getSimpleName() + "-" + keySuffix;
    }
}
