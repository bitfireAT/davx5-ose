/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.webdav;

import android.os.Build;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifierHC4;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import lombok.Cleanup;

public class TlsSniSocketFactory extends SSLConnectionSocketFactory {
	private static final String TAG = "davdroid.TLS_SNI";

	public static TlsSniSocketFactory getSocketFactory() {
		return new TlsSniSocketFactory(
				(SSLSocketFactory) SSLSocketFactory.getDefault(),
				new BrowserCompatHostnameVerifierHC4()      // use BrowserCompatHostnameVerifier to allow IP addresses in the Common Name
		);
	}

	// Android 5.0+ (API level21) provides reasonable default settings
	// but it still allows SSLv3
	// https://developer.android.com/about/versions/android-5.0-changes.html#ssl
	static String protocols[] = null, cipherSuites[] = null;
	static {
		try {
			@Cleanup SSLSocket socket = (SSLSocket)SSLSocketFactory.getDefault().createSocket();

			/* set reasonable protocol versions */
			// - enable all supported protocols (enables TLSv1.1 and TLSv1.2 on Android <5.0)
			// - remove all SSL versions (especially SSLv3) because they're insecure now
			List<String> protocols = new LinkedList<>();
			for (String protocol : socket.getSupportedProtocols())
				if (!protocol.toUpperCase().contains("SSL"))
					protocols.add(protocol);
			Log.v(TAG, "Setting allowed TLS protocols: " + StringUtils.join(protocols, ", "));
			TlsSniSocketFactory.protocols = protocols.toArray(new String[protocols.size()]);

			/* set reasonable cipher suites */
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				// choose secure cipher suites
				List<String> allowedCiphers = Arrays.asList(
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
                        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA");

				List<String> availableCiphers = Arrays.asList(socket.getSupportedCipherSuites());

				// preferred ciphers = allowed Ciphers \ availableCiphers
				HashSet<String> preferredCiphers = new HashSet<>(allowedCiphers);
				preferredCiphers.retainAll(availableCiphers);

				// add preferred ciphers to enabled ciphers
				// for maximum security, preferred ciphers should *replace* enabled ciphers,
				// but I guess for the security level of DAVdroid, disabling of insecure
				// ciphers should be a server-side task
				HashSet<String> enabledCiphers = preferredCiphers;
				enabledCiphers.addAll(new HashSet<>(Arrays.asList(socket.getEnabledCipherSuites())));

				Log.v(TAG, "Setting allowed TLS ciphers: " + StringUtils.join(enabledCiphers, ", "));
				TlsSniSocketFactory.cipherSuites = enabledCiphers.toArray(new String[enabledCiphers.size()]);
			}
		} catch (IOException e) {
		}
	}

	public TlsSniSocketFactory(SSLSocketFactory socketfactory, X509HostnameVerifier hostnameVerifier) {
		super(socketfactory, protocols, cipherSuites, hostnameVerifier);
	}

}
