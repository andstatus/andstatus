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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.ConnectionEmpty;
import org.andstatus.app.net.HttpConnection;
import org.andstatus.app.net.HttpConnectionEmpty;
import org.andstatus.app.util.TriState;

import java.net.URL;

public class OriginConnectionData {
    private OriginType originType;
    private long originId = 0;
    private boolean isSsl = true;
    private boolean isOAuth = true;
    private URL originUrl = null;
    private String basicPath = "";
    private String oauthPath = "oauth";
    
    private String accountUsername = "";
    private String accountUserOid = "";
    private AccountDataReader dataReader = null;
    
    private Class<? extends org.andstatus.app.net.Connection> connectionClass = ConnectionEmpty.class;
    private Class<? extends org.andstatus.app.net.HttpConnection> httpConnectionClass = HttpConnectionEmpty.class;
    
    private OriginConnectionData() {
    }
        
    protected static OriginConnectionData fromOrigin(Origin origin, TriState triStateOAuth) {
        OriginConnectionData connectionData = new OriginConnectionData();
        connectionData.originUrl = origin.getUrl();
        connectionData.basicPath = origin.getOriginType().basicPath;
        connectionData.oauthPath = origin.getOriginType().oauthPath;
        connectionData.isSsl = origin.isSsl();
        connectionData.originType = origin.getOriginType();
        connectionData.originId = origin.getId();
        connectionData.isOAuth = origin.getOriginType().fixIsOAuth(triStateOAuth);
        connectionData.connectionClass = origin.getOriginType().getConnectionClass();
        connectionData.httpConnectionClass = origin.getOriginType()
                .getHttpConnectionClass(connectionData.isOAuth());
        return connectionData;
    }

    public OriginType getOriginType() {
        return originType;
    }

    public long getOriginId() {
        return originId;
    }

    public boolean isSsl() {
        return isSsl;
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

    public String getBasicPath() {
        return basicPath;
    }

    public String getOauthPath() {
        return oauthPath;
    }

    public String getAccountUsername() {
        return accountUsername;
    }

    public void setAccountUsername(String accountUsername) {
        this.accountUsername = accountUsername;
    }

    public String getAccountUserOid() {
        return accountUserOid;
    }

    public void setAccountUserOid(String accountUserOid) {
        this.accountUserOid = accountUserOid;
    }

    public AccountDataReader getDataReader() {
        return dataReader;
    }

    public void setDataReader(AccountDataReader dataReader) {
        this.dataReader = dataReader;
    }

    public Class<? extends org.andstatus.app.net.Connection> getConnectionClass() {
        return connectionClass;
    }

    public HttpConnection newHttpConnection() throws InstantiationException, IllegalAccessException {
        HttpConnection http = MyContextHolder.get().getHttpConnectionMock();
        if (http == null) {
            http = httpConnectionClass.newInstance();
        }
        return http;
    }

    public void setHttpConnectionClass(Class<? extends org.andstatus.app.net.HttpConnection> httpConnectionClass) {
        this.httpConnectionClass = httpConnectionClass;
    }
}
