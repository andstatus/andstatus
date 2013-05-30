/**
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

package org.andstatus.app.net;

import android.net.Uri;

import org.andstatus.app.account.AccountDataReader;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;

/**
 * Specific implementation of the {@link ApiEnum.STATUSNET_TWITTER}
 * @author yvolk
 */
public class ConnectionBasicAuthStatusNet extends ConnectionBasicAuth {

    public ConnectionBasicAuthStatusNet(AccountDataReader dr, ApiEnum api, String apiBaseUrl) {
        super(dr, api, apiBaseUrl);
    }

    @Override
    public JSONArray getFriendsIds(String userId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiUrl(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
        HttpGet get = new HttpGet(builder.build().toString());
        return getRequestAsArray(get);
    }
}
