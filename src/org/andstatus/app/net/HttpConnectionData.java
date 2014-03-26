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

package org.andstatus.app.net;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;

class HttpConnectionData implements Cloneable {
    protected OriginType originType;

    protected boolean isSsl;
    protected String basicPath;
    protected String oauthPath;
    protected String accountUsername;
    protected String host;
    protected String hostForUserToken = "";
    protected AccountDataReader dataReader = null;

    protected OAuthClientKeys oauthClientKeys;

    protected boolean areOAuthClientKeysPresent() {
        return oauthClientKeys != null && oauthClientKeys.areKeysPresent();
    }
    
    static HttpConnectionData fromConnectionData(OriginConnectionData oConnectionData) {
        HttpConnectionData data = new HttpConnectionData();
        data.originType = oConnectionData.getOriginType();
        data.isSsl = oConnectionData.isSsl();
        data.basicPath = oConnectionData.getBasicPath();
        data.oauthPath = oConnectionData.getOauthPath();
        data.accountUsername = oConnectionData.getAccountUsername();
        data.host = oConnectionData.getHost();
        data.hostForUserToken = oConnectionData.getHost();
        data.dataReader = oConnectionData.getDataReader();
        return data;
    }

    @Override
    protected HttpConnectionData clone() {
        try {
            return (HttpConnectionData) super.clone();
        } catch (CloneNotSupportedException e) {
            MyLog.e(this, "Clone failed", e);
            return new HttpConnectionData();
        }
    }

    @Override
    public String toString() {
        return "HttpConnectionData {" + originType + ", isSsl:" + isSsl + ", basicPath:"
                + basicPath + ", oauthPath:" + oauthPath + ", accountUsername:" + accountUsername
                + ", host:" + host + ", hostForUserToken:" + hostForUserToken + ", dataReader:"
                + dataReader + ", oauthClientKeys:" + oauthClientKeys + "}";
    }
}
