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

package org.andstatus.app.net.social.pumpio;

import android.net.Uri;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;

class ConnectionAndUrl {
    public final Uri uri;
    public final HttpConnection httpConnection;

    public ConnectionAndUrl(Uri uri, HttpConnection httpConnection) {
        this.uri = uri;
        this.httpConnection = httpConnection;
    }

    public static ConnectionAndUrl fromActor(ConnectionPumpio connection, Connection.ApiRoutineEnum apiRoutine, Actor actor) throws ConnectionException {
        String username = actor.getUsername();
        if (StringUtils.isEmpty(username)) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine + ": username is required");
        }
        Uri uri = UriUtils.map(connection.getApiPath(apiRoutine), s -> s.replace("%username%", username));
        HttpConnection httpConnection = connection.getHttp();
        String host = actor.getHost();
        if (StringUtils.isEmpty(host)) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine +
                    ": host is empty for " + actor);
        } else if (connection.getHttp().data.originUrl == null ||
                host.compareToIgnoreCase(connection.getHttp().data.originUrl.getHost()) != 0) {
            MyLog.v(connection, () -> "Requesting data from the host: " + host);
            HttpConnectionData connectionData1 = connection.getHttp().data.copy();
            connectionData1.oauthClientKeys = null;
            connectionData1.originUrl = UrlUtils.buildUrl(host, connectionData1.isSsl());
            httpConnection = connection.getHttp().getNewInstance();
            httpConnection.setHttpConnectionData(connectionData1);
        }
        if (!httpConnection.data.areOAuthClientKeysPresent()) {
            httpConnection.registerClient();
            if (!httpConnection.getCredentialsPresent()) {
                throw ConnectionException.fromStatusCodeAndHost(ConnectionException.StatusCode.NO_CREDENTIALS_FOR_HOST,
                        "No credentials", httpConnection.data.originUrl);
            }
        }
        return new ConnectionAndUrl(uri, httpConnection);
    }
}
