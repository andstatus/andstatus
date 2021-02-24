/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.net.http

import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil

import kotlin.jvm.Volatile

/**
 * CLient Keys, obtained dynamically for each host and Origin.
 * @author yvolk@yurivolkov.com
 */
class OAuthClientKeysDynamic : OAuthClientKeysStrategy {
    @Volatile
    var keySuffix: String? = ""

    @Volatile
    var keyConsumerKey: String? = ""

    @Volatile
    var keyConsumerSecret: String? = ""

    @Volatile
    var consumerKey: String? = ""

    @Volatile
    var consumerSecret: String? = ""
    override fun initialize(connectionData: HttpConnectionData?) {
        if (connectionData.originUrl == null) {
            MyLog.v(this) { "OriginUrl is null; " + connectionData.toString() }
            return
        }
        keySuffix = java.lang.Long.toString(connectionData.getAccountName().getOrigin().id) +
                "-" + connectionData.originUrl.host
        keyConsumerKey = OAuthClientKeysDynamic.Companion.KEY_OAUTH_CLIENT_KEY + keySuffix
        keyConsumerSecret = OAuthClientKeysDynamic.Companion.KEY_OAUTH_CLIENT_SECRET + keySuffix
        consumerKey = SharedPreferencesUtil.getString(keyConsumerKey, "")
        consumerSecret = SharedPreferencesUtil.getString(keyConsumerSecret, "")
    }

    override fun getConsumerKey(): String? {
        return consumerKey
    }

    override fun getConsumerSecret(): String? {
        return consumerSecret
    }

    override fun setConsumerKeyAndSecret(consumerKeyIn: String?, consumerSecretIn: String?) {
        if (consumerKeyIn.isNullOrEmpty() || consumerSecretIn.isNullOrEmpty()) {
            consumerKey = ""
            consumerSecret = ""
            SharedPreferencesUtil.removeKey(keyConsumerKey)
            SharedPreferencesUtil.removeKey(keyConsumerSecret)
        } else {
            consumerKey = consumerKeyIn
            consumerSecret = consumerSecretIn
            SharedPreferencesUtil.putString(keyConsumerKey, consumerKey)
            SharedPreferencesUtil.putString(keyConsumerSecret, consumerSecret)
        }
    }

    override fun toString(): String {
        return OAuthClientKeysDynamic::class.java.simpleName + "-" + keySuffix
    }

    companion object {
        private val KEY_OAUTH_CLIENT_KEY: String? = "oauth_client_key"
        private val KEY_OAUTH_CLIENT_SECRET: String? = "oauth_client_secret"
    }
}