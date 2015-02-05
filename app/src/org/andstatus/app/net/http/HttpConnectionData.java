/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.http;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;

import java.net.URL;

public class HttpConnectionData implements Cloneable {
    protected OriginType originType;
    protected long originId;
    public boolean isSsl;
    public SslModeEnum sslMode;
    public String basicPath;
    protected String oauthPath;
    protected String accountUsername;
    public URL originUrl;
    protected URL urlForUserToken;
    protected AccountDataReader dataReader;

    public OAuthClientKeys oauthClientKeys;

    public boolean areOAuthClientKeysPresent() {
        return oauthClientKeys != null && oauthClientKeys.areKeysPresent();
    }
    
    private HttpConnectionData() {
        // Empty
    }
    
    public static HttpConnectionData fromConnectionData(OriginConnectionData oConnectionData) {
        HttpConnectionData data = new HttpConnectionData();
        data.originType = oConnectionData.getOriginType();
        data.originId = oConnectionData.getOriginId();
        data.isSsl = oConnectionData.isSsl();
        data.sslMode = oConnectionData.getSslMode();
        data.basicPath = oConnectionData.getBasicPath();
        data.oauthPath = oConnectionData.getOauthPath();
        data.accountUsername = oConnectionData.getAccountUsername();
        data.originUrl = oConnectionData.getOriginUrl();
        data.urlForUserToken = oConnectionData.getOriginUrl();
        data.dataReader = oConnectionData.getDataReader();
        return data;
    }

    @Override
    public HttpConnectionData clone() {
        try {
            return (HttpConnectionData) super.clone();
        } catch (CloneNotSupportedException e) {
            MyLog.e(this, "Clone failed", e);
            return new HttpConnectionData();
        }
    }

    @Override
    public String toString() {
        return "HttpConnectionData {" + originId + ", " + originType + ", isSsl:" + isSsl
                + ", sslMode:" + sslMode
                + ", basicPath:" + basicPath 
                + ", oauthPath:" + oauthPath + ", accountUsername:" + accountUsername
                + ", originUrl:" + originUrl + ", hostForUserToken:" + urlForUserToken + ", dataReader:"
                + dataReader + ", oauthClientKeys:" + oauthClientKeys + "}";
    }
}
