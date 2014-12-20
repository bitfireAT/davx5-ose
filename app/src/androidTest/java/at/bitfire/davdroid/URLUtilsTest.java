/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid;

import java.net.URI;

import junit.framework.TestCase;
import at.bitfire.davdroid.URIUtils;


public class URLUtilsTest extends TestCase {
	
	/* RFC 1738 p17 HTTP URLs:
	hpath          = hsegment *[ "/" hsegment ]
	hsegment       = *[ uchar | ";" | ":" | "@" | "&" | "=" ]
	uchar          = unreserved | escape
	unreserved     = alpha | digit | safe | extra
	alpha          = lowalpha | hialpha
	lowalpha       = ...
	hialpha        = ...					
	digit          = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" |
	                 "8" | "9"
	safe           = "$" | "-" | "_" | "." | "+"
	extra          = "!" | "*" | "'" | "(" | ")" | ","
	escape         = "%" hex hex
	*/


	public void testEnsureTrailingSlash() throws Exception {
		assertEquals("/test/", URIUtils.ensureTrailingSlash("/test"));
		assertEquals("/test/", URIUtils.ensureTrailingSlash("/test/"));

		String	withoutSlash = "http://www.test.at/dav/collection",
				withSlash = withoutSlash + "/";
		assertEquals(new URI(withSlash), URIUtils.ensureTrailingSlash(new URI(withoutSlash)));
		assertEquals(new URI(withSlash), URIUtils.ensureTrailingSlash(new URI(withSlash)));
	}

	public void testParseURI() throws Exception {
		// don't escape valid characters
		String validPath = "/;:@&=$-_.+!*'(),";
		assertEquals(new URI("https://www.test.at:123" + validPath), URIUtils.parseURI("https://www.test.at:123" + validPath));
		assertEquals(new URI(validPath), URIUtils.parseURI(validPath));
		
		// keep literal IPv6 addresses (only in host name)
		assertEquals(new URI("https://[1:2::1]/"), URIUtils.parseURI("https://[1:2::1]/"));
		
		// ~ as home directory
		assertEquals(new URI("http://www.test.at/~user1/"), URIUtils.parseURI("http://www.test.at/~user1/"));
		assertEquals(new URI("http://www.test.at/~user1/"), URIUtils.parseURI("http://www.test.at/%7euser1/"));
		
		// @ in directory name
		assertEquals(new URI("http://www.test.at/user@server.com/"), URIUtils.parseURI("http://www.test.at/user@server.com/"));
		assertEquals(new URI("http://www.test.at/user@server.com/"), URIUtils.parseURI("http://www.test.at/user%40server.com/"));
	}
}
