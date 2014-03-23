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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.conn.socket.LayeredConnectionSocketFactory;
import ch.boye.httpclientandroidlib.conn.ssl.BrowserCompatHostnameVerifier;
import ch.boye.httpclientandroidlib.protocol.HttpContext;
import android.annotation.TargetApi;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class TlsSniSocketFactory implements LayeredConnectionSocketFactory {
	private static final String TAG = "davdroid.SNISocketFactory";
	
	final static HostnameVerifier hostnameVerifier = new BrowserCompatHostnameVerifier();
	
	final static TlsSniSocketFactory INSTANCE = new TlsSniSocketFactory();
	
	
	// Plain TCP/IP (layer below TLS)

	@Override
	public Socket createSocket(HttpContext context) throws IOException {
		return null;
	}

	@Override
	public Socket connectSocket(int timeout, Socket socket, HttpHost host, InetSocketAddress remoteAddr, InetSocketAddress localAddr, HttpContext context)
			throws IOException {
		return null;
	}

	
	// TLS layer

	@Override
	public Socket createLayeredSocket(Socket plainSocket, String host, int port, HttpContext context) throws IOException, UnknownHostException {
		plainSocket.close();
		
		// create and connect SSL socket, but don't do hostname/certificate verification yet
		SSLCertificateSocketFactory sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(0);
		SSLSocket ssl = (SSLSocket)sslSocketFactory.createSocket(InetAddress.getByName(host), port);
		
		// set up SNI before the handshake
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			Log.d(TAG, "Setting SNI hostname");
			sslSocketFactory.setHostname(ssl, host);
		} else
			Log.i(TAG, "No SNI support below Android 4.2!");
		
		// verify hostname and certificate
		SSLSession session = ssl.getSession();
		if (!hostnameVerifier.verify(host, session))
			throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);
		
		Log.i(TAG, "Established " + session.getProtocol() + " connection with " + session.getPeerHost() +
				" using " + session.getCipherSuite());

		return ssl;
	}
}
