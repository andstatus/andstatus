/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;
import java.net.URL;

public class OriginMastodon extends Origin {

    @Override
    public String messagePermalink(long messageId) {
        String msgUrl = MyQuery.msgIdToStringColumnValue(MsgTable.URL, messageId);
        try {
            return new URL(msgUrl).toExternalForm();
        } catch (MalformedURLException e) {
            MyLog.d(this, "Malformed URL from '" + msgUrl + "'", e);
        }
        return "";
    }

}
