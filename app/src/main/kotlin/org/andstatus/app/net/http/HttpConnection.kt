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

const val CLIENT_URI = "http://andstatus.org/andstatus"
const val CALLBACK_URI: String = "http://oauth-redirect.andstatus.org"
const val LOGO_URI = "http://andstatus.org/images/andstatus-logo.png"
val oauthScopesMinimum: List<String> = listOf("read", "write", "follow")
val oauthScopesKnown: List<String> = oauthScopesMinimum + "profile"
const val POLICY_URI = "https://github.com/andstatus/andstatus/blob/master/doc/Privacy-Policy.md"
const val USER_AGENT: String = "AndStatus"

open class HttpConnection {
    open var data: HttpConnectionData = HttpConnectionData.EMPTY
    var isStub: Boolean = false

    val oauthHttp: HttpConnectionOAuth? get() = this as? HttpConnectionOAuth

    open fun pathToUrlString(path: String): String {
        // TODO: return Try
        return UrlUtils.pathToUrlString(data.originUrl, path, errorOnInvalidUrls())
            .getOrElse("")
    }

    open fun errorOnInvalidUrls(): Boolean {
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

    private fun executeInner(request: HttpRequest): Try<HttpReadResult> {
        if (request.verb == Verb.POST && MyPreferences.isLogNetworkLevelMessages()) {
            var jso = JsonUtils.put(request.postParams.orElseGet { JSONObject() }, "loggedURL", request.uri)
            if (request.mediaUri.isPresent) {
                jso = JsonUtils.put(jso, "loggedMediaUri", request.mediaUri.get().toString())
            }
            MyLog.logNetworkLevelMessage("post", request.getLogName(), jso, "")
        }
        return request.validate()
            .flatMap(Throttler::beforeExecution)
            .map { obj: HttpRequest -> obj.newResult() }
            .flatMap { result: HttpReadResult ->
                if (result.request.verb == Verb.POST) postRequest(result).toTryResult()
                else getRequestInner(result).toTryResult()
            }
            .also(Throttler::afterExecution)
            .onSuccess { result: HttpReadResult -> result.logResponse() }
    }

    open fun postRequest(result: HttpReadResult): HttpReadResult {
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
        return result.readStream(
            "mediaUri='" + result.request.uri + "'"
        ) { result.request.myContext().context.contentResolver.openInputStream(result.request.uri) }
            .recover(Exception::class.java) { e: Exception? -> result.setException(e) }
            .getOrElse(result)
    }

    open fun getRequest(result: HttpReadResult): HttpReadResult {
        return result
    }

    open fun clearAuthInformation() {
        // Nothing to do
    }

    open fun isPasswordNeeded(): Boolean {
        return false
    }

    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    open fun saveTo(dw: AccountDataWriter): Boolean {
        return false
    }

    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    open val credentialsPresent: Boolean get() = false

    val sslMode: SslModeEnum get() = data.sslMode

    fun onMoved(result: HttpReadResult): Boolean {
        result.appendToLog("statusLine:'" + result.statusLine + "'")
        result.redirected = true
        return TryUtils.fromOptional(result.location)
            .mapFailure { ConnectionException(StatusCode.MOVED, "No 'Location' header on MOVED response") }
            .flatMap { location: String -> UrlUtils.redirectTo(result.url, location) }
            .mapFailure {
                ConnectionException(
                    StatusCode.MOVED, "Invalid redirect from '${result.url}'" +
                        " to '${result.location}'"
                )
            }
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
        val EMPTY: HttpConnection = HttpConnection()

        /**
         * The URI is consistent with "scheme" and "host" in AndroidManifest
         * Pump.io doesn't work with this scheme: "andstatus-oauth://andstatus.org"
         */
        val CALLBACK_URI_PARSED: Uri = Uri.parse(CALLBACK_URI)
    }
}
