/*
 * Copyright (C) 2022 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.UrlUtils
import org.json.JSONObject
import java.net.URL

private const val KEY_ISSUER = "issuer"
private const val KEY_AUTHORIZATION_ENDPOINT = "authorization_endpoint"
private const val KEY_TOKEN_ENDPOINT = "token_endpoint"
private const val KEY_REGISTRATION_ENDPOINT = "registration_endpoint"
/** OAuth 2.0 Token Introspection https://www.rfc-editor.org/rfc/rfc7662 */
private const val KEY_INTROSPECTION_ENDPOINT = "introspection_endpoint"

/** https://datatracker.ietf.org/doc/html/rfc8414 */
data class AuthorizationServerMetadata(
    val issuer: URL,
    val authorizationEndpoint: String?,
    val tokenEndpoint: String?,
    val registrationEndpoint: String?,
    val introspectionEndpoint: String?
) {
    fun save(connectionData: HttpConnectionData) {
        if (connectionData.originUrl == null) {
            MyLog.v(this) { "OriginUrl is null; $connectionData" }
        }
        val keySuffix = java.lang.Long.toString(connectionData.getAccountName().origin.id) +
                "-" + connectionData.originUrl?.host
        saveString(KEY_ISSUER + keySuffix, issuer.toString())
        saveString(KEY_AUTHORIZATION_ENDPOINT + keySuffix, authorizationEndpoint)
        saveString(KEY_TOKEN_ENDPOINT + keySuffix, tokenEndpoint)
        saveString(KEY_REGISTRATION_ENDPOINT + keySuffix, registrationEndpoint)
        saveString(KEY_INTROSPECTION_ENDPOINT + keySuffix, introspectionEndpoint)
    }

    private fun saveString(key: String, value: String?) {
        if (value.isNullOrEmpty()) {
            SharedPreferencesUtil.removeKey(key)
        } else {
            SharedPreferencesUtil.putString(key, value)
        }
    }


    companion object {
        fun fromJson(json: JSONObject?): AuthorizationServerMetadata? = json?.let { json1 ->
            UrlUtils.fromJson(json1, KEY_ISSUER)?.let { issuer ->
                AuthorizationServerMetadata(
                    issuer,
                    JsonUtils.optString(json1, KEY_AUTHORIZATION_ENDPOINT),
                    JsonUtils.optString(json1, KEY_TOKEN_ENDPOINT),
                    JsonUtils.optString(json1, KEY_REGISTRATION_ENDPOINT),
                    JsonUtils.optString(json1, KEY_INTROSPECTION_ENDPOINT)
                )
            }
        }

        fun load(connectionData: HttpConnectionData): AuthorizationServerMetadata? {
            if (connectionData.originUrl == null) {
                MyLog.v(this) { "OriginUrl is null; $connectionData" }
                return null
            }
            val keySuffix = java.lang.Long.toString(connectionData.getAccountName().origin.id) +
                    "-" + connectionData.originUrl?.host
            val issuer = UrlUtils.fromString(SharedPreferencesUtil.getString(KEY_ISSUER + keySuffix)) ?: return null

            return AuthorizationServerMetadata(
                issuer,
                SharedPreferencesUtil.getString(KEY_AUTHORIZATION_ENDPOINT + keySuffix),
                SharedPreferencesUtil.getString(KEY_TOKEN_ENDPOINT + keySuffix),
                SharedPreferencesUtil.getString(KEY_REGISTRATION_ENDPOINT + keySuffix),
                SharedPreferencesUtil.getString(KEY_INTROSPECTION_ENDPOINT + keySuffix)
            )
        }

    }
}
