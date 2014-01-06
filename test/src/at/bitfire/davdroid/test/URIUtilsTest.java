package at.bitfire.davdroid.test;

import junit.framework.TestCase;
import at.bitfire.davdroid.URIUtils;

public class URIUtilsTest extends TestCase {

	public void testSanitize() {
		assertNull(URIUtils.sanitize(null));
		
		// escape "@"
		assertEquals("https://my%40server/my%40email.com/dir", URIUtils.sanitize("https://my@server/my@email.com/dir"));
		assertEquals("http://my%40server/my%40email.com/dir", URIUtils.sanitize("http://my@server/my@email.com/dir"));
		assertEquals("//my%40server/my%40email.com/dir", URIUtils.sanitize("//my@server/my@email.com/dir"));
		assertEquals("/my%40email.com/dir", URIUtils.sanitize("/my@email.com/dir"));
		assertEquals("my%40email.com/dir", URIUtils.sanitize("my@email.com/dir"));
		
		// escape ":" in path but not as port separator
		assertEquals("https://www.test.at:80/my%3afile.vcf", URIUtils.sanitize("https://www.test.at:80/my:file.vcf"));
		assertEquals("http://www.test.at:80/my%3afile.vcf", URIUtils.sanitize("http://www.test.at:80/my:file.vcf"));
		assertEquals("//www.test.at:80/my%3afile.vcf", URIUtils.sanitize("//www.test.at:80/my:file.vcf"));
		assertEquals("/my%3afile.vcf", URIUtils.sanitize("/my:file.vcf"));
		assertEquals("my%3afile.vcf", URIUtils.sanitize("my:file.vcf"));
		
		// keep literal IPv6 addresses (only in host name)
		assertEquals("https://[1:2::1]/", URIUtils.sanitize("https://[1:2::1]/"));
		assertEquals("/%5b1%3a2%3a%3a1%5d/", URIUtils.sanitize("/[1:2::1]/"));
	}
}
