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
package org.andstatus.app.net.social.activitypub

import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.http.StatusCode
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType.Companion.toActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.ConnectionFactory
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UrlUtils

internal class ConnectionAndUrl private constructor(val apiRoutine: ApiRoutineEnum, val uri: Uri, val httpConnection: HttpConnection) {
    var syncYounger = true

    fun withUri(newUri: Uri): ConnectionAndUrl {
        return ConnectionAndUrl(apiRoutine, newUri, httpConnection)
                .withSyncDirection(syncYounger)
    }

    fun newRequest(): HttpRequest {
        return HttpRequest.of(apiRoutine, uri)
    }

    fun execute(request: HttpRequest): Try<HttpReadResult> {
        return httpConnection.execute(request)
    }

    fun withSyncDirection(syncYounger: Boolean): ConnectionAndUrl {
        this.syncYounger = syncYounger
        return this
    }

    companion object {
        fun fromUriActor(uri: Uri, connection: ConnectionActivityPub,
                         apiRoutine: ApiRoutineEnum, actor: Actor): Try<ConnectionAndUrl> {
            return getConnection(connection, apiRoutine, actor)
                    .map { conu: HttpConnection -> ConnectionAndUrl(apiRoutine, uri, conu) }
        }

        fun fromActor(connection: ConnectionActivityPub, apiRoutine: ApiRoutineEnum,
                      position: TimelinePosition, actor: Actor): Try<ConnectionAndUrl> {
            val endpoint = if (position.optUri().isPresent) position.optUri()
                else actor.getEndpoint(apiRoutine.toActorEndpointType())
            return if (!endpoint.isPresent) {
                Try.failure(ConnectionException(StatusCode.BAD_REQUEST, apiRoutine.toString() +
                        ": endpoint is empty for " + actor))
            } else getConnection(connection, apiRoutine, actor)
                    .map { httpConnection: HttpConnection -> ConnectionAndUrl(apiRoutine, endpoint.get(), httpConnection) }
        }

        private fun getConnection(connection: ConnectionActivityPub, apiRoutine: ApiRoutineEnum,
                                  actor: Actor): Try<HttpConnection> {
            var oauthHttp = connection.oauthHttpOrThrow
            val host = actor.getConnectionHost()
            if (host.isEmpty()) {
                return Try.failure(ConnectionException(StatusCode.BAD_REQUEST, apiRoutine.toString() +
                        ": host is empty for " + actor))
            } else if (connection.http.data.originUrl == null || host.compareTo(
                            connection.http.data.originUrl?.host ?: "", ignoreCase = true) != 0) {
                MyLog.v(connection) { "Requesting data from the host: $host" }
                val httpData = connection.http.data.copy()
                oauthHttp.oauthClientKeys = null
                httpData.originUrl = UrlUtils.buildUrl(host, httpData.isSsl())
                oauthHttp = ConnectionFactory.newHttp(connection.data, oauthHttp).oauthHttpOrThrow
                oauthHttp.data = httpData
            }
            if (!oauthHttp.areClientKeysPresent()) {
                oauthHttp.obtainAuthorizationServerMetadata()
                oauthHttp.registerClient()
                if (!oauthHttp.credentialsPresent) {
                    return Try.failure(ConnectionException.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST,
                            "No credentials", oauthHttp.data.originUrl))
                }
            }
            return Try.success(oauthHttp)
        }
    }
}
