package org.andstatus.app.net.social

import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.account.MyAccount
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpConnectionData
import org.andstatus.app.net.http.HttpConnectionOAuth
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState

object ConnectionFactory {

    fun fromMyAccount(myAccount: MyAccount, isOAuth: TriState): Connection {
        if (!myAccount.origin.isValid) return ConnectionEmpty.EMPTY
        val connectionData: AccountConnectionData = AccountConnectionData.fromMyAccount(myAccount, isOAuth)
        return newConnection(myAccount.origin.originType, connectionData)
            .apply {
                setPassword(myAccount.data.getDataString(Connection.KEY_PASSWORD))
            }
    }

    fun fromOrigin(origin: Origin, isOAuth: TriState): Connection {
        if (!origin.isValid) return ConnectionEmpty.EMPTY
        val connectionData: AccountConnectionData = AccountConnectionData.fromOrigin(origin, isOAuth)
        return newConnection(origin.originType, connectionData)
    }

    private fun newConnection(originType: OriginType, acData: AccountConnectionData): Connection {
        return try {
            val connection = originType.getConnectionClass().newInstance()
            val acData2 = connection.updateConnectionData(acData)
            connection.data = acData2
            connection.http = newHttp(acData2, null)
            return connection
        } catch (e: Exception) {
            MyLog.e("Failed to instantiate connection for $originType", e)
            ConnectionEmpty.EMPTY
        }
    }

    fun newHttp(acData: AccountConnectionData, prevHttp: HttpConnection?): HttpConnection {
        val http = if (prevHttp?.isStub == true)
            prevHttp
        else acData.getOrigin().myContext.httpConnectionStub
            ?: acData.getOrigin().originType.getHttpConnectionClass(acData.isOAuth()).let { clazz ->
                try {
                    clazz.newInstance()
                } catch (e: Exception) {
                    MyLog.e("Failed to instantiate HttpConnection of $clazz", e)
                    HttpConnection.EMPTY
                }
            }

        if (http is HttpConnectionOAuth) http.urlForAccessToken = acData.getOriginUrl()
        http.data = HttpConnectionData.fromAccountConnectionData(acData)
        return http
    }

}
