/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import androidx.annotation.RawRes
import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpConnectionStub

class ConnectionStub private constructor(val connection: Connection) {
    fun withException(e: ConnectionException?): ConnectionStub {
        getHttpStub().setException(e)
        return this
    }

    fun addResponse(@RawRes responseResourceId: Int) {
        getHttpStub().addResponse(responseResourceId)
    }

    fun getData(): AccountConnectionData {
        return connection.data
    }

    fun getHttp(): HttpConnection {
        return connection.http
    }

    fun getHttpStub(): HttpConnectionStub {
        return getHttpStub(getHttp())
    }

    companion object {
        fun newFor(accountName: String?): ConnectionStub {
            return newFor(DemoData.demoData.getMyAccount(accountName))
        }

        fun newFor(myAccount: MyAccount): ConnectionStub {
            TestSuite.setHttpConnectionStubClass(HttpConnectionStub::class.java)
            val stub = ConnectionStub(myAccount.setConnection())
            TestSuite.setHttpConnectionStubClass(null)
            return stub
        }

        fun getHttpStub(http: HttpConnection?): HttpConnectionStub {
            if (http != null && HttpConnectionStub::class.java.isAssignableFrom(http.javaClass)) {
                return http as HttpConnectionStub
            }
            checkNotNull(http) { "http is null" }
            MyContextHolder.myContextHolder.getNow().httpConnectionStub
            throw IllegalStateException("http is " + http.javaClass.name + ", " +  MyContextHolder.myContextHolder.getNow())
        }
    }
}
