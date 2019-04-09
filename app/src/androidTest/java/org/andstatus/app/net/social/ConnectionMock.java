/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import org.andstatus.app.account.AccountConnectionData;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionMock;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import static org.andstatus.app.context.DemoData.demoData;

public class ConnectionMock {
    public final Connection connection;

    public static ConnectionMock newFor(String accountName) {
        return newFor(demoData.getMyAccount(accountName));
    }

    public static ConnectionMock newFor(MyAccount myAccount) {
        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        ConnectionMock mock = new ConnectionMock(myAccount.setConnection());
        TestSuite.setHttpConnectionMockClass(null);
        return mock;
    }

    private ConnectionMock(Connection connection) {
        this.connection = connection;
    }

    public ConnectionMock withException(ConnectionException e) {
        getHttpMock().setException(e);
        return this;
    }

    public void addResponse(@RawRes int responseResourceId) throws IOException {
        getHttpMock().addResponse(responseResourceId);
    }

    public AccountConnectionData getData() {
        return connection.getData();
    }

    public HttpConnection getHttp() {
        return connection.getHttp();
    }

    @NonNull
    public HttpConnectionMock getHttpMock() {
        return getHttpMock(getHttp());
    }

    @NonNull
    public static HttpConnectionMock getHttpMock(HttpConnection http) {
        if (http != null && HttpConnectionMock.class.isAssignableFrom(http.getClass())) {
            return (HttpConnectionMock) http;
        }
        if (http == null) {
            throw new IllegalStateException("http is null");
        }
        MyContextHolder.get().getHttpConnectionMock();
        throw new IllegalStateException("http is " + http.getClass().getName() + ", " + MyContextHolder.get().toString());
    }

}
