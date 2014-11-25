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

import org.andstatus.app.origin.OriginType;

/**
 * Keys of the "AndStatus-OpenSource" application.
 * @author yvolk@yurivolkov.com
 */
public class OAuthClientKeysOpenSource implements OAuthClientKeysStrategy {
    private OriginType originType = OriginType.UNKNOWN;

    @Override
    public void initialize(HttpConnectionData connectionData) {
        originType = connectionData.originType;
    }

    @Override
    public String getConsumerKey() {
        if(originType == OriginType.TWITTER) {
            return "XPHj81OgjphGlN6Jb55Kmg";
        } else {
            return "";
        }
    }
    
    @Override
    public String getConsumerSecret() {
        if(originType == OriginType.TWITTER) {  
            return "o2E5AYoDQhZf9qT7ctHLGihpq2ibc5bC4iFAOHURxw";
        } else {
            return "";
        }
    }

    @Override
    public void setConsumerKeyAndSecret(String consumerKey, String consumerSecret) {
        // Nothing to do
    }

    @Override
    public String toString() {
        return OAuthClientKeysOpenSource.class.getSimpleName();
    }
}
