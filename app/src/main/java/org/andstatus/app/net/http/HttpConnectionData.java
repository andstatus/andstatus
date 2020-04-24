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

import org.andstatus.app.account.AccountConnectionData;
import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.TriState;

import java.net.URL;
import java.util.Optional;

import static org.andstatus.app.data.MyContentType.APPLICATION_JSON;

public class HttpConnectionData {
    public static final HttpConnectionData EMPTY = new HttpConnectionData(AccountName.getEmpty());
    private final AccountName accountName;
    public URL originUrl;
    URL urlForUserToken;
    AccountDataReader dataReader;

    public OAuthClientKeys oauthClientKeys;

    private HttpConnectionData(AccountName accountName) {
        this.accountName = accountName;
    }

    public static HttpConnectionData fromConnectionData(AccountConnectionData oConnectionData) {
        HttpConnectionData data = new HttpConnectionData(oConnectionData.getAccountName());
        data.originUrl = oConnectionData.getOriginUrl();
        data.urlForUserToken = oConnectionData.getOriginUrl();
        data.dataReader = oConnectionData.getDataReader();
        return data;
    }

    public HttpConnectionData copy() {
        HttpConnectionData data = new HttpConnectionData(accountName);
        data.originUrl = originUrl;
        data.urlForUserToken = urlForUserToken;
        data.dataReader = dataReader;
        data.oauthClientKeys = oauthClientKeys;
        return data;
    }

    public AccountName getAccountName() {
        return accountName;
    }

    public boolean areOAuthClientKeysPresent() {
        return oauthClientKeys != null && oauthClientKeys.areKeysPresent();
    }

    @Override
    public String toString() {
        return "HttpConnectionData {" + accountName + ", isSsl:" + isSsl()
                + ", sslMode:" + getSslMode()
                + (getUseLegacyHttpProtocol() != TriState.UNKNOWN ?
                        ", HTTP:" + (getUseLegacyHttpProtocol() == TriState.TRUE ?  "legacy" : "latest") : "")
                + ", basicPath:" + getBasicPath()
                + ", oauthPath:" + getOauthPath()
                + ", originUrl:" + originUrl + ", hostForUserToken:" + urlForUserToken + ", dataReader:"
                + dataReader + ", oauthClientKeys:" + oauthClientKeys + "}";
    }

    public String getLogName(ApiRoutineEnum apiRoutine) {
        return accountName.getLogName() + "-" + apiRoutine.name().toLowerCase();
    }

    public OriginType getOriginType() {
        return accountName.getOrigin().getOriginType();
    }

    public String getBasicPath() {
        return getOriginType().getBasicPath();
    }

    public String getOauthPath() {
        return getOriginType().getOauthPath();
    }

    public boolean isSsl() {
        return accountName.getOrigin().isSsl();
    }

    public TriState getUseLegacyHttpProtocol() {
        return accountName.getOrigin().useLegacyHttpProtocol();
    }

    public SslModeEnum getSslMode() {
        return accountName.getOrigin().getSslMode();
    }

    public String jsonContentType(ApiRoutineEnum apiRoutine) {
        return apiRoutine.isOriginApi()
                ? getOriginType().getContentType().orElse(APPLICATION_JSON)
                : APPLICATION_JSON;
    }

    public Optional<String> optOriginContentType() {
        return getOriginType().getContentType();
    }

    public MyContext getMyContext() {
        return accountName.getOrigin().myContext;
    }
}
