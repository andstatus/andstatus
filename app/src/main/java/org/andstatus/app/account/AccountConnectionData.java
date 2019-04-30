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

package org.andstatus.app.account;

import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionEmpty;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.TriState;

import java.net.URL;

import androidx.annotation.NonNull;

public class AccountConnectionData {
    private boolean isOAuth;
    private URL originUrl;

    private final MyAccount myAccount;
    private final Origin origin;

    private final Class<? extends org.andstatus.app.net.http.HttpConnection> httpConnectionClass;

    public static AccountConnectionData fromOrigin(Origin origin, TriState triStateOAuth) {
        return new AccountConnectionData(MyAccount.EMPTY, origin, triStateOAuth);
    }

    public static AccountConnectionData fromMyAccount(MyAccount myAccount, TriState triStateOAuth) {
        return new AccountConnectionData(myAccount, myAccount.getOrigin(), triStateOAuth);
    }

    private AccountConnectionData(MyAccount myAccount, Origin origin, TriState triStateOAuth) {
        this.myAccount = myAccount;
        this.origin = origin;
        originUrl = origin.getUrl();
        isOAuth = origin.getOriginType().fixIsOAuth(triStateOAuth);
        httpConnectionClass = origin.getOriginType().getHttpConnectionClass(isOAuth());
    }

    @NonNull
    public Actor getAccountActor() {
        return myAccount.getActor();
    }

    @NonNull
    public MyAccount getMyAccount() {
        return myAccount;
    }

    public AccountName getAccountName() {
        return myAccount.getOAccountName();
    }

    public OriginType getOriginType() {
        return origin.getOriginType();
    }

    public Origin getOrigin() {
        return origin;
    }

    public boolean isSsl() {
        return origin.isSsl();
    }

    public boolean isOAuth() {
        return isOAuth;
    }

    public URL getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(URL urlIn) {
        this.originUrl = urlIn;
    }

    public AccountDataReader getDataReader() {
        return myAccount.accountData;
    }

    public HttpConnection newHttpConnection() {
        HttpConnection http = origin.myContext.getHttpConnectionMock();
        if (http != null) return http;
        try {
            return httpConnectionClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            return HttpConnectionEmpty.EMPTY;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return myAccount.isEmpty()
            ? (origin.hasHost()
                ? origin.getHost()
                : originUrl.toString())
            : myAccount.getAccountName();
    }
}
