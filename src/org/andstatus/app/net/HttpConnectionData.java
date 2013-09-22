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

package org.andstatus.app.net;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.origin.OriginConnectionData;

class HttpConnectionData {
    public long originId;

    public boolean isHttps;
    public String basicPath;
    public String oauthPath;
    public String accountUsername;
    public String host;
    public String hostForUserToken = "";
    public AccountDataReader dataReader = null;

    public OAuthClientKeys oauthClientKeys;

    public boolean areOAuthClientKeysPresent() {
        return (oauthClientKeys != null && oauthClientKeys.areKeysPresent());
    }
    
    static HttpConnectionData fromConnectionData(OriginConnectionData oConnectionData) {
        HttpConnectionData data = new HttpConnectionData();
        data.originId = oConnectionData.originId;
        data.isHttps = oConnectionData.isHttps;
        data.basicPath = oConnectionData.basicPath;
        data.oauthPath = oConnectionData.oauthPath;
        data.accountUsername = oConnectionData.accountUsername;
        data.host = oConnectionData.host;
        data.hostForUserToken = oConnectionData.host;
        data.dataReader = oConnectionData.dataReader;
        return data;
    }

    public HttpConnectionData newCopy() {
        HttpConnectionData data = new HttpConnectionData();
        data.originId = originId;
        data.isHttps = isHttps;
        data.basicPath = basicPath;
        data.oauthPath = oauthPath;
        data.accountUsername = accountUsername;
        data.host = host;
        data.hostForUserToken = hostForUserToken;
        data.dataReader = dataReader;
        return data;
    }
}
