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
package org.andstatus.app.net.http

import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.account.AccountDataReader
import org.andstatus.app.account.AccountName
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.MyContentType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.TriState

org.andstatus.app.origin.OriginTypeimport java.net.URLimport java.util.*
class HttpConnectionData private constructor(private val accountName: AccountName?) {
    var originUrl: URL? = null
    var urlForUserToken: URL? = null
    var dataReader: AccountDataReader? = null
    var oauthClientKeys: OAuthClientKeys? = null
    fun copy(): HttpConnectionData? {
        val data = HttpConnectionData(accountName)
        data.originUrl = originUrl
        data.urlForUserToken = urlForUserToken
        data.dataReader = dataReader
        data.oauthClientKeys = oauthClientKeys
        return data
    }

    fun getAccountName(): AccountName? {
        return accountName
    }

    fun areOAuthClientKeysPresent(): Boolean {
        return oauthClientKeys != null && oauthClientKeys.areKeysPresent()
    }

    override fun toString(): String {
        return ("HttpConnectionData {" + accountName + ", isSsl:" + isSsl()
                + ", sslMode:" + getSslMode()
                + (if (getUseLegacyHttpProtocol() != TriState.UNKNOWN) ", HTTP:" + (if (getUseLegacyHttpProtocol() == TriState.TRUE) "legacy" else "latest") else "")
                + ", basicPath:" + getBasicPath()
                + ", oauthPath:" + getOauthPath()
                + ", originUrl:" + originUrl + ", hostForUserToken:" + urlForUserToken + ", dataReader:"
                + dataReader + ", oauthClientKeys:" + oauthClientKeys + "}")
    }

    fun getOriginType(): OriginType? {
        return accountName.getOrigin().originType
    }

    fun getBasicPath(): String? {
        return getOriginType().getBasicPath()
    }

    fun getOauthPath(): String? {
        return getOriginType().getOauthPath()
    }

    fun isSsl(): Boolean {
        return accountName.getOrigin().isSsl
    }

    fun getUseLegacyHttpProtocol(): TriState? {
        return accountName.getOrigin().useLegacyHttpProtocol()
    }

    fun getSslMode(): SslModeEnum? {
        return accountName.getOrigin().sslMode
    }

    fun jsonContentType(apiRoutine: ApiRoutineEnum?): String? {
        return if (apiRoutine.isOriginApi()) getOriginType().getContentType().orElse(MyContentType.Companion.APPLICATION_JSON) else MyContentType.Companion.APPLICATION_JSON
    }

    fun optOriginContentType(): Optional<String> {
        return getOriginType().getContentType()
    }

    fun myContext(): MyContext? {
        return accountName.getOrigin().myContext
    }

    companion object {
        val EMPTY: HttpConnectionData? = HttpConnectionData(AccountName.Companion.getEmpty())
        fun fromAccountConnectionData(accountnData: AccountConnectionData?): HttpConnectionData? {
            val data = HttpConnectionData(accountnData.getAccountName())
            data.originUrl = accountnData.getOriginUrl()
            data.urlForUserToken = accountnData.getOriginUrl()
            data.dataReader = accountnData.getDataReader()
            return data
        }
    }
}