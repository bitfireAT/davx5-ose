/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.webdav;

import junit.framework.TestCase;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

import at.bitfire.davdroid.TestConstants;

public class DavRedirectStrategyTest extends TestCase {
	
	CloseableHttpClient httpClient;
	DavRedirectStrategy strategy = DavRedirectStrategy.INSTANCE;
	
	@Override
	protected void setUp() {
		httpClient = HttpClientBuilder.create()
				.useSystemProperties()
				.disableRedirectHandling()
				.build();
	}
	
	@Override
	protected void tearDown() throws IOException {
		httpClient.close();
	}
	
	
	// happy cases
	
	public void testNonRedirection() throws Exception {
		HttpUriRequest request = new HttpOptions(TestConstants.roboHydra);
		HttpResponse response = httpClient.execute(request);
		assertFalse(strategy.isRedirected(request, response, null));
	}
	
	public void testDefaultRedirection() throws Exception {
		final String newLocation = "/new-location";
		
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(TestConstants.roboHydra.resolve("redirect/301?to=" + newLocation));
		HttpResponse response = httpClient.execute(request, context);
		assertTrue(strategy.isRedirected(request, response, context));
		
		HttpUriRequest redirected = strategy.getRedirect(request, response, context);
		assertEquals(TestConstants.roboHydra.resolve(newLocation), redirected.getURI());
	}
	
	
	// error cases
	
	public void testMissingLocation() throws Exception {
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(TestConstants.roboHydra.resolve("redirect/without-location"));
		HttpResponse response = httpClient.execute(request, context);
		assertFalse(strategy.isRedirected(request, response, context));
	}
	
	public void testRelativeLocation() throws Exception {
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(TestConstants.roboHydra.resolve("redirect/relative"));
		HttpResponse response = httpClient.execute(request, context);
		assertTrue(strategy.isRedirected(request, response, context));
		
		HttpUriRequest redirected = strategy.getRedirect(request, response, context);
		assertEquals(TestConstants.roboHydra.resolve("/new/location"), redirected.getURI());
	}
}
