/**
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.util.MyLog;

/**
 * These are the keys for the AndStatus application (a "Client" of the Microblogging system)
 * Keys are per Microblogging System (i.e. per the "Origin")
 * 
 * You may use this application:
 * 1. With application OAuth keys provided in the public repository and stored in the {@link OAuthClientKeysOpenSource} class
 * 2. With keys of your own (Twitter etc.) application.
 * 
 * Instructions:
 * 1. Leave everything as is.
 *    Please read this information about possible problems with Twitter: 
 *    <a href="http://blog.nelhage.com/2010/09/dear-twitter/">http://blog.nelhage.com/2010/09/dear-twitter/</a>.
 * 2. Create new class in this package with this name: {@link OAuthClientKeys#SECRET_CLASS_NAME}
 *    as a copy of existing {@link OAuthClientKeysOpenSource} class 
 *    with Your application's  Consumer Key and Consumer Secret
 *    
 *    For more information please read 
 *    <a href="https://github.com/andstatus/andstatus/wiki/Developerfaq">Developer FAQ</a>.
 **/
public class OAuthClientKeys {
    private static final String TAG = OAuthClientKeys.class.getSimpleName();

    // Strategy pattern, see http://en.wikipedia.org/wiki/Strategy_pattern
    private OAuthClientKeysStrategy strategy = null;
    private static final String SECRET_CLASS_NAME = OAuthClientKeysOpenSource.class.getPackage().getName() + "." + "OAuthClientKeysSecret";
    private static boolean noSecretClass = false;
    
    private OAuthClientKeys() {
    }

    @NonNull
    public static OAuthClientKeys fromConnectionData(HttpConnectionData connectionData) {
        OAuthClientKeys keys = new OAuthClientKeys();
        if (!noSecretClass) {
            // Try to load the application's secret keys first
            try {
                @SuppressWarnings("rawtypes")
                Class cls = Class.forName(SECRET_CLASS_NAME);
                keys.strategy = (OAuthClientKeysStrategy) cls.newInstance();
            } catch (Exception e) {
                MyLog.v(TAG, "Class " + SECRET_CLASS_NAME + " was not loaded", e);
                noSecretClass = true;
            }
        }
        if (keys.strategy == null) {
            // Load keys published in the public source code repository
            keys.strategy = new OAuthClientKeysOpenSource();
        }
        keys.strategy.initialize(connectionData);
        if (!keys.areKeysPresent()) {
            keys.strategy = null;
        }
        if (keys.strategy == null) {
            keys.strategy = new OAuthClientKeysDynamic();
            keys.strategy.initialize(connectionData);
        }
        MyLog.d(TAG, "Loaded " + keys.strategy.toString() + "; "
                + ( keys.areKeysPresent() ? "Keys present" : "No keys")
                );
        return keys;
    }
    
    public boolean areKeysPresent() {
        return !TextUtils.isEmpty(getConsumerKey()) && !TextUtils.isEmpty(getConsumerSecret());
    }
    
    public String getConsumerKey() {
        return strategy.getConsumerKey();
    }

    public String getConsumerSecret() {
        return strategy.getConsumerSecret();
    }

    public void clear() {
        setConsumerKeyAndSecret("", "");
    }

    public void setConsumerKeyAndSecret(String consumerKey, String consumerSecret) {
        strategy.setConsumerKeyAndSecret(consumerKey, consumerSecret);
        MyLog.d(TAG, "Saved " + strategy.toString() + "; "
                + ( areKeysPresent() ? "Keys present" : "No keys")
                );
    }
}