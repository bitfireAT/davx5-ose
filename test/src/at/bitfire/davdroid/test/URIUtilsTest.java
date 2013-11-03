package at.bitfire.davdroid.test;

import java.net.URI;
import java.net.URISyntaxException;

import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.URIUtils;

public class URIUtilsTest extends InstrumentationTestCase {
	final static String
		ROOT_URI = "http://server/",
		BASE_URI = ROOT_URI + "dir/";
	URI baseURI;
	
	@Override
	protected void setUp() throws Exception {
		baseURI = new URI(BASE_URI);
	}

	
	public void testIsSame() throws URISyntaxException {
		assertTrue(URIUtils.isSame(new URI(ROOT_URI + "my@email/"), new URI(ROOT_URI + "my%40email/")));
	}

	public void testResolve() {
		// resolve absolute URL
		assertEquals(ROOT_URI + "file", URIUtils.resolve(baseURI, "/file").toString());
		
		// resolve relative URL (default case)
		assertEquals(BASE_URI + "file", URIUtils.resolve(baseURI, "file").toString());
		
		// resolve relative URL with special characters
		assertEquals(BASE_URI + "fi:le", URIUtils.resolve(baseURI, "fi:le").toString());
		assertEquals(BASE_URI + "fi@le", URIUtils.resolve(baseURI, "fi@le").toString());
		
		// resolve URL with other schema
		assertEquals("https://server", URIUtils.resolve(baseURI, "https://server").toString());
	}
}
