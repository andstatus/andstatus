/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social.activitypub;

import android.net.Uri;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UrlUtils;

import java.util.Optional;

class ConnectionAndUrl {
    public final Uri uri;
    public final HttpConnection httpConnection;

    private ConnectionAndUrl(Uri uri, HttpConnection httpConnection) {
        this.uri = uri;
        this.httpConnection = httpConnection;
    }

    public ConnectionAndUrl withUri(Uri newUri) {
        return new ConnectionAndUrl(newUri, httpConnection);
    }

    static ConnectionAndUrl fromUriActor(Uri uri, ConnectionActivityPub connection, Connection.ApiRoutineEnum apiRoutine, Actor actor) throws ConnectionException {
        return new ConnectionAndUrl(uri, getConnection(connection, apiRoutine, actor));
    }

    static ConnectionAndUrl fromActor(ConnectionActivityPub connection, Connection.ApiRoutineEnum apiRoutine, Actor actor) throws ConnectionException {
        final Optional<Uri> endpoint = actor.getEndpoint(ActorEndpointType.from(apiRoutine));
        if (!endpoint.isPresent()) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine + ": endpoint is empty for " + actor);
        }
        return new ConnectionAndUrl(endpoint.get(), getConnection(connection, apiRoutine, actor));
    }

    private static HttpConnection getConnection(ConnectionActivityPub connection, Connection.ApiRoutineEnum apiRoutine, Actor actor) throws ConnectionException {
        HttpConnection httpConnection = connection.getHttp();
        String host = actor.getHost();
        if (StringUtils.isEmpty(host)) {
            throw new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine + ": host is empty for " + actor);
        } else if (connection.getHttp().data.originUrl == null || host.compareToIgnoreCase(connection.getHttp().data.originUrl.getHost()) != 0) {
            MyLog.v(connection, () -> "Requesting data from the host: " + host);
            HttpConnectionData connectionData1 = connection.getHttp().data.copy();
            connectionData1.oauthClientKeys = null;
            connectionData1.originUrl = UrlUtils.buildUrl(host, connectionData1.isSsl());
            httpConnection = connection.getHttp().getNewInstance();
            httpConnection.setConnectionData(connectionData1);
        }
        if (!httpConnection.data.areOAuthClientKeysPresent()) {
            httpConnection.registerClient();
            if (!httpConnection.getCredentialsPresent()) {
                throw ConnectionException.fromStatusCodeAndHost(ConnectionException.StatusCode.NO_CREDENTIALS_FOR_HOST,
                        "No credentials", httpConnection.data.originUrl);
            }
        }
        return httpConnection;
    }
}
