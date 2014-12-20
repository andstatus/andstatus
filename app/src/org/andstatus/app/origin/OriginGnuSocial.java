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

import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;
import java.net.URL;

class OriginGnuSocial extends Origin {

    @Override
    public String messagePermalink(long messageId) {
        try {
            return  new URL(url, "notice/"
                    + MyProvider.msgIdToStringColumnValue(Msg.MSG_OID, messageId)).toExternalForm();
        } catch (MalformedURLException e) {
            MyLog.d(this, "Malformed URL from '" + url.toExternalForm() + "'", e);
        }
        return "";
    }
}
