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

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionMock;

public interface ConnectionMockable {

    abstract HttpConnection getHttp();

    @NonNull
    default HttpConnectionMock getHttpMock() {
        return getHttpMock(getHttp());
    }

    @NonNull
    public static HttpConnectionMock getHttpMock(@NonNull MyAccount ma) {
        return getHttpMock(ma.getConnection());
    }

    @NonNull
    public static HttpConnectionMock getHttpMock(@NonNull Connection connection) {
        return getHttpMock(connection.getHttp());
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
