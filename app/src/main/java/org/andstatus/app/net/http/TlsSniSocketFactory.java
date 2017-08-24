/*******************************************************************************
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com - Insecure connection added
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 ******************************************************************************/
package org.andstatus.app.net.http;

import android.net.SSLCertificateSocketFactory;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.socket.LayeredConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.AllowAllHostnameVerifier;
import cz.msebera.android.httpclient.conn.ssl.BrowserCompatHostnameVerifier;
import cz.msebera.android.httpclient.protocol.HttpContext;

public class TlsSniSocketFactory implements LayeredConnectionSocketFactory {

    private static final ConcurrentHashMap<SslModeEnum, TlsSniSocketFactory> instances = new ConcurrentHashMap<SslModeEnum, TlsSniSocketFactory>();
    public static ConnectionSocketFactory getInstance(SslModeEnum sslMode) {
        if (!instances.containsKey(sslMode)) {
            instances.put(sslMode, new TlsSniSocketFactory(sslMode));
        }
        return instances.get(sslMode);
    }
    public static void forget() {
        instances.clear();
    }
    
    private final boolean secure;
    private final SSLCertificateSocketFactory sslSocketFactory;
    
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

    public TlsSniSocketFactory(SslModeEnum sslMode) {
        secure = sslMode == SslModeEnum.SECURE;
        if (secure) {
            sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory
                    .getDefault(MyPreferences.getConnectionTimeoutMs());
        } else {
            sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory
                    .getInsecure(MyPreferences.getConnectionTimeoutMs(), null);
            MyLog.i(this, "Insecure SSL allowed");
        }
    }
    
    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        return sslSocketFactory.createSocket();
    }

    @Override
    public Socket connectSocket(int timeout, Socket plain, HttpHost host, InetSocketAddress remoteAddr, InetSocketAddress localAddr, HttpContext context) throws IOException {
        MyLog.d(this, "Preparing direct SSL connection (without proxy) to " + host);
        
        // we'll rather use an SSLSocket directly
        plain.close();
        
        // create a plain SSL socket, but don't do hostname/certificate verification yet
        SSLSocket ssl = (SSLSocket)sslSocketFactory.createSocket(remoteAddr.getAddress(), host.getPort());
        
        // connect, set SNI, shake hands, verify, print connection info
        connectWithSNI(ssl, host.getHostName());

        return ssl;
    }

    @Override
    public Socket createLayeredSocket(Socket plain, String host, int port, HttpContext context) throws IOException {
        MyLog.d(this, "Preparing layered SSL connection (over proxy) to " + host);
        
        // create a layered SSL socket, but don't do hostname/certificate verification yet
        SSLSocket ssl = (SSLSocket)sslSocketFactory.createSocket(plain, host, port, true);

        // already connected, but verify host name again and print some connection info
        MyLog.w(this, "Setting SNI/TLSv1.2 will silently fail because the handshake is already done");
        connectWithSNI(ssl, host);

        return ssl;
    }
    
    private void connectWithSNI(SSLSocket ssl, String host) throws SSLPeerUnverifiedException {
        // set reasonable SSL/TLS settings before the handshake:
        // - enable all supported protocols
        ssl.setEnabledProtocols(ssl.getSupportedProtocols());
        
        MyLog.d(this, "Using documented SNI with host name " + host);
        sslSocketFactory.setHostname(ssl, host);

        // verify hostname and certificate
        SSLSession session = ssl.getSession();
        if (!session.isValid()) {
            MyLog.i(this, "Invalid session to host:'" + host + "'");
        }

        HostnameVerifier hostnameVerifier = secure ? new BrowserCompatHostnameVerifier() : new AllowAllHostnameVerifier();
        if (!hostnameVerifier.verify(host, session)) {
            throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);
        }

        MyLog.i(this, "Established " + session.getProtocol() + " connection with " + session.getPeerHost() +
                " using " + session.getCipherSuite());
    }
    
}