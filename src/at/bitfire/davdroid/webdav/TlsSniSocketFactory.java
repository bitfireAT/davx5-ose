/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import android.annotation.TargetApi;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.conn.socket.LayeredConnectionSocketFactory;
import ch.boye.httpclientandroidlib.conn.ssl.BrowserCompatHostnameVerifier;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class TlsSniSocketFactory implements LayeredConnectionSocketFactory {
	private static final String TAG = "davdroid.SNISocketFactory";
	
	final static TlsSniSocketFactory INSTANCE = new TlsSniSocketFactory();
	
	private final static SSLCertificateSocketFactory sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(0);
	private final static HostnameVerifier hostnameVerifier = new BrowserCompatHostnameVerifier();
	

	@Override
	public Socket createSocket(HttpContext context) throws IOException {
		return sslSocketFactory.createSocket();
	}

	@Override
	public Socket connectSocket(int timeout, Socket socket, HttpHost host, InetSocketAddress remoteAddr, InetSocketAddress localAddr, HttpContext context) throws IOException {
		// we'll rather create a new socket
		socket.close();
		
		// create and connect SSL socket, but don't do hostname/certificate verification yet
		SSLSocket ssl = (SSLSocket)sslSocketFactory.createSocket(remoteAddr.getAddress(), host.getPort());
		
		// set up SNI before the handshake
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			// Android 4.2+, use documented way to set SNI host name
			Log.d(TAG, "Setting SNI hostname");
			sslSocketFactory.setHostname(ssl, host.getHostName());
		} else {
			Log.d(TAG, "No documented SNI support on Android <4.2, trying with reflection");
			try {
				java.lang.reflect.Method setHostnameMethod = ssl.getClass().getMethod("setHostname", String.class);
				setHostnameMethod.invoke(ssl, host.getHostName());
			} catch (Exception e) {
				Log.w(TAG, "SNI not useable", e);
			}
		}
		
		// verify hostname and certificate
		SSLSession session = ssl.getSession();
		if (!hostnameVerifier.verify(host.getHostName(), session))
			throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);
		
		Log.i(TAG, "Established " + session.getProtocol() + " connection with " + session.getPeerHost() +
				" using " + session.getCipherSuite());

		return ssl;
	}

	
	// TLS layer

	@Override
	public Socket createLayeredSocket(Socket plainSocket, String host, int port, HttpContext context) throws IOException, UnknownHostException {
		Log.wtf(TAG, "createLayeredSocket should never be called");
		return plainSocket;
	}
}
