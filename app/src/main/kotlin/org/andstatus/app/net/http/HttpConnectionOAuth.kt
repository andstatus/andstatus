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
package org.andstatus.app.net.http

import android.net.Uri
import android.text.TextUtils
import com.github.scribejava.core.oauth.OAuth20Service
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils

abstract class HttpConnectionOAuth : HttpConnection(), OAuthService {
    var logMe = false
    private fun userTokenKey(): String {
        return "user_token"
    }

    private fun userSecretKey(): String {
        return "user_secret"
    }

    override var userToken: String = ""
    override var userSecret: String = ""
    // TODO: Do we need this?
    override var password: String = ""

    override fun setHttpConnectionData(connectionData: HttpConnectionData) {
        super.setHttpConnectionData(connectionData)
        connectionData.oauthClientKeys = OAuthClientKeys.fromConnectionData(connectionData)
        // We look for saved user keys
        connectionData.dataReader?.let { dataReader ->
            if (dataReader.dataContains(userTokenKey()) == true &&
                    dataReader.dataContains(userSecretKey()) == true) {
                userToken = dataReader.getDataString(userTokenKey())
                userSecret = dataReader.getDataString(userSecretKey())
                setUserTokenWithSecret(userToken, userSecret)
            }
        }
    }

    override val credentialsPresent: Boolean get() {
        val yes = (data.oauthClientKeys?.areKeysPresent() == true
                && !userToken.isEmpty()
                && !userSecret.isEmpty())
        if (!yes && logMe) {
            MyLog.v(this) {
                ("Credentials presence: clientKeys:" + data.oauthClientKeys?.areKeysPresent()
                        + "; userKeys:" + !userToken.isEmpty() + "," + !userSecret.isEmpty())
            }
        }
        return yes
    }

    open fun getApiUri(routine: ApiRoutineEnum?): Uri {
        var url: String
        url = when (routine) {
            ApiRoutineEnum.OAUTH_ACCESS_TOKEN -> data.oauthPath + "/access_token"
            ApiRoutineEnum.OAUTH_AUTHORIZE -> data.oauthPath + "/authorize"
            ApiRoutineEnum.OAUTH_REQUEST_TOKEN -> data.oauthPath + "/request_token"
            ApiRoutineEnum.OAUTH_REGISTER_CLIENT -> data.basicPath + "/client/register"
            else -> ""
        }
        if (!url.isNullOrEmpty()) {
            url = pathToUrlString(url)
        }
        return UriUtils.fromString(url)
    }

    override fun getService(redirect: Boolean): OAuth20Service? {
        return null
    }

    override fun isOAuth2(): Boolean {
        return false
    }

    /**
     * @param token empty value means to clear the old values
     * @param secret
     */
    override fun setUserTokenWithSecret(token: String?, secret: String?) {
        synchronized(this) {
            userToken = token ?: ""
            userSecret = secret ?: ""
        }
        if (logMe) {
            MyLog.v(this) {
                ("Credentials set?: " + !token.isNullOrEmpty()
                        + ", " + !secret.isNullOrEmpty())
            }
        }
    }

    override fun saveTo(dw: AccountDataWriter): Boolean {
        var changed = super.saveTo(dw)
        if (!TextUtils.equals(userToken, dw.getDataString(userTokenKey())) ||
                !TextUtils.equals(userSecret, dw.getDataString(userSecretKey()))) {
            changed = true
            if (userToken.isEmpty()) {
                dw.setDataString(userTokenKey(), "")
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Token")
                }
            } else {
                dw.setDataString(userTokenKey(), userToken)
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Token: $userToken")
                }
            }
            if (userSecret.isEmpty()) {
                dw.setDataString(userSecretKey(), "")
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Secret")
                }
            } else {
                dw.setDataString(userSecretKey(), userSecret)
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Secret: $userSecret")
                }
            }
        }
        return changed
    }

    override fun clearAuthInformation() {
        setUserTokenWithSecret("", "")
    }

    companion object {
        private val TAG: String = HttpConnectionOAuth::class.java.simpleName
    }
}
