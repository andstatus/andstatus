/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import org.andstatus.app.service.ConnectionState
import org.json.JSONObject
import java.net.URL
import java.util.*
import java.util.function.Function

object UriUtils {
    fun fromAlternativeTags(jso: JSONObject?, tag1: String?, tag2: String?): Uri {
        var uri = fromJson(jso, tag1)
        if (isEmpty(uri)) {
            uri = fromJson(jso, tag2)
        }
        return uri
    }

    fun nonEmpty(uri: Uri?): Boolean {
        return !isEmpty(uri)
    }

    /** @return true for null also
     */
    fun isEmpty(uri: Uri?): Boolean {
        return uri == null || Uri.EMPTY == uri
    }

    fun fromJson(jsoIn: JSONObject?, pathIn: String?): Uri {
        if (jsoIn == null || pathIn.isNullOrEmpty()) return Uri.EMPTY
        val path: Array<String?> = pathIn.split("/".toRegex()).toTypedArray()
        val jso = if (path.size == 2) jsoIn.optJSONObject(path[0]) else jsoIn
        val urlTag = if (path.size == 2) path[1] else pathIn
        return if (jso != null && !urlTag.isNullOrEmpty() && jso.has(urlTag)) {
            fromString(JsonUtils.optString(jso, urlTag))
        } else Uri.EMPTY
    }

    fun map(uri: Uri, mapper: Function<String?, String?>): Uri {
        return fromString(mapper.apply(uri.toString()))
    }

    fun toDownloadableOptional(uriString: String?): Optional<Uri> {
        return toOptional(uriString).filter { obj: Uri -> isDownloadable(obj) }
    }

    fun toOptional(uriString: String?): Optional<Uri> {
        if (uriString.isNullOrEmpty()) return Optional.empty()
        val uri = fromString(uriString)
        return if (uri === Uri.EMPTY) Optional.empty() else Optional.of(uri)
    }

    fun fromString(strUri: String?): Uri {
        return if (strUri == null || SharedPreferencesUtil.isEmpty(strUri)) Uri.EMPTY
            else Uri.parse(strUri.trim { it <= ' ' })
    }

    fun notNull(uri: Uri?): Uri {
        return uri ?: Uri.EMPTY
    }

    fun fromUrl(url: URL?): Uri {
        return if (url == null) {
            Uri.EMPTY
        } else {
            fromString(url.toExternalForm())
        }
    }

    /** See http://developer.android.com/guide/topics/providers/document-provider.html  */
    fun flagsToTakePersistableUriPermission(): Int {
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        flags = flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        return flags
    }

    /** See http://stackoverflow.com/questions/25999886/android-content-provider-uri-doesnt-work-after-reboot  */
    fun takePersistableUriPermission(context: Context, uri: Uri, takeFlagsIn: Int) {
        if (takeFlagsIn and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            val takeFlags = takeFlagsIn and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                context.getContentResolver().takePersistableUriPermission(uri, takeFlags)
            } catch (e: SecurityException) {
                MyLog.i(context, "Exception while taking persistable URI permission for '$uri'", e)
            }
        } else {
            MyLog.i(context, "No persistable URI permission for '$uri'")
        }
    }

    fun isDownloadable(uri: Uri?): Boolean {
        if (uri != null) {
            val scheme = uri.scheme
            if (scheme != null) {
                when (scheme) {
                    "http", "https", "magnet" -> return true
                    else -> {
                    }
                }
            }
        }
        return false
    }

    fun isRealOid(oid: String?): Boolean {
        return !nonRealOid(oid)
    }

    fun nonRealOid(oid: String?): Boolean {
        return StringUtil.isEmptyOrTemp(oid)
    }

    fun nonEmptyOid(oid: String?): Boolean {
        return !isEmptyOid(oid)
    }

    fun isEmptyOid(oid: String?): Boolean {
        return SharedPreferencesUtil.isEmpty(oid)
    }

    /**
     * Based on http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts
     */
    fun getConnectionState(context: Context): ConnectionState {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            ?: return ConnectionState.UNKNOWN
        return connectivityManager.allNetworks
            .mapNotNull { connectivityManager.getNetworkInfo(it) }
            .filter { it.isConnected && it.isAvailable }
            .map { networkInfo ->
                if (networkInfo.type == ConnectivityManager.TYPE_WIFI) {
                    ConnectionState.WIFI
                } else ConnectionState.ONLINE
            }
            .fold(ConnectionState.OFFLINE) { state, type ->
                if (type == ConnectionState.WIFI) {
                    ConnectionState.WIFI
                } else if (state == ConnectionState.OFFLINE) {
                    ConnectionState.ONLINE
                } else state
            }
    }
}
