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

import org.andstatus.app.net.ConnectionTwitterStatusNet;
import org.andstatus.app.util.TriState;

class OriginStatusNet extends Origin {

    protected OriginStatusNet() {
        isOAuthDefault = false;
        canChangeOAuth = false; 
        shouldSetNewUsernameManuallyIfOAuth = false;
        shouldSetNewUsernameManuallyNoOAuth = true;
        canSetHostOfOrigin = true;
        usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
        maxCharactersInMessage = CHARS_MAX_DEFAULT;
    }

    @Override
    public OriginConnectionData getConnectionData(TriState triState) {
        OriginConnectionData connectionData = super.getConnectionData(triState);
        connectionData.isHttps = false;
        connectionData.basicPath = "api";
        connectionData.oauthPath = "api";
        connectionData.connectionClass = ConnectionTwitterStatusNet.class;
        return connectionData;
    }

    @Override
    public boolean isUsernameValidToStartAddingNewAccount(String username, boolean isOAuthUser) {
        if (isOAuthUser) {
            return true;  // Name doesn't matter at this step
        } else {
            return isUsernameValid(username);
        }
    }
}
