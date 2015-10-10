/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import at.bitfire.davdroid.TestConstants;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.resource.ServerInfo.ResourceInfo;

public class DavResourceFinderTest extends InstrumentationTestCase {
	
	DavResourceFinder finder;

	@Override
	protected void setUp() {
		finder = new DavResourceFinder(getInstrumentation().getContext());
	}
	
	@Override
	protected void tearDown() throws IOException {
	}
	

	public void testFindResourcesRobohydra() throws Exception {
		ServerInfo info = new ServerInfo(new URI(TestConstants.ROBOHYDRA_BASE), "test", "test", true);
		finder.findResources(info);
		
		/*** CardDAV ***/
		List<ResourceInfo> collections = info.getAddressBooks();
		// two address books
		assertEquals(2, collections.size());
		ResourceInfo collection = collections.get(0);
		assertEquals(TestConstants.roboHydra.resolve("/dav/addressbooks/test/default.vcf/").toString(), collection.getURL());
		assertEquals("Default Address Book", collection.getDescription());
		// second one
		collection = collections.get(1);
		assertEquals("https://my.server/absolute:uri/my-address-book/", collection.getURL());
		assertEquals("Absolute URI VCard Book", collection.getDescription());

		/*** CalDAV ***/
		collections = info.getCalendars();
		assertEquals(2, collections.size());
		
		ResourceInfo resource = collections.get(0);
		assertEquals("Private Calendar", resource.getTitle());
		assertEquals("This is my private calendar.", resource.getDescription());
		assertFalse(resource.isReadOnly());
		
		resource = collections.get(1);
		assertEquals("Work Calendar", resource.getTitle());
		assertTrue(resource.isReadOnly());
	}
	
	
	public void testGetInitialContextURL() throws Exception {
		// without SRV records, but with well-known paths
		ServerInfo roboHydra = new ServerInfo(new URI(TestConstants.ROBOHYDRA_BASE), "test", "test", true);
		assertEquals(TestConstants.roboHydra.resolve("/"), finder.getInitialContextURL(roboHydra, "caldav"));
		assertEquals(TestConstants.roboHydra.resolve("/"), finder.getInitialContextURL(roboHydra, "carddav"));
		
		// with SRV records and well-known paths
		ServerInfo iCloud = new ServerInfo(new URI("mailto:test@icloud.com"), "", "", true);
		assertEquals(new URI("https://contacts.icloud.com/"), finder.getInitialContextURL(iCloud, "carddav"));
		assertEquals(new URI("https://caldav.icloud.com/"), finder.getInitialContextURL(iCloud, "caldav"));
	}

}
