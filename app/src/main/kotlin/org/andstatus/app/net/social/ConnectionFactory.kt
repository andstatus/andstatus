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

    private fun newConnection(originType: OriginType, connectionData: AccountConnectionData): Connection {
        return try {
            val connection = originType.getConnectionClass().newInstance() as Connection
            connection.data = connection.updateConnectionData(connectionData)
            connection.http = httpFromConnection(connection, false)
            return connection
        } catch (e: InstantiationException) {
            MyLog.e("Failed to instantiate connection for $originType", e)
            ConnectionEmpty.EMPTY
        } catch (e: IllegalAccessException) {
            MyLog.e("Failed to instantiate connection for $originType", e)
            ConnectionEmpty.EMPTY
        }
    }

    fun httpFromConnection(connection: Connection, checkHttp: Boolean): HttpConnection {
        val http = if (checkHttp && connection.http.isStub)
            connection.http
        else connection.data.getOrigin().myContext.httpConnectionStub
            ?: try {
                connection.data.httpConnectionClass.newInstance()
            } catch (e: InstantiationException) {
                HttpConnection.EMPTY
            } catch (e: IllegalAccessException) {
                HttpConnection.EMPTY
            }

        if (http is HttpConnectionOAuth) http.urlForUserToken = connection.data.getOriginUrl()
        http.data = HttpConnectionData.fromAccountConnectionData(connection.data)
        return http
    }

}
