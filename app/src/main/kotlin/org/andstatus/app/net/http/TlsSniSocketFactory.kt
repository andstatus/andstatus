/*******************************************************************************
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com - Insecure connection added
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 */
package org.andstatus.app.net.http

import android.net.SSLCertificateSocketFactory
import cz.msebera.android.httpclient.HttpHost
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory
import cz.msebera.android.httpclient.conn.socket.LayeredConnectionSocketFactory
import cz.msebera.android.httpclient.conn.ssl.AllowAllHostnameVerifier
import cz.msebera.android.httpclient.conn.ssl.BrowserCompatHostnameVerifier
import cz.msebera.android.httpclient.protocol.HttpContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket

class TlsSniSocketFactory(sslMode: SslModeEnum?) : LayeredConnectionSocketFactory {
    private val secure: Boolean
    private var sslSocketFactory: SSLCertificateSocketFactory? = null

    override fun createSocket(context: HttpContext?): Socket? {
        return sslSocketFactory?.createSocket()
    }

    override fun connectSocket(timeout: Int, plain: Socket, host: HttpHost, remoteAddr: InetSocketAddress, localAddr: InetSocketAddress?, context: HttpContext?): Socket {
        MyLog.d(TAG, "Preparing direct SSL connection (without proxy) to $host")

        // we'll rather use an SSLSocket directly
        plain.close()

        // create a plain SSL socket, but don't do hostname/certificate verification yet
        val ssl = sslSocketFactory?.createSocket(remoteAddr.getAddress(), host.getPort()) as SSLSocket

        // connect, set SNI, shake hands, verify, print connection info
        connectWithSNI(ssl, host.getHostName())
        return ssl
    }

    override fun createLayeredSocket(plain: Socket?, host: String, port: Int, context: HttpContext?): Socket {
        MyLog.d(TAG, "Preparing layered SSL connection (over proxy) to $host")

        // create a layered SSL socket, but don't do hostname/certificate verification yet
        val ssl = sslSocketFactory?.createSocket(plain, host, port, true) as SSLSocket

        // already connected, but verify host name again and print some connection info
        MyLog.d(TAG, "Setting SNI/TLSv1.2 will silently fail because the handshake is already done")
        connectWithSNI(ssl, host)
        return ssl
    }

    private fun connectWithSNI(ssl: SSLSocket, host: String) {
        // set reasonable SSL/TLS settings before the handshake:
        // - enable all supported protocols
        ssl.setEnabledProtocols(ssl.getSupportedProtocols())
        MyLog.d(TAG, "Using documented SNI with host name $host")
        sslSocketFactory?.setHostname(ssl, host)

        // verify hostname and certificate
        val session = ssl.getSession()
        if (!session.isValid) {
            MyLog.i(TAG, "Invalid session to host:'$host'")
        }
        val hostnameVerifier: HostnameVerifier = if (secure) BrowserCompatHostnameVerifier() else AllowAllHostnameVerifier()
        if (!hostnameVerifier.verify(host, session)) {
            throw SSLPeerUnverifiedException("Cannot verify hostname: $host")
        }
        MyLog.d(TAG, "Established " + session.protocol + " connection with " + session.peerHost +
                " using " + session.cipherSuite)
    }

    companion object {
        private val TAG: String = TlsSniSocketFactory::class.java.simpleName
        private val instances: ConcurrentHashMap<SslModeEnum?, TlsSniSocketFactory?> = ConcurrentHashMap()
        fun getInstance(sslMode: SslModeEnum?): ConnectionSocketFactory? {
            if (!instances.containsKey(sslMode)) {
                instances[sslMode] = TlsSniSocketFactory(sslMode)
            }
            return instances.get(sslMode)
        }

        fun forget() {
            instances.clear()
        }
    }

    /*
    For SSL connections without HTTP(S) proxy:
       1) createSocket() is called
       2) connectSocket() is called which creates a new SSL connection
       2a) SNI is set up, and then
       2b) the connection is established, hands are shaken and certificate/host name are verified        
    
    Layered sockets are used with HTTP(S) proxies:
       1) a new plain socket is created by the HTTP library
       2) the plain socket is connected to http://proxy:8080
       3) a CONNECT request is sent to the proxy and the response is parsed
       4) now, createLayeredSocket() is called which wraps an SSL socket around the proxy connection,
          doing all the set-up and verfication
       4a) Because SSLSocket.createSocket(socket, ...) always does a handshake without allowing
           to set up SNI before, *** SNI is not available for layered connections *** (unless
           active by Android's defaults, which it isn't at the moment).
    */
    init {
        secure = sslMode == SslModeEnum.SECURE
        if (secure) {
            sslSocketFactory = SSLCertificateSocketFactory
                    .getDefault(MyPreferences.getConnectionTimeoutMs()) as SSLCertificateSocketFactory
        } else {
            sslSocketFactory = SSLCertificateSocketFactory
                    .getInsecure(MyPreferences.getConnectionTimeoutMs(), null) as SSLCertificateSocketFactory
            MyLog.i(TAG, "Insecure SSL allowed")
        }
    }
}
