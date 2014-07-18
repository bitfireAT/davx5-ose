/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import android.util.Log;
import at.bitfire.davdroid.Constants;
import ch.boye.httpclientandroidlib.client.config.RequestConfig;
import ch.boye.httpclientandroidlib.config.Registry;
import ch.boye.httpclientandroidlib.config.RegistryBuilder;
import ch.boye.httpclientandroidlib.conn.socket.ConnectionSocketFactory;
import ch.boye.httpclientandroidlib.conn.socket.PlainConnectionSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;
import ch.boye.httpclientandroidlib.impl.client.HttpClients;
import ch.boye.httpclientandroidlib.impl.conn.ManagedHttpClientConnectionFactory;
import ch.boye.httpclientandroidlib.impl.conn.PoolingHttpClientConnectionManager;

public class DavHttpClient {
	private final static String TAG = "davdroid.DavHttpClient";
	
	private final static RequestConfig defaultRqConfig;
	private final static Registry<ConnectionSocketFactory> socketFactoryRegistry;
		
	static {
		socketFactoryRegistry =	RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", TlsSniSocketFactory.INSTANCE)
				.build();
		
		// use request defaults from AndroidHttpClient
		defaultRqConfig = RequestConfig.copy(RequestConfig.DEFAULT)
				.setConnectTimeout(20*1000)
				.setSocketTimeout(45*1000)
				.setStaleConnectionCheckEnabled(false)
				.build();
	}


	public static CloseableHttpClient create(boolean disableCompression, boolean logTraffic) {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		// limits per DavHttpClient (= per DavSyncAdapter extends AbstractThreadedSyncAdapter)
		connectionManager.setMaxTotal(3);				// max.  3 connections in total
		connectionManager.setDefaultMaxPerRoute(2);		// max.  2 connections per host
		
		HttpClientBuilder builder = HttpClients.custom()
				.useSystemProperties()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(defaultRqConfig)
				.setRetryHandler(DavHttpRequestRetryHandler.INSTANCE)
				.setRedirectStrategy(DavRedirectStrategy.INSTANCE)
				.setUserAgent("DAVdroid/" + Constants.APP_VERSION)
				.disableCookieManagement();
		
		if (disableCompression) {
			Log.d(TAG, "Disabling compression for debugging purposes");
			builder = builder.disableContentCompression();
		}

		if (logTraffic)
			Log.d(TAG, "Logging network traffic for debugging purposes");
		ManagedHttpClientConnectionFactory.INSTANCE.wirelog.enableDebug(logTraffic);
		ManagedHttpClientConnectionFactory.INSTANCE.log.enableDebug(logTraffic);

		return builder.build();
	}

}
