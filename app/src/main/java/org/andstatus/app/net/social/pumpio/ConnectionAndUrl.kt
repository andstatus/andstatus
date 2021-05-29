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
package org.andstatus.app.net.social.pumpio

import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UrlUtils

internal class ConnectionAndUrl(val apiRoutine: ApiRoutineEnum, val uri: Uri, val httpConnection: HttpConnection) {
    fun withUri(newUri: Uri): ConnectionAndUrl {
        return ConnectionAndUrl(apiRoutine, newUri, httpConnection)
    }

    fun newRequest(): HttpRequest {
        return HttpRequest.of(apiRoutine, uri)
    }

    fun execute(request: HttpRequest): Try<HttpReadResult> {
        return httpConnection.execute(request)
    }

    companion object {
        fun fromActor(connection: ConnectionPumpio, apiRoutine: ApiRoutineEnum, actor: Actor): Try<ConnectionAndUrl> {
            val endpoint = actor.getEndpoint(ActorEndpointType.from(apiRoutine))
            val uri: Uri
            val host: String?
            if (endpoint.isPresent) {
                uri = endpoint.get()
                host = uri.host
            } else {
                val username = actor.getUsername()
                if (username.isEmpty()) {
                    return Try.failure(ConnectionException(StatusCode.BAD_REQUEST, "$apiRoutine: username is required"))
                }
                uri = connection.tryApiPath(Actor.EMPTY, apiRoutine)
                        .map { u: Uri -> UriUtils.map(u) { s: String? -> s?.replace("%username%", username) } }
                        .getOrElse(Uri.EMPTY)
                host = actor.getConnectionHost()
            }
            var httpConnection =  connection.http
            if (host.isNullOrEmpty()) {
                return Try.failure(ConnectionException(StatusCode.BAD_REQUEST, apiRoutine.toString() +
                        ": host is empty for " + actor))
            } else if ( connection.http.data.originUrl == null ||
                    host.compareTo( connection.http.data.originUrl?.host ?: "", ignoreCase = true) != 0) {
                MyLog.v(connection) { "Requesting data from the host: $host" }
                val connectionData1 =  connection.http.data.copy()
                connectionData1.oauthClientKeys = null
                connectionData1.originUrl = UrlUtils.buildUrl(host, connectionData1.isSsl())
                httpConnection =  connection.http.getNewInstance()
                httpConnection.setHttpConnectionData(connectionData1)
            }
            if (!httpConnection.data.areOAuthClientKeysPresent()) {
                httpConnection.registerClient()
                if (!httpConnection.credentialsPresent) {
                    return Try.failure(ConnectionException.fromStatusCodeAndHost(
                            StatusCode.NO_CREDENTIALS_FOR_HOST,
                            "No credentials", httpConnection.data.originUrl))
                }
            }
            return Try.success(ConnectionAndUrl(apiRoutine, uri, httpConnection))
        }
    }
}
