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
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.net.ConnectionTwitter1p1;
import org.andstatus.app.util.TriState;

class OriginTwitter extends Origin {

    protected OriginTwitter() {
        host = "api.twitter.com";
        isOAuthDefault = true;
        canChangeOAuth = false;  // Starting from 2010-09 twitter.com allows OAuth only
        shouldSetNewUsernameManuallyIfOAuth = false;
        shouldSetNewUsernameManuallyNoOAuth = true;
        usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
        shortUrlLength = 23; // TODO: Read from Config
        textLimit = 140;
    }

    /**
     * In order to comply with Twitter's "Developer Display Requirements" 
     *   https://dev.twitter.com/terms/display-requirements
     * @param resId
     * @return Id of alternative (proprietary) term/phrase
     */
    @Override
    public int alternativeTermForResourceId(int resId) {
        int resId_out;
        switch (resId) {
            case R.string.button_create_message:
                resId_out = R.string.button_create_message_twitter;
                break;
            case R.string.menu_item_destroy_reblog:
                resId_out = R.string.menu_item_destroy_reblog_twitter;
                break;
            case R.string.menu_item_reblog:
                resId_out = R.string.menu_item_reblog_twitter;
                break;
            case R.string.message:
                resId_out = R.string.message_twitter;
                break;
            case R.string.reblogged_by:
                resId_out = R.string.reblogged_by_twitter;
                break;
            default:
                resId_out = resId;
        }
        return resId_out;
    }
    
    @Override
    public OriginConnectionData getConnectionData(TriState triState) {
        OriginConnectionData connectionData = super.getConnectionData(triState);
        connectionData.isHttps = false;
        connectionData.basicPath = "1.1";
        connectionData.oauthPath = "oauth";
        connectionData.connectionClass = ConnectionTwitter1p1.class;
        return connectionData;
    }

    @Override
    public String messagePermalink(String userName, long messageId) {
        String url = "https://twitter.com/"
                    + userName 
                    + "/status/"
                    + MyProvider.msgIdToStringColumnValue(Msg.MSG_OID, messageId);
        return url;
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
