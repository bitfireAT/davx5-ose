/*
 * Copyright (c) 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.cert.CertPathValidatorException;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

import android.util.Log;
import junit.framework.TestCase;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.HttpHost;

import lombok.Cleanup;

public class TlsSniSocketFactoryTest extends TestCase {
	private static final String TAG = "davdroid.TlsSniSocketFactoryTest";

	TlsSniSocketFactory factory = TlsSniSocketFactory.INSTANCE;

	private InetSocketAddress sampleTlsEndpoint;

	@Override
	protected void setUp() {
		// sni.velox.ch is used to test SNI (without SNI support, the certificate is invalid)
		sampleTlsEndpoint = new InetSocketAddress("sni.velox.ch", 443);
	}

	public void testCreateSocket() {
		try {
			@Cleanup SSLSocket socket = factory.createSocket(null);
			assertFalse(socket.isConnected());
		} catch (IOException e) {
			fail();
		}
	}

	public void testConnectSocket() {
		try {
			@Cleanup SSLSocket socket = factory.createSocket(null);

			factory.connectSocket(1000, socket, new HttpHost(sampleTlsEndpoint.getHostName()), sampleTlsEndpoint, null, null);
		} catch (IOException e) {
			Log.e(TAG, "I/O exception", e);
			fail();
		}
	}

	public void testCreateLayeredSocket() {
		try {
			// connect plain socket first
			@Cleanup Socket plain = new Socket();
			plain.connect(sampleTlsEndpoint);
			assertTrue(plain.isConnected());

			// then create TLS socket on top of it and establish TLS Connection
			@Cleanup SSLSocket socket = factory.createLayeredSocket(plain, sampleTlsEndpoint.getHostName(), sampleTlsEndpoint.getPort(), null);
			assertTrue(socket.isConnected());

		} catch (IOException e) {
			Log.e(TAG, "I/O exception", e);
			fail();
		}
	}

	public void testSetTlsParameters() throws IOException {
		@Cleanup SSLSocket socket = factory.createSocket(null);
		factory.setTlsParameters(socket);

		String enabledProtocols[] = socket.getEnabledProtocols();
		// SSL (all versions) should be disabled
		for (String protocol : enabledProtocols)
			assertFalse(protocol.contains("SSL"));
		// TLS v1+ should be enabled
		assertTrue(ArrayUtils.contains(enabledProtocols, "TLSv1"));
		assertTrue(ArrayUtils.contains(enabledProtocols, "TLSv1.1"));
		assertTrue(ArrayUtils.contains(enabledProtocols, "TLSv1.2"));
	}


	public void testHostnameNotInCertificate() {
		try {
			// host with certificate that doesn't match host name
			// use the IP address as host name because IP addresses are usually not in the certificate subject
			InetSocketAddress host = new InetSocketAddress(sampleTlsEndpoint.getAddress().getHostAddress(), 443);

			@Cleanup SSLSocket socket = factory.connectSocket(0, null, new HttpHost(host.getHostName()), host, null, null);
			fail();
		} catch (IOException e) {
			assertFalse(ExceptionUtils.indexOfType(e, SSLPeerUnverifiedException.class) == -1);
		}
	}

	public void testUntrustedCertificate() {
		try {
			// host with certificate that is not trusted by default
			InetSocketAddress host = new InetSocketAddress("cacert.org", 443);

			@Cleanup SSLSocket socket = factory.connectSocket(0, null, new HttpHost(host.getHostName()), host, null, null);
			fail();
		} catch (IOException e) {
			assertFalse(ExceptionUtils.indexOfType(e, CertPathValidatorException.class) == -1);
		}
	}

}
