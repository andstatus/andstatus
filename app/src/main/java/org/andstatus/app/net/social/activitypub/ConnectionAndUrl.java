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
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONObject;

import java.util.Optional;

import io.vavr.control.Try;

class ConnectionAndUrl {
    public final Uri uri;
    final HttpConnection httpConnection;

    private ConnectionAndUrl(Uri uri, HttpConnection httpConnection) {
        this.uri = uri;
        this.httpConnection = httpConnection;
    }

    ConnectionAndUrl withUri(Uri newUri) {
        return new ConnectionAndUrl(newUri, httpConnection);
    }

    static Try<ConnectionAndUrl> fromUriActor(Uri uri, ConnectionActivityPub connection,
                                              Connection.ApiRoutineEnum apiRoutine, Actor actor) {
        return getConnection(connection, apiRoutine, actor).map(conu -> new ConnectionAndUrl(uri, conu));
    }

    static Try<ConnectionAndUrl> fromActor(ConnectionActivityPub connection, Connection.ApiRoutineEnum apiRoutine, Actor actor) {
        final Optional<Uri> endpoint = actor.getEndpoint(ActorEndpointType.from(apiRoutine));
        if (!endpoint.isPresent()) {
            return Try.failure(new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine +
                    ": endpoint is empty for " + actor));
        }
        return getConnection(connection, apiRoutine, actor).map(conu -> new ConnectionAndUrl(endpoint.get(), conu));
    }

    private static Try<HttpConnection> getConnection(ConnectionActivityPub connection, Connection.ApiRoutineEnum apiRoutine,
                                                Actor actor) {
        HttpConnection httpConnection = connection.getHttp();
        String host = actor.getConnectionHost();
        if (StringUtil.isEmpty(host)) {
            return Try.failure(new ConnectionException(ConnectionException.StatusCode.BAD_REQUEST, apiRoutine +
                    ": host is empty for " + actor));
        } else if (connection.getHttp().data.originUrl == null || host.compareToIgnoreCase(
                connection.getHttp().data.originUrl.getHost()) != 0) {
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
                return Try.failure(ConnectionException.fromStatusCodeAndHost(ConnectionException.StatusCode.NO_CREDENTIALS_FOR_HOST,
                        "No credentials", httpConnection.data.originUrl));
            }
        }
        return Try.success(httpConnection);
    }

    Try<JSONObject> getRequest() {
        return httpConnection.getRequest(uri);
    }
}
