/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import android.annotation.SuppressLint;
import android.util.Log;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;


public class DavHttpClient {
	private final static String TAG = "davdroid.DavHttpClient";
	
	private final static RequestConfig defaultRqConfig;
	private final static Registry<ConnectionSocketFactory> socketFactoryRegistry;
		
	static {
		socketFactoryRegistry =	RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", TlsSniSocketFactory.getSocketFactory())
				.build();
		
		// use request defaults from AndroidHttpClient
		defaultRqConfig = RequestConfig.copy(RequestConfig.DEFAULT)
				.setConnectTimeout(20*1000)
				.setSocketTimeout(45*1000)
				.setStaleConnectionCheckEnabled(false)
				.build();
	}


	@SuppressLint("LogTagMismatch")
    public static CloseableHttpClient create() {
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
				.setUserAgent("DAVdroid/" + BuildConfig.VERSION_NAME);
		
		if (Log.isLoggable("Wire", Log.DEBUG)) {
			Log.i(TAG, "Wire logging active, disabling HTTP compression");
			builder = builder.disableContentCompression();
		}

        return builder.build();
	}

}
