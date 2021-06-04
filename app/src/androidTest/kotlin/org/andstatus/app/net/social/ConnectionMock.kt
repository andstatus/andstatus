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
import org.andstatus.app.net.http.HttpConnectionMock
import java.io.IOException

class ConnectionMock private constructor(val connection: Connection) {
    fun withException(e: ConnectionException?): ConnectionMock {
        getHttpMock().setException(e)
        return this
    }

    @Throws(IOException::class)
    fun addResponse(@RawRes responseResourceId: Int) {
        getHttpMock().addResponse(responseResourceId)
    }

    fun getData(): AccountConnectionData {
        return connection.data
    }

    fun getHttp(): HttpConnection {
        return connection.http
    }

    fun getHttpMock(): HttpConnectionMock {
        return getHttpMock(getHttp())
    }

    companion object {
        fun newFor(accountName: String?): ConnectionMock {
            return newFor(DemoData.demoData.getMyAccount(accountName))
        }

        fun newFor(myAccount: MyAccount): ConnectionMock {
            TestSuite.setHttpConnectionMockClass(HttpConnectionMock::class.java)
            val mock = ConnectionMock(myAccount.setConnection())
            TestSuite.setHttpConnectionMockClass(null)
            return mock
        }

        fun getHttpMock(http: HttpConnection?): HttpConnectionMock {
            if (http != null && HttpConnectionMock::class.java.isAssignableFrom(http.javaClass)) {
                return http as HttpConnectionMock
            }
            checkNotNull(http) { "http is null" }
             MyContextHolder.myContextHolder.getNow().getHttpConnectionMock()
            throw IllegalStateException("http is " + http.javaClass.name + ", " +  MyContextHolder.myContextHolder.getNow().toString())
        }
    }
}
