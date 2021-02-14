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
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UrlUtils

internal class ConnectionAndUrl private constructor(val apiRoutine: ApiRoutineEnum?, val uri: Uri?, val httpConnection: HttpConnection?) {
    var syncYounger = true
    fun withUri(newUri: Uri?): ConnectionAndUrl? {
        return ConnectionAndUrl(apiRoutine, newUri, httpConnection)
                .withSyncDirection(syncYounger)
    }

    fun newRequest(): HttpRequest? {
        return HttpRequest.Companion.of(apiRoutine, uri)
    }

    fun execute(request: HttpRequest?): Try<HttpReadResult?>? {
        return httpConnection.execute(request)
    }

    fun withSyncDirection(syncYounger: Boolean): ConnectionAndUrl? {
        this.syncYounger = syncYounger
        return this
    }

    companion object {
        fun fromUriActor(uri: Uri?, connection: ConnectionActivityPub?,
                         apiRoutine: ApiRoutineEnum?, actor: Actor?): Try<ConnectionAndUrl?>? {
            return getConnection(connection, apiRoutine, actor).map(CheckedFunction { conu: HttpConnection? -> ConnectionAndUrl(apiRoutine, uri, conu) })
        }

        fun fromActor(connection: ConnectionActivityPub?, apiRoutine: ApiRoutineEnum?,
                      position: TimelinePosition?, actor: Actor?): Try<ConnectionAndUrl?>? {
            val endpoint = if (position.optUri().isPresent) position.optUri() else actor.getEndpoint(ActorEndpointType.Companion.from(apiRoutine))
            return if (!endpoint.isPresent) {
                Try.failure(ConnectionException(StatusCode.BAD_REQUEST, apiRoutine.toString() +
                        ": endpoint is empty for " + actor))
            } else getConnection(connection, apiRoutine, actor).map(CheckedFunction { httpConnection: HttpConnection? -> ConnectionAndUrl(apiRoutine, endpoint.get(), httpConnection) })
        }

        private fun getConnection(connection: ConnectionActivityPub?, apiRoutine: ApiRoutineEnum?,
                                  actor: Actor?): Try<HttpConnection?>? {
            var httpConnection = connection.getHttp()
            val host = actor.getConnectionHost()
            if (StringUtil.isEmpty(host)) {
                return Try.failure(ConnectionException(StatusCode.BAD_REQUEST, apiRoutine.toString() +
                        ": host is empty for " + actor))
            } else if (connection.getHttp().data.originUrl == null || host.compareTo(
                            connection.getHttp().data.originUrl.host, ignoreCase = true) != 0) {
                MyLog.v(connection) { "Requesting data from the host: $host" }
                val connectionData1 = connection.getHttp().data.copy()
                connectionData1.oauthClientKeys = null
                connectionData1.originUrl = UrlUtils.buildUrl(host, connectionData1.isSsl)
                httpConnection = connection.getHttp().newInstance
                httpConnection.setHttpConnectionData(connectionData1)
            }
            if (!httpConnection.data.areOAuthClientKeysPresent()) {
                httpConnection.registerClient()
                if (!httpConnection.credentialsPresent) {
                    return Try.failure(ConnectionException.Companion.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST,
                            "No credentials", httpConnection.data.originUrl))
                }
            }
            return Try.success(httpConnection)
        }
    }
}