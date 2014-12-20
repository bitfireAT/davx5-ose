package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.net.URL;

import junit.framework.TestCase;
import at.bitfire.davdroid.test.Constants;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.methods.HttpOptions;
import ch.boye.httpclientandroidlib.client.methods.HttpUriRequest;
import ch.boye.httpclientandroidlib.client.protocol.HttpClientContext;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

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
		HttpUriRequest request = new HttpOptions(Constants.roboHydra.toURI());
		HttpResponse response = httpClient.execute(request);
		assertFalse(strategy.isRedirected(request, response, null));
	}
	
	public void testDefaultRedirection() throws Exception {
		final String newLocation = "/new-location";
		
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(new URL(Constants.roboHydra, "redirect/301?to=" + newLocation).toURI());
		HttpResponse response = httpClient.execute(request, context);
		assertTrue(strategy.isRedirected(request, response, context));
		
		HttpUriRequest redirected = strategy.getRedirect(request, response, context);
		assertEquals(new URL(Constants.roboHydra, newLocation).toURI(), redirected.getURI());
	}
	
	
	// error cases
	
	public void testMissingLocation() throws Exception {
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(new URL(Constants.roboHydra, "redirect/without-location").toURI());
		HttpResponse response = httpClient.execute(request, context);
		assertFalse(strategy.isRedirected(request, response, context));
	}
	
	public void testRelativeLocation() throws Exception {
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(new URL(Constants.roboHydra, "redirect/relative").toURI());
		HttpResponse response = httpClient.execute(request, context);
		assertTrue(strategy.isRedirected(request, response, context));
		
		HttpUriRequest redirected = strategy.getRedirect(request, response, context);
		assertEquals(new URL(Constants.roboHydra, "/new/location").toURI(), redirected.getURI());
	}
}
