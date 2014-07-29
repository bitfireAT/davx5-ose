/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;

import lombok.Cleanup;

import org.apache.commons.io.IOUtils;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.test.Constants;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavHttpClient;
import at.bitfire.davdroid.webdav.DavMultiget;
import at.bitfire.davdroid.webdav.HttpException;
import at.bitfire.davdroid.webdav.HttpPropfind;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;
import at.bitfire.davdroid.webdav.NotFoundException;
import at.bitfire.davdroid.webdav.PreconditionFailedException;
import at.bitfire.davdroid.webdav.WebDavResource;
import at.bitfire.davdroid.webdav.WebDavResource.PutMode;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;

// tests require running robohydra!

public class WebDavResourceTest extends InstrumentationTestCase {
	static byte[] SAMPLE_CONTENT = new byte[] { 1, 2, 3, 4, 5 };
	
	final static String PATH_SIMPLE_FILE = "collection/new.file";
	
	AssetManager assetMgr;
	CloseableHttpClient httpClient;
	
	WebDavResource baseDAV;
	WebDavResource simpleFile,
		davCollection, davNonExistingFile, davExistingFile,
		davInvalid;

	@Override
	protected void setUp() throws Exception {
		httpClient = DavHttpClient.create(true, true);
				
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
		
		baseDAV = new WebDavResource(httpClient, Constants.roboHydra.resolve("/dav/"));
		
		simpleFile = new WebDavResource(httpClient, new URI(Constants.ROBOHYDRA_BASE + "assets/test.random"));
		
		davCollection = new WebDavResource(httpClient, new URI(Constants.ROBOHYDRA_BASE + "dav/"));
		davNonExistingFile = new WebDavResource(davCollection, "collection/new.file");
		davExistingFile = new WebDavResource(davCollection, "collection/existing.file");
		
		davInvalid = new WebDavResource(httpClient, new URI(Constants.ROBOHYDRA_BASE + "dav-invalid/"));
	}
	
	@Override
	protected void tearDown() throws IOException {
		httpClient.close();
	}


	/* test feature detection */
	
	public void testOptions() throws URISyntaxException, IOException, HttpException {
		String[]	davMethods = new String[] { "PROPFIND", "GET", "PUT", "DELETE", "REPORT" },
					davCapabilities = new String[] { "addressbook", "calendar-access" };
		
		WebDavResource capable = new WebDavResource(baseDAV);
		capable.options();
		for (String davMethod : davMethods)
			assert(capable.supportsMethod(davMethod));
		for (String capability : davCapabilities)
			assert(capable.supportsDAV(capability));
	}

	public void testPropfindCurrentUserPrincipal() throws IOException, HttpException, DavException {
		davCollection.propfind(HttpPropfind.Mode.CURRENT_USER_PRINCIPAL);
		assertEquals("/dav/principals/users/test", davCollection.getCurrentUserPrincipal());
		
		try {
			simpleFile.propfind(HttpPropfind.Mode.CURRENT_USER_PRINCIPAL);
			fail();
			
		} catch(DavException ex) {
		}
		assertNull(simpleFile.getCurrentUserPrincipal());
	}
		
	public void testPropfindHomeSets() throws IOException, HttpException, DavException {
		WebDavResource dav = new WebDavResource(davCollection, "principals/users/test");
		dav.propfind(HttpPropfind.Mode.HOME_SETS);
		assertEquals("/dav/addressbooks/test", dav.getAddressbookHomeSet());
		assertEquals("/dav/calendars/test/", dav.getCalendarHomeSet());
	}
	
	public void testPropfindAddressBooks() throws IOException, HttpException, DavException {
		WebDavResource dav = new WebDavResource(davCollection, "addressbooks/test");
		dav.propfind(HttpPropfind.Mode.CARDDAV_COLLECTIONS);
		assertEquals(2, dav.getMembers().size());
		for (WebDavResource member : dav.getMembers()) {
			if (member.getName().equals("default-v4.vcf"))
				assertTrue(member.isAddressBook());
			else
				assertFalse(member.isAddressBook());
			assertFalse(member.isCalendar());
		}
	}
	
	public void testPropfindCalendars() throws IOException, HttpException, DavException {
		WebDavResource dav = new WebDavResource(davCollection, "calendars/test");
		dav.propfind(HttpPropfind.Mode.CALDAV_COLLECTIONS);
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
	
	
	/* test normal HTTP/WebDAV */
	
	public void testPropfindRedirection() throws URISyntaxException, IOException, DavException, HttpException {
		// PROPFIND redirection
		WebDavResource redirected = new WebDavResource(baseDAV, "/redirect/301?to=/dav/");
		redirected.propfind(Mode.CURRENT_USER_PRINCIPAL);
		assertEquals("/dav/", redirected.getLocation().getPath());
	}
	
	public void testGet() throws URISyntaxException, IOException, HttpException, DavException {
		simpleFile.get();
		@Cleanup InputStream is = assetMgr.open("test.random", AssetManager.ACCESS_STREAMING);
		byte[] expected = IOUtils.toByteArray(is);
		assertTrue(Arrays.equals(expected, simpleFile.getContent()));
	}
	
	public void testGetHttpsWithSni() throws URISyntaxException, HttpException, IOException, DavException {
		WebDavResource file = new WebDavResource(httpClient, new URI("https://sni.velox.ch"));
		
		boolean	sniWorking = false;
		try {
			file.get();
			sniWorking = true; 
		} catch (SSLPeerUnverifiedException e) {
		}
		
		assertTrue(sniWorking);
	}
	
	public void testMultiGet() throws DavException, IOException, HttpException {
		WebDavResource davAddressBook = new WebDavResource(davCollection, "addressbooks/default.vcf");
		davAddressBook.multiGet(DavMultiget.Type.ADDRESS_BOOK, new String[] { "1.vcf", "2.vcf" });
		assertEquals(2, davAddressBook.getMembers().size());
		for (WebDavResource member : davAddressBook.getMembers()) {
			assertNotNull(member.getContent());
		}
	}
	
	public void testPutAddDontOverwrite() throws IOException, HttpException {
		// should succeed on a non-existing file
		assertEquals("has-just-been-created", davNonExistingFile.put(SAMPLE_CONTENT, PutMode.ADD_DONT_OVERWRITE));
		
		// should fail on an existing file
		try {
			davExistingFile.put(SAMPLE_CONTENT, PutMode.ADD_DONT_OVERWRITE);
			fail();
		} catch(PreconditionFailedException ex) {
		}
	}
	
	public void testPutUpdateDontOverwrite() throws IOException, HttpException {
		// should succeed on an existing file
		assertEquals("has-just-been-updated", davExistingFile.put(SAMPLE_CONTENT, PutMode.UPDATE_DONT_OVERWRITE));
		
		// should fail on a non-existing file
		try {
			davNonExistingFile.put(SAMPLE_CONTENT, PutMode.UPDATE_DONT_OVERWRITE);
			fail();
		} catch(PreconditionFailedException ex) {
		}
	}
	
	public void testDelete() throws IOException, HttpException {
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
	
	public void testInvalidURLs() throws IOException, HttpException, DavException {
		WebDavResource dav = new WebDavResource(davInvalid, "addressbooks/user%40domain/");
		dav.propfind(HttpPropfind.Mode.CARDDAV_COLLECTIONS);
		List<WebDavResource> members = dav.getMembers();
		assertEquals(2, members.size());
		assertEquals(Constants.ROBOHYDRA_BASE + "dav/addressbooks/user%40domain/My%20Contacts%3a1.vcf/", members.get(0).getLocation().toString());
		assertEquals("HTTPS://example.com/user%40domain/absolute-url.vcf", members.get(1).getLocation().toString());
	}
	
}
