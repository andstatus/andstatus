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

package org.andstatus.app.origin;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.TriState;

import java.net.URL;

import androidx.annotation.NonNull;

public class OriginConnectionData {
    private boolean isOAuth;
    private URL originUrl;

    private final MyAccount myAccount;
    private AccountDataReader dataReader;
    
    private final Class<? extends org.andstatus.app.net.http.HttpConnection> httpConnectionClass;

    public static OriginConnectionData fromMyAccount(MyAccount myAccount, TriState triStateOAuth) {
        return new OriginConnectionData(myAccount, triStateOAuth);
    }

    private OriginConnectionData(MyAccount myAccount, TriState triStateOAuth) {
        this.myAccount = myAccount;
        originUrl = myAccount.getOrigin().getUrl();
        isOAuth = myAccount.getOrigin().getOriginType().fixIsOAuth(triStateOAuth);
        httpConnectionClass = myAccount.getOrigin().getOriginType().getHttpConnectionClass(isOAuth());
        dataReader = new AccountDataReaderEmpty();
    }

    @NonNull
    public Actor getAccountActor() {
        return myAccount.getActor();
    }

    public AccountName getAccountName() {
        return myAccount.getOAccountName();
    }

    public OriginType getOriginType() {
        return myAccount.getOrigin().getOriginType();
    }

    public Origin getOrigin() {
        return myAccount.getOrigin();
    }

    public boolean isSsl() {
        return myAccount.getOrigin().isSsl();
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

    @NonNull
    public Connection newConnection() throws ConnectionException {
        Connection connection;
        try {
            connection = myAccount.getOrigin().getOriginType().getConnectionClass().newInstance();
            connection.enrichConnectionData(this);
            connection.setAccountData(this);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ConnectionException(myAccount.getOrigin().toString(), e);
        }
        return connection;
    }

    public AccountDataReader getDataReader() {
        return dataReader;
    }

    public void setDataReader(AccountDataReader dataReader) {
        this.dataReader = dataReader;
    }

    public HttpConnection newHttpConnection(String logMsg) throws ConnectionException {
        HttpConnection http = MyContextHolder.get().getHttpConnectionMock();
        if (http == null) {
            try {
                http = httpConnectionClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new ConnectionException(logMsg, e);
            }
        }
        return http;
    }
}
