package at.bitfire.davdroid.webdav;

import java.io.IOException;

import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.test.Constants;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.methods.HttpOptions;
import ch.boye.httpclientandroidlib.client.methods.HttpUriRequest;
import ch.boye.httpclientandroidlib.client.protocol.HttpClientContext;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

public class DavRedirectStrategyTest extends InstrumentationTestCase {
	
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
		HttpUriRequest request = new HttpOptions(Constants.roboHydra);
		HttpResponse response = httpClient.execute(request);
		assertFalse(strategy.isRedirected(request, response, null));
	}
	
	public void testDefaultRedirection() throws Exception {
		final String newLocation = "/new-location";
		
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(Constants.roboHydra.resolve("redirect/301?to=" + newLocation));
		HttpResponse response = httpClient.execute(request, context);
		assertTrue(strategy.isRedirected(request, response, context));
		
		HttpUriRequest redirected = strategy.getRedirect(request, response, context);
		assertEquals(Constants.roboHydra.resolve(newLocation), redirected.getURI());
	}
	
	
	// error cases
	
	public void testMissingLocation() throws Exception {
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(Constants.roboHydra.resolve("redirect/without-location"));
		HttpResponse response = httpClient.execute(request, context);
		assertFalse(strategy.isRedirected(request, response, context));
	}
	
	public void testRelativeLocation() throws Exception {
		HttpContext context = HttpClientContext.create();
		HttpUriRequest request = new HttpOptions(Constants.roboHydra.resolve("redirect/relative"));
		HttpResponse response = httpClient.execute(request, context);
		assertTrue(strategy.isRedirected(request, response, context));
		
		HttpUriRequest redirected = strategy.getRedirect(request, response, context);
		assertEquals(Constants.roboHydra.resolve("/new/location"), redirected.getURI());
	}
}
