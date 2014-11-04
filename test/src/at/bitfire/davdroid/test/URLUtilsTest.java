/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.test;

import java.net.URL;

import junit.framework.TestCase;
import at.bitfire.davdroid.URLUtils;

public class URLUtilsTest extends TestCase {

	public void testEnsureTrailingSlash() throws Exception {
		assertEquals("/test/", URLUtils.ensureTrailingSlash("/test"));
		assertEquals("/test/", URLUtils.ensureTrailingSlash("/test/"));

		String	withoutSlash = "http://www.test.at/dav/collection",
				withSlash = withoutSlash + "/";
		assertEquals(new URL(withSlash), URLUtils.ensureTrailingSlash(new URL(withoutSlash)));
		assertEquals(new URL(withSlash), URLUtils.ensureTrailingSlash(new URL(withSlash)));
	}

	public void testSanitize() {
		assertNull(URLUtils.sanitize(null));
		
		// escape "@"
		assertEquals("https://my%40server/my%40email.com/dir", URLUtils.sanitize("https://my@server/my@email.com/dir"));
		assertEquals("http://my%40server/my%40email.com/dir", URLUtils.sanitize("http://my@server/my@email.com/dir"));
		assertEquals("//my%40server/my%40email.com/dir", URLUtils.sanitize("//my@server/my@email.com/dir"));
		assertEquals("/my%40email.com/dir", URLUtils.sanitize("/my@email.com/dir"));
		assertEquals("my%40email.com/dir", URLUtils.sanitize("my@email.com/dir"));
		
		// escape ":" in path but not as port separator
		assertEquals("https://www.test.at:80/my%3afile.vcf", URLUtils.sanitize("https://www.test.at:80/my:file.vcf"));
		assertEquals("http://www.test.at:80/my%3afile.vcf", URLUtils.sanitize("http://www.test.at:80/my:file.vcf"));
		assertEquals("//www.test.at:80/my%3afile.vcf", URLUtils.sanitize("//www.test.at:80/my:file.vcf"));
		assertEquals("/my%3afile.vcf", URLUtils.sanitize("/my:file.vcf"));
		assertEquals("my%3afile.vcf", URLUtils.sanitize("my:file.vcf"));
		
		// keep literal IPv6 addresses (only in host name)
		assertEquals("https://[1:2::1]/", URLUtils.sanitize("https://[1:2::1]/"));
		assertEquals("/%5b1%3a2%3a%3a1%5d/", URLUtils.sanitize("/[1:2::1]/"));
	}
}
