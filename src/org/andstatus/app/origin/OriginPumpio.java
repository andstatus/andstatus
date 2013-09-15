/*
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

import org.andstatus.app.R;
import org.andstatus.app.net.ConnectionPumpio;
import org.andstatus.app.util.TriState;

class OriginPumpio extends Origin {
    protected OriginPumpio() {
        isOAuthDefault = true;  
        canChangeOAuth = false;
        shouldSetNewUsernameManuallyIfOAuth = true;
        shouldSetNewUsernameManuallyNoOAuth = false;
        usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+@[a-zA-Z_0-9/\\.\\-\\(\\)]+";
        maxCharactersInMessage = 5000; // This is not a hard limit, just for convenience.
    }

    @Override
    public int alternativeTermForResourceId(int resId) {
        int resId_out;
        switch (resId) {
            case R.string.summary_preference_username:
                resId_out = R.string.summary_preference_username_pumpio;
                break;
            default:
                resId_out = resId;
        }
        return resId_out;
    }
    
    @Override
    public String messagePermalink(String userName, String messageOid) {
        return messageOid;
    }
    
    @Override
    public boolean isUsernameValidToStartAddingNewAccount(String username, boolean isOAuthUser) {
        return isUsernameValid(username);
    }

    @Override
    public OriginConnectionData getConnectionData(TriState triState) {
        OriginConnectionData connectionData = super.getConnectionData(triState);
        connectionData.isHttps = true;
        connectionData.host = "identi.ca";  // Default host
        connectionData.basicPath = "api";
        connectionData.oauthPath = "oauth";
        connectionData.connectionClass = ConnectionPumpio.class;
        return connectionData;
    }
    
}
