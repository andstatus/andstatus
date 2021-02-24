/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.util

import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.json.JSONException
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.regex.Pattern

object UrlUtils {
    private val TAG: String = UrlUtils::class.java.simpleName

    // From http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address?rq=1
    private val validHostnameRegex: String = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$"
    private val validHostnameRegexPattern = Pattern.compile(validHostnameRegex)
    val MALFORMED = fromString("http://127.0.0.1/malformedUrl")

    fun getHost(strUrl: String?): Optional<String> {
        return Optional.ofNullable(fromString(strUrl))
                .map { it.host }
                .filter { hostIsValid(it) }
    }

    fun hostIsValid(host: String?): Boolean {
        return !host.isNullOrEmpty() && validHostnameRegexPattern.matcher(host).matches()
    }

    fun hasHost(url: URL?): Boolean {
        return url != null && hostIsValid(url.host)
    }

    fun isHostOnly(url: URL?): Boolean {
        return (url != null && url.file.isNullOrEmpty()
                && url.host?.contentEquals(url.authority) ?: false)
    }

    fun fromString(strUrl: String?): URL? {
        return if (strUrl.isNullOrEmpty()) null else try {
            URL(strUrl)
        } catch (e: MalformedURLException) {
            null
        }
    }

    fun fromUri(uri: Uri?): URL? {
        return if (uri == null || uri === Uri.EMPTY) {
            null
        } else {
            fromString(uri.toString())
        }
    }

    @Throws(JSONException::class)
    fun fromJson(jso: JSONObject?, urlTag: String?): URL? {
        if (jso != null && !urlTag.isNullOrEmpty() && jso.has(urlTag)) {
            val strUrl = jso.getString(urlTag)
            try {
                return URL(strUrl)
            } catch (e: MalformedURLException) {
                MyLog.d(TAG, "tag:'$urlTag' has malformed URL:'$strUrl'", e)
            }
        }
        return null
    }

    fun buildUrl(hostOrUrl: String?, isSsl: Boolean): URL? {
        if (hostOrUrl.isNullOrEmpty()) {
            return null
        }
        val corrected = correctedHostOrUrl(hostOrUrl)
        if (hostIsValid(corrected)) {
            return fromString("http" + (if (isSsl) "s" else "") + "://" + corrected)
        }
        val urlIn = fromString(corrected)
        return if (urlIn == null || urlIn.protocol == if (isSsl) "https" else "http") {
            urlIn
        } else fromString((if (isSsl) "https" else "http") + urlIn.toExternalForm().substring(urlIn.toExternalForm().indexOf(":")))
    }

    private fun correctedHostOrUrl(hostOrUrl: String?): String {
        return if (hostOrUrl.isNullOrEmpty()) {
            ""
        } else hostOrUrl.replace(" ".toRegex(), "").toLowerCase(Locale.ENGLISH)
        // Test with: http://www.regexplanet.com/advanced/java/index.html
    }

    fun pathToUrlString(originUrl: URL?, path: String?, failOnInvalid: Boolean): Try<String> {
        val url: Try<URL> = pathToUrl(originUrl, path)
        if (url.isFailure()) {
            return if (failOnInvalid) Try.failure(ConnectionException.hardConnectionException("URL is unknown or malformed. System URL:'"
                    + originUrl + "', path:'" + path + "'", null)) else Try.success("")
        }
        val host = url.map { obj -> obj.getHost() }.getOrElse("")
        return if (failOnInvalid && (host == "example.com" || host.endsWith(".example.com"))) {
            Try.failure(ConnectionException.fromStatusCode(StatusCode.NOT_FOUND,
                    "URL: '" + url.get().toExternalForm() + "'"))
        } else url.map { it.toExternalForm() }
    }

    fun pathToUrl(originUrl: URL?, path: String?): Try<URL> {
        return try {
            if (path != null && path.contains("://")) {
                Try.success(URL(path))
            } else Try.success(URL(originUrl, path))
        } catch (e: MalformedURLException) {
            TryUtils.failure("Malformed URL, originUrl:'$originUrl', path:'$path'")
        }
    }
}