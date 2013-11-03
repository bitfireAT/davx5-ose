package at.bitfire.davdroid.webdav.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpException;

import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.webdav.WebDavResource;

public class WebDavResourceTest extends InstrumentationTestCase {
	URI davBaseURI, uriWithoutDAV, uriWithRedirection;
	final static String davUsername = "test", davPassword = "test";

	protected void setUp() throws Exception {
		davBaseURI = new URI("https://wurd.dev001.net/radicale/test/");
		uriWithoutDAV = new URI("http://www.apache.org");
		uriWithRedirection = new URI("http://wurd.dev001.net/public/");
	}
	
	
	public void testGet() throws URISyntaxException, IOException, HttpException {
		WebDavResource dav = new WebDavResource(uriWithoutDAV, "", "", false, true);
		dav.get();
		InputStream is = dav.getContent();
		assertNotNull(is);
	}
	
	public void testTrailingSlash() throws URISyntaxException {
		WebDavResource dav = new WebDavResource(new URI("http://server/path"), "", "", false, true);
		assertEquals("/path/", dav.getLocation().getPath());
	}	
	
	public void testOptions() throws URISyntaxException, IOException, HttpException {
		String[]	davMethods = new String[] { "PROPFIND", "PUT", "DELETE" },
					davCapabilities = new String[] { "addressbook", "calendar-access" };
		
		// server without DAV
		WebDavResource dav = new WebDavResource(uriWithoutDAV, "", "", false, true);
		dav.options();
		for (String method : davMethods)
			assertFalse(dav.supportsMethod(method));
		for (String capability : davCapabilities)
			assertFalse(dav.supportsDAV(capability));
		
		// server with DAV
		dav = new WebDavResource(davBaseURI, davUsername, davPassword, false, true);
		dav.options();
		for (String davMethod : davMethods)
			assert(dav.supportsMethod(davMethod));
		for (String capability : davCapabilities)
			assert(dav.supportsDAV(capability));
	}

	public void testRedirections() throws URISyntaxException, IOException {
		WebDavResource dav = new WebDavResource(uriWithRedirection, "", "", false, true);
		try {
			dav.options();
		} catch (HttpException e) {
			return;
		}
		fail();
	}
}
