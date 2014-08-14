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
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyProvider;

class OriginPumpio extends Origin {
    @Override
    public int alternativeTermForResourceId(int resId) {
        int resIdOut;
        switch (resId) {
            case R.string.dialog_title_preference_username:
                resIdOut = R.string.dialog_title_preference_username_pumpio;
                break;
            case R.string.title_preference_username:
                resIdOut = R.string.title_preference_username_pumpio;
                break;
            case R.string.summary_preference_username:
                resIdOut = R.string.summary_preference_username_pumpio;
                break;
            default:
                resIdOut = resId;
                break;
        }
        return resIdOut;
    }
    
    @Override
    public String messagePermalink(long messageId) {
        return MyProvider.msgIdToStringColumnValue(Msg.URL, messageId);
    }
    
    @Override
    public boolean isUsernameValidToStartAddingNewAccount(String username, boolean isOAuthUser) {
        return isUsernameValid(username);
    }
}
