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
package org.andstatus.app.account

import org.andstatus.app.account.MyAccount
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpConnectionEmpty
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.TriState
import java.net.URL

class AccountConnectionData private constructor(private val myAccount: MyAccount, private val origin: Origin, triStateOAuth: TriState) {
    private val isOAuth: Boolean
    private var originUrl: URL?
    private val httpConnectionClass: Class<out HttpConnection?>?
    fun getAccountActor(): Actor {
        return myAccount.actor
    }

    fun getMyAccount(): MyAccount {
        return myAccount
    }

    fun getAccountName(): AccountName {
        return myAccount.getOAccountName()
    }

    fun getOriginType(): OriginType {
        return origin.originType
    }

    fun getOrigin(): Origin {
        return origin
    }

    fun isSsl(): Boolean {
        return origin.isSsl()
    }

    fun isOAuth(): Boolean {
        return isOAuth
    }

    fun getOriginUrl(): URL? {
        return originUrl
    }

    fun setOriginUrl(urlIn: URL?) {
        originUrl = urlIn
    }

    fun getDataReader(): AccountDataReader? {
        return myAccount.data
    }

    fun newHttpConnection(): HttpConnection? {
        val http = origin.myContext.httpConnectionMock
        return http
                ?: try {
                    httpConnectionClass.newInstance()
                } catch (e: InstantiationException) {
                    HttpConnectionEmpty.Companion.EMPTY
                } catch (e: IllegalAccessException) {
                    HttpConnectionEmpty.Companion.EMPTY
                }
    }

    override fun toString(): String {
        return if (myAccount.isEmpty) if (origin.hasHost()) origin.getHost() else originUrl.toString() else myAccount.getAccountName()
    }

    companion object {
        fun fromOrigin(origin: Origin?, triStateOAuth: TriState?): AccountConnectionData {
            return AccountConnectionData(MyAccount.Companion.EMPTY, origin, triStateOAuth)
        }

        fun fromMyAccount(myAccount: MyAccount?, triStateOAuth: TriState?): AccountConnectionData {
            return AccountConnectionData(myAccount, myAccount.origin, triStateOAuth)
        }
    }

    init {
        originUrl = origin.url
        isOAuth = origin.originType.fixIsOAuth(triStateOAuth)
        httpConnectionClass = origin.originType.getHttpConnectionClass(isOAuth())
    }
}