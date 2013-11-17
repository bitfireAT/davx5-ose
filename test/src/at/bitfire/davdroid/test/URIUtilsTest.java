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

	public void testSanitize() {
		assertEquals("/my%40email.com/dir", URIUtils.sanitize("/my@email.com/dir"));
		assertEquals("my%3Afile.vcf", URIUtils.sanitize("my:file.vcf"));
	}
}
