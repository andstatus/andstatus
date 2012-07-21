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
package org.andstatus.app.net;

import org.andstatus.app.util.MyLog;

/**
 * You may use this application:
 * 1. With application OAuth keys provided in the public repository and stored in the {@link OAuthKeysOpenSource} class
 * 2. With keys of your own (Twitter etc.) application.
 * 
 * Instructions:
 * 1. Leave everything as is.
 *    Please read this information about possible problems with Twitter: 
 *    <a href="http://blog.nelhage.com/2010/09/dear-twitter/">http://blog.nelhage.com/2010/09/dear-twitter/</a>.
 * 2. Create new class in this package with this name: {@link OAuthKeys#SECRET_CLASS_NAME}
 *    as a copy of existing {@link OAuthKeysOpenSource} class 
 *    with Your application's  Consumer Key and Consumer Secret
 *    
 *    For more information please read 
 *    <a href="https://github.com/andstatus/andstatus/wiki/Developerfaq">Developer FAQ</a>.
 **/
public class OAuthKeys {
    private static final String TAG = OAuthKeys.class.getSimpleName();

    private long originId = 0;
    // Strategy pattern, see http://en.wikipedia.org/wiki/Strategy_pattern
    private OAuthKeysStrategy strategy = null;
    private final static String SECRET_CLASS_NAME = OAuthKeysOpenSource.class.getPackage().getName() + "." + "OAuthKeysSecret";
    
    public OAuthKeys(long originId) {
        this.originId = originId;
        
        //Try to load my secret keys first
        try {
            @SuppressWarnings("rawtypes")
            Class cls = Class.forName(SECRET_CLASS_NAME);
            strategy = (OAuthKeysStrategy) cls.newInstance();
        } catch (Exception e) {
            MyLog.d(TAG, "Class " + SECRET_CLASS_NAME + " was not loaded:" + e.getMessage());
        }
        
        if (strategy == null) {
            // Load keys published in the public repository
            strategy = new OAuthKeysOpenSource();
        }
        MyLog.d(TAG, "Class " + strategy.getClass().getCanonicalName() + " was loaded");
    }
    
    /**
     * Consumer key
     */
    public String getConsumerKey() {
        return strategy.getConsumerKey(originId);
    }
    /**
     * Consumer secret
     */
    public String getConsumerSecret() {
        return strategy.getConsumerSecret(originId);
    }
}