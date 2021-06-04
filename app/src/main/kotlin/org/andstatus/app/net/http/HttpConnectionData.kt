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
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.TriState
import java.net.URL
import java.util.*

class HttpConnectionData private constructor(private val accountName: AccountName) {
    var originUrl: URL? = null
    var urlForUserToken: URL? = null
    var dataReader: AccountDataReader? = null
    var oauthClientKeys: OAuthClientKeys? = null

    fun copy(): HttpConnectionData {
        val data = HttpConnectionData(accountName)
        data.originUrl = originUrl
        data.urlForUserToken = urlForUserToken
        data.dataReader = dataReader
        data.oauthClientKeys = oauthClientKeys
        return data
    }

    fun getAccountName(): AccountName {
        return accountName
    }

    fun areOAuthClientKeysPresent(): Boolean {
        return oauthClientKeys?.areKeysPresent() == true
    }

    override fun toString(): String {
        return ("HttpConnectionData {" + accountName + ", isSsl:" + isSsl()
                + ", sslMode:" + sslMode
                + (if (getUseLegacyHttpProtocol() != TriState.UNKNOWN) ", HTTP:" + (if (getUseLegacyHttpProtocol() == TriState.TRUE) "legacy" else "latest") else "")
                + ", basicPath:" + basicPath
                + ", oauthPath:" + oauthPath
                + ", originUrl:" + originUrl + ", hostForUserToken:" + urlForUserToken + ", dataReader:"
                + dataReader + ", oauthClientKeys:" + oauthClientKeys + "}")
    }

    fun getOriginType(): OriginType {
        return accountName.origin.originType
    }

    val basicPath: String get() = getOriginType().getBasicPath()
    val oauthPath: String get() = getOriginType().getOauthPath()

    fun isSsl(): Boolean {
        return accountName.origin.isSsl()
    }

    fun getUseLegacyHttpProtocol(): TriState {
        return accountName.origin.useLegacyHttpProtocol()
    }

    val sslMode: SslModeEnum get() = accountName.origin.getSslMode()

    fun jsonContentType(apiRoutine: ApiRoutineEnum): String {
        return if (apiRoutine.isOriginApi()) getOriginType().getContentType()
                .orElse(MyContentType.APPLICATION_JSON)
        else MyContentType.APPLICATION_JSON
    }

    fun optOriginContentType(): Optional<String> {
        return getOriginType().getContentType()
    }

    fun myContext(): MyContext {
        return accountName.origin.myContext
    }

    companion object {
        val EMPTY: HttpConnectionData = HttpConnectionData(AccountName.getEmpty())
        fun fromAccountConnectionData(acData: AccountConnectionData): HttpConnectionData {
            val data = HttpConnectionData(acData.getAccountName())
            data.originUrl = acData.getOriginUrl()
            data.urlForUserToken = acData.getOriginUrl()
            data.dataReader = acData.getDataReader()
            return data
        }
    }
}
