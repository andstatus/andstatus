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
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;
import java.net.URL;

class OriginTwitter extends Origin {

    /**
     * In order to comply with Twitter's "Developer Display Requirements" 
     *   https://dev.twitter.com/terms/display-requirements
     * @param resId
     * @return Id of alternative (proprietary) term/phrase
     */
    @Override
    public int alternativeTermForResourceId(int resId) {
        int resIdOut;
        switch (resId) {
            case R.string.button_create_message:
                resIdOut = R.string.button_create_message_twitter;
                break;
            case R.string.menu_item_destroy_reblog:
                resIdOut = R.string.menu_item_destroy_reblog_twitter;
                break;
            case R.string.menu_item_reblog:
                resIdOut = R.string.menu_item_reblog_twitter;
                break;
            case R.string.message:
                resIdOut = R.string.message_twitter;
                break;
            case R.string.reblogged_by:
                resIdOut = R.string.reblogged_by_twitter;
                break;
            default:
                resIdOut = resId;
                break;
        }
        return resIdOut;
    }

    @Override
    public String messagePermalink(long messageId) {
        String userName = MyProvider.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, messageId);
        try {
            return  new URL(url, userName
                    + "/status/"
                    + MyProvider.msgIdToStringColumnValue(Msg.MSG_OID, messageId)).toExternalForm();
        } catch (MalformedURLException e) {
            MyLog.d(this, "Malformed URL from '" + url.toExternalForm() + "'", e);
        }
        return "";
    }
}
