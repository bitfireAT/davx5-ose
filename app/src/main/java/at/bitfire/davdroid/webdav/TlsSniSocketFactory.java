/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.util.Log;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifierHC4;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathValidatorException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class TlsSniSocketFactory implements LayeredConnectionSocketFactory {
	private static final String TAG = "davdroid.SNISocketFactory";
	
	public final static TlsSniSocketFactory INSTANCE = new TlsSniSocketFactory();

	private final static SSLSocketFactory sslSocketFactory = (SSLSocketFactory)SSLSocketFactory.getDefault();
	private final static HostnameVerifier hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();

	
	/*
	For TLS connections without HTTPS (CONNECT) proxy:
	   1) socket = createSocket() is called
	   2) connectSocket(socket) is called which creates a new TLS connection (but no handshake yet)
	   3) reasonable encryption settings are applied to socket
	   4) SNI is set up for socket
	   5) handshake and certificate/host name verification
	
	Layered sockets are used with HTTPS (CONNECT) proxies:
	   1) plain = createSocket() is called
	   2) the plain socket is connected to http://proxy:8080
	   3) a CONNECT request is sent to the proxy and the response is parsed
	   4) socket = createLayeredSocket(plain) is called to "upgrade" the plain connection to a TLS connection (but no handshake yet)
	   5) SNI is set up for socket
	   6) handshake and certificate/host name verification
	*/

	@Override
	public Socket createSocket(HttpContext context) throws IOException {
		return sslSocketFactory.createSocket();
	}

	@Override
	public Socket connectSocket(int timeout, Socket sock, HttpHost host, InetSocketAddress remoteAddr, InetSocketAddress localAddr, HttpContext context) throws IOException {
		Log.d(TAG, "Preparing direct TLS connection to " + host);
		final SSLSocket socket = (SSLSocket)((sock != null) ? sock : createSocket(context));
		connectAndVerify(socket, host.getHostName());
		return socket;
	}

	@Override
	public Socket createLayeredSocket(Socket plain, String host, int port, HttpContext context) throws IOException {
		Log.d(TAG, "Preparing proxied TLS connection to " + host);
		final SSLSocket socket = (SSLSocket)sslSocketFactory.createSocket(plain, host, port, true);
		connectAndVerify(socket, host);
		return socket;
	}


	/**
	 * Establishes a connection to an unconnected SSLSocket:
	 *   - prepare socket
	 *   - set SNI host name
	 *   - verify host name
	 *   - verify certificate
	 * @param socket    unconnected SSLSocket
	 * @param host      host name for SNI
	 * @throws SSLPeerUnverifiedException
	 */
	private void connectAndVerify(SSLSocket socket, String host) throws IOException, SSLPeerUnverifiedException {
		// prepare socket (set encryption etc.)
		prepareSSLSocket(socket);

		// set SNI hostname
		setSniHostname(socket, host);

		// TLS handshake
		socket.startHandshake();

		// verify hostname and certificate
		SSLSession session = socket.getSession();
		if (!hostnameVerifier.verify(host, session))
			throw new SSLPeerUnverifiedException(host);

		Log.d(TAG, "Established " + session.getProtocol() + " connection with " + session.getPeerHost() +
				" using " + session.getCipherSuite());
	}


	/**
	 * Prepares a TLS/SSL connetion socket by:
	 *   - setting the default TrustManager (as we have created an "insecure" connection to avoid handshake problems before)
	 *   - setting reasonable TLS protocol versions
	 *   - setting reasonable cipher suites (if required)
	 * @param socket   unconnected SSLSocket to prepare
	 */
	@SuppressLint("DefaultLocale")
	private void prepareSSLSocket(SSLSocket socket) {
		// Android 5.0+ (API level21) provides reasonable default settings
		// but it still allows SSLv3
		// https://developer.android.com/about/versions/android-5.0-changes.html#ssl

		/* set reasonable protocol versions */
		// - enable all supported protocols (enables TLSv1.1 and TLSv1.2 on Android <5.0)
		// - remove all SSL versions (especially SSLv3) because they're insecure now
		List<String> protocols = new LinkedList<String>();
		for (String protocol : socket.getSupportedProtocols())
			if (!protocol.toUpperCase().contains("SSL"))
				protocols.add(protocol);
		Log.v(TAG, "Setting allowed TLS protocols: " + StringUtils.join(protocols, ", "));
		socket.setEnabledProtocols(protocols.toArray(new String[0]));

		/* set reasonable cipher suites */
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			// choose secure cipher suites
			List<String> allowedCiphers = Arrays.asList(new String[] {
				// allowed secure ciphers according to NIST.SP.800-52r1.pdf Section 3.3.1 (see docs directory)
				// TLS 1.2
				"TLS_RSA_WITH_AES_256_GCM_SHA384",
				"TLS_RSA_WITH_AES_128_GCM_SHA256",
				"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
				"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
				"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
				"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
				"TLS_ECHDE_RSA_WITH_AES_128_GCM_SHA256",
				// maximum interoperability
				"TLS_RSA_WITH_3DES_EDE_CBC_SHA",
				"TLS_RSA_WITH_AES_128_CBC_SHA",
				// additionally
				"TLS_RSA_WITH_AES_256_CBC_SHA",
				"TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
				"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
				"TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
				"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
			});
			
			List<String> availableCiphers = Arrays.asList(socket.getSupportedCipherSuites());
			
			// preferred ciphers = allowed Ciphers \ availableCiphers
			HashSet<String> preferredCiphers = new HashSet<String>(allowedCiphers);
			preferredCiphers.retainAll(availableCiphers);
			
			// add preferred ciphers to enabled ciphers
			// for maximum security, preferred ciphers should *replace* enabled ciphers,
			// but I guess for the security level of DAVdroid, disabling of insecure
			// ciphers should be a server-side task
			HashSet<String> enabledCiphers = preferredCiphers;
			enabledCiphers.addAll(new HashSet<String>(Arrays.asList(socket.getEnabledCipherSuites())));
			
			Log.v(TAG, "Setting allowed TLS ciphers: " + StringUtils.join(enabledCiphers, ", "));
			socket.setEnabledCipherSuites(enabledCiphers.toArray(new String[0]));
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private void setSniHostname(SSLSocket socket, String hostName) {
		// set SNI host name
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && sslSocketFactory instanceof SSLCertificateSocketFactory) {
			Log.d(TAG, "Using documented SNI with host name " + hostName);
			((SSLCertificateSocketFactory)sslSocketFactory).setHostname(socket, hostName);
		} else {
			Log.d(TAG, "No documented SNI support on Android <4.2, trying reflection method with host name " + hostName);
			try {
				java.lang.reflect.Method setHostnameMethod = socket.getClass().getMethod("setHostname", String.class);
				setHostnameMethod.invoke(socket, hostName);
			} catch (Exception e) {
				Log.w(TAG, "SNI not useable", e);
			}
		}
	}
	
}
