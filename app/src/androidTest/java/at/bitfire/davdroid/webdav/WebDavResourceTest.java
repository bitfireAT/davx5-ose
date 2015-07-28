/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;

import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;

import javax.net.ssl.SSLPeerUnverifiedException;

import at.bitfire.davdroid.TestConstants;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;
import at.bitfire.davdroid.webdav.WebDavResource.PutMode;
import ezvcard.VCardVersion;
import lombok.Cleanup;

// tests require running robohydra!

public class WebDavResourceTest extends InstrumentationTestCase {
	final static byte[] SAMPLE_CONTENT = new byte[] { 1, 2, 3, 4, 5 };
	
	AssetManager assetMgr;
	CloseableHttpClient httpClient;
	
	WebDavResource baseDAV;
	WebDavResource davAssets,
		davCollection, davNonExistingFile, davExistingFile;

	@Override
	protected void setUp() throws Exception {
		httpClient = DavHttpClient.create();
				
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
		
		baseDAV = new WebDavResource(httpClient, TestConstants.roboHydra.resolve("/dav/"));
		davAssets = new WebDavResource(httpClient, TestConstants.roboHydra.resolve("assets/"));
		davCollection = new WebDavResource(httpClient, TestConstants.roboHydra.resolve("dav/"));

		davNonExistingFile = new WebDavResource(davCollection, "collection/new.file");
		davExistingFile = new WebDavResource(davCollection, "collection/existing.file");
	}
	
	@Override
	protected void tearDown() throws Exception {
		httpClient.close();
	}


	/* test feature detection */
	
	public void testOptions() throws Exception {
		String[]	davMethods = new String[] { "PROPFIND", "GET", "PUT", "DELETE", "REPORT" },
					davCapabilities = new String[] { "addressbook", "calendar-access" };
		
		WebDavResource capable = new WebDavResource(baseDAV);
		capable.options();
		for (String davMethod : davMethods)
			assertTrue(capable.supportsMethod(davMethod));
		for (String capability : davCapabilities)
			assertTrue(capable.supportsDAV(capability));
	}

	public void testPropfindCurrentUserPrincipal() throws Exception {
		davCollection.propfind(HttpPropfind.Mode.CURRENT_USER_PRINCIPAL);
		assertEquals(new URI("/dav/principals/users/test"), davCollection.getCurrentUserPrincipal());

        WebDavResource simpleFile = new WebDavResource(davAssets, "test.random");
		try {
			simpleFile.propfind(HttpPropfind.Mode.CURRENT_USER_PRINCIPAL);
			fail();
			
		} catch(DavException ex) {
		}
		assertNull(simpleFile.getCurrentUserPrincipal());
	}
		
	public void testPropfindHomeSets() throws Exception {
		WebDavResource dav = new WebDavResource(davCollection, "principals/users/test");
		dav.propfind(HttpPropfind.Mode.HOME_SETS);
		assertEquals(new URI("/dav/addressbooks/test/"), dav.getAddressbookHomeSet());
		assertEquals(new URI("/dav/calendars/test/"), dav.getCalendarHomeSet());
	}
	
	public void testPropfindAddressBooks() throws Exception {
		WebDavResource dav = new WebDavResource(davCollection, "addressbooks/test");
		dav.propfind(HttpPropfind.Mode.CARDDAV_COLLECTIONS);

		// there should be two address books
		assertEquals(3, dav.getMembers().size());

		// the first one is not an address book and not even a collection (referenced by relative URI)
		WebDavResource ab = dav.getMembers().get(0);
		assertEquals(TestConstants.roboHydra.resolve("/dav/addressbooks/test/useless-member"), ab.getLocation());
		assertEquals("useless-member", ab.getName());
		assertFalse(ab.isAddressBook());

		// the second one is a VCard4-capable address book (referenced by relative URI)
		ab = dav.getMembers().get(1);
		assertEquals(TestConstants.roboHydra.resolve("/dav/addressbooks/test/default-v4.vcf/"), ab.getLocation());
		assertEquals("default-v4.vcf", ab.getName());
		assertTrue(ab.isAddressBook());
		assertEquals(VCardVersion.V4_0, ab.getVCardVersion());

		// the third one is a (non-VCard4-capable) address book (referenced by an absolute URI)
		ab = dav.getMembers().get(2);
		assertEquals(new URI("https://my.server/absolute:uri/my-address-book/"), ab.getLocation());
		assertEquals("my-address-book", ab.getName());
		assertTrue(ab.isAddressBook());
		assertNull(ab.getVCardVersion());
	}
	
	public void testPropfindCalendars() throws Exception {
		WebDavResource dav = new WebDavResource(davCollection, "calendars/test");
		dav.propfind(Mode.CALDAV_COLLECTIONS);
		assertEquals(3, dav.getMembers().size());
		assertEquals("0xFF00FF", dav.getMembers().get(2).getColor());
		for (WebDavResource member : dav.getMembers()) {
			if (member.getName().contains(".ics"))
				assertTrue(member.isCalendar());
			else
				assertFalse(member.isCalendar());
			assertFalse(member.isAddressBook());
		}
	}
	
	public void testPropfindTrailingSlashes() throws Exception {
		final String principalOK = "/principals/ok";
		
		String requestPaths[] = {
			"/dav/collection-response-with-trailing-slash",
			"/dav/collection-response-with-trailing-slash/",
			"/dav/collection-response-without-trailing-slash",
			"/dav/collection-response-without-trailing-slash/"
		};
		
		for (String path : requestPaths) {
			WebDavResource davSlash = new WebDavResource(davCollection, new URI(path));
			davSlash.propfind(Mode.CARDDAV_COLLECTIONS);
			assertEquals(new URI(principalOK), davSlash.getCurrentUserPrincipal());
		}
	}

    public void testStrangeMemberNames() throws Exception {
        // construct a WebDavResource from a base collection and a member which is an encoded URL (see https://github.com/bitfireAT/davdroid/issues/482)
        WebDavResource dav = new WebDavResource(davCollection, "http%3A%2F%2Fwww.invalid.example%2Fm8%2Ffeeds%2Fcontacts%2Fmaria.mueller%2540gmail.com%2Fbase%2F5528abc5720cecc.vcf");
        dav.get("text/vcard");
    }
	
	
	/* test normal HTTP/WebDAV */
	
	public void testPropfindRedirection() throws Exception {
		// PROPFIND redirection
		WebDavResource redirected = new WebDavResource(baseDAV, new URI("/redirect/301?to=/dav/"));
		redirected.propfind(Mode.CURRENT_USER_PRINCIPAL);
		assertEquals("/dav/", redirected.getLocation().getPath());
	}
	
	public void testGet() throws Exception {
        WebDavResource simpleFile = new WebDavResource(davAssets, "test.random");
		simpleFile.get("*/*");
		@Cleanup InputStream is = assetMgr.open("test.random", AssetManager.ACCESS_STREAMING);
		byte[] expected = IOUtils.toByteArray(is);
		assertTrue(Arrays.equals(expected, simpleFile.getContent()));
	}
	
	public void testGetHttpsWithSni() throws Exception {
		WebDavResource file = new WebDavResource(httpClient, new URI("https://sni.velox.ch"));
		
		boolean	sniWorking = false;
		try {
			file.get("* /*");
			sniWorking = true; 
		} catch (SSLPeerUnverifiedException e) {
		}
		
		assertTrue(sniWorking);
	}
	
	public void testMultiGet() throws Exception {
		WebDavResource davAddressBook = new WebDavResource(davCollection, "addressbooks/default.vcf/");
		davAddressBook.multiGet(DavMultiget.Type.ADDRESS_BOOK, new String[] { "1.vcf", "2:3@my%40pc.vcf" });
		// queried address book has a name
		assertEquals("My Book", davAddressBook.getDisplayName());
		// there are two contacts
		assertEquals(2, davAddressBook.getMembers().size());
		// contact file names should be unescaped (yes, it's really named ...%40pc... to check double-encoding)
		assertEquals("1.vcf", davAddressBook.getMembers().get(0).getName());
		assertEquals("2:3@my%40pc.vcf", davAddressBook.getMembers().get(1).getName());
		// all contacts have some content
		for (WebDavResource member : davAddressBook.getMembers())
			assertNotNull(member.getContent());
	}

	public void testMultiGetWith404() throws Exception {
		WebDavResource davAddressBook = new WebDavResource(davCollection, "addressbooks/default-with-404.vcf/");
		try {
			davAddressBook.multiGet(DavMultiget.Type.ADDRESS_BOOK, new String[]{ "notexisting" });
			fail();
		} catch(NotFoundException e) {
			// addressbooks/default.vcf/notexisting doesn't exist,
			// so server responds with 404 which causes a NotFoundException
		}
	}
	
	public void testPutAddDontOverwrite() throws Exception {
		// should succeed on a non-existing file
		assertEquals("has-just-been-created", davNonExistingFile.put(SAMPLE_CONTENT, PutMode.ADD_DONT_OVERWRITE));
		
		// should fail on an existing file
		try {
			davExistingFile.put(SAMPLE_CONTENT, PutMode.ADD_DONT_OVERWRITE);
			fail();
		} catch(PreconditionFailedException ex) {
		}
	}
	
	public void testPutUpdateDontOverwrite() throws Exception {
		// should succeed on an existing file
		assertEquals("has-just-been-updated", davExistingFile.put(SAMPLE_CONTENT, PutMode.UPDATE_DONT_OVERWRITE));
		
		// should fail on a non-existing file (resource has been deleted on server, thus server returns 412)
		try {
			davNonExistingFile.put(SAMPLE_CONTENT, PutMode.UPDATE_DONT_OVERWRITE);
			fail();
		} catch(PreconditionFailedException ex) {
		}

		// should fail on existing file with wrong ETag (resource has changed on server, thus server returns 409)
		try {
			WebDavResource dav = new WebDavResource(davCollection, new URI("collection/existing.file?conflict=1"));
			dav.put(SAMPLE_CONTENT, PutMode.UPDATE_DONT_OVERWRITE);
			fail();
		} catch(ConflictException ex) {
		}
	}
	
	public void testDelete() throws Exception {
		// should succeed on an existing file
		davExistingFile.delete();
		
		// should fail on a non-existing file
		try {
			davNonExistingFile.delete();
			fail();
		} catch (NotFoundException e) {
		}
	}
	
	
	/* test CalDAV/CardDAV */
	
	
	/* special test */
	
	public void testGetSpecialURLs() throws Exception {
        WebDavResource dav = new WebDavResource(davAssets, "member-with:colon.vcf");
        try {
            dav.get("*/*");
            fail();
        } catch(NotFoundException e) {
            assertTrue(true);
        }
	}
	
}
