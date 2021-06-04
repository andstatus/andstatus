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
package org.andstatus.app.net.http

import android.net.Uri
import com.github.scribejava.core.model.Verb
import io.vavr.control.Try
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONObject
import java.net.URL

interface HttpConnectionInterface {

    val data: HttpConnectionData

    fun registerClient(): Try<Void> {
        // Do nothing in the default implementation
        return Try.success(null)
    }

    fun setHttpConnectionData(data: HttpConnectionData)

    fun pathToUrlString(path: String): String {
        // TODO: return Try
        return UrlUtils.pathToUrlString(data.originUrl, path, errorOnInvalidUrls())
                .getOrElse("")
    }

    fun errorOnInvalidUrls(): Boolean {
        return true
    }

    fun execute(requestIn: HttpRequest): Try<HttpReadResult> {
        val request = requestIn.withConnectionData(data)
        return if (request.verb == Verb.POST) {
            /* See https://github.com/andstatus/andstatus/issues/249 */
            if (data.getUseLegacyHttpProtocol() == TriState.UNKNOWN) executeOneProtocol(request, false)
                    .orElse { executeOneProtocol(request, true) }
            else executeOneProtocol(request, data.getUseLegacyHttpProtocol().toBoolean(true))
        } else {
            executeInner(request)
        }
    }

    fun executeOneProtocol(request: HttpRequest, isLegacyHttpProtocol: Boolean): Try<HttpReadResult> {
        return executeInner(request.withLegacyHttpProtocol(isLegacyHttpProtocol))
    }

    fun executeInner(request: HttpRequest): Try<HttpReadResult> {
        if (request.verb == Verb.POST && MyPreferences.isLogNetworkLevelMessages()) {
            var jso = JsonUtils.put(request.postParams.orElseGet { JSONObject() }, "loggedURL", request.uri)
            if (request.mediaUri.isPresent) {
                jso = JsonUtils.put(jso, "loggedMediaUri", request.mediaUri.get().toString())
            }
            MyLog.logNetworkLevelMessage("post", request.getLogName(), jso, "")
        }
        return request.validate()
                .map { obj: HttpRequest -> obj.newResult() }
                .map { result: HttpReadResult -> if (result.request.verb == Verb.POST) postRequest(result)
                else getRequestInner(result) }
                .map { obj: HttpReadResult -> obj.logResponse() }
                .flatMap { obj: HttpReadResult -> obj.tryToParse() }
    }

    fun postRequest(result: HttpReadResult): HttpReadResult {
        return result
    }

    fun getRequestInner(result: HttpReadResult): HttpReadResult {
        return if (result.request.apiRoutine == ApiRoutineEnum.DOWNLOAD_FILE && !UriUtils.isDownloadable(result.request.uri)) {
            downloadLocalFile(result)
        } else {
            getRequest(result)
        }
    }

    fun downloadLocalFile(result: HttpReadResult): HttpReadResult {
        return result.readStream("mediaUri='" + result.request.uri + "'"
        ) { result.request.myContext().context().contentResolver.openInputStream(result.request.uri) }
                .recover(Exception::class.java) { e: Exception? -> result.setException(e) }
                .getOrElse(result)
    }

    fun getRequest(result: HttpReadResult): HttpReadResult {
        return result
    }

    fun clearAuthInformation() {
        // Empty
    }

    fun clearClientKeys() {
        data.oauthClientKeys?.clear()
    }

    fun isPasswordNeeded(): Boolean {
        return false
    }

    var password: String

    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    fun saveTo(dw: AccountDataWriter): Boolean {
        return false
    }

    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    val credentialsPresent: Boolean get() = false

    val sslMode: SslModeEnum get() = data.sslMode

    fun setUserTokenWithSecret(token: String?, secret: String?) {
        throw IllegalArgumentException("setUserTokenWithSecret is for OAuth only!")
    }

    val userToken: String get() = ""

    val userSecret: String get() = ""

    fun getNewInstance(): HttpConnectionInterface

    fun onMoved(result: HttpReadResult): Boolean {
        result.appendToLog("statusLine:'" + result.statusLine + "'")
        result.redirected = true
        return TryUtils.fromOptional(result.getLocation())
                .mapFailure { ConnectionException(StatusCode.MOVED, "No 'Location' header on MOVED response") }
                .flatMap { location: String -> UrlUtils.redirectTo(result.url, location)}
                .mapFailure { ConnectionException(StatusCode.MOVED, "Invalid redirect from '${result.url}'" +
                        " to '${result.getLocation()}'") }
                .map { redirected: URL -> result.setUrl(redirected) }
                .onFailure { e: Throwable -> result.setException(e) }
                .onSuccess { result1: HttpReadResult -> logFollowingRedirects(result1) }
                .isFailure
    }

    fun logFollowingRedirects(result: HttpReadResult) {
        if (MyLog.isVerboseEnabled()) {
            val builder: MyStringBuilder = MyStringBuilder.of("Following redirect to '${result.url}'")
            result.appendHeaders(builder)
            MyLog.v(this, builder.toString())
        }
    }

    companion object {
        val USER_AGENT: String = "AndStatus"

        /**
         * The URI is consistent with "scheme" and "host" in AndroidManifest
         * Pump.io doesn't work with this scheme: "andstatus-oauth://andstatus.org"
         */
        val CALLBACK_URI = Uri.parse("http://oauth-redirect.andstatus.org")
    }
}
