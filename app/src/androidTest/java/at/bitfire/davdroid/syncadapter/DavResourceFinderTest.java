package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.resource.ServerInfo.ResourceInfo;
import at.bitfire.davdroid.TestConstants;
import ezvcard.VCardVersion;

public class DavResourceFinderTest extends InstrumentationTestCase {
	
	DavResourceFinder finder;

	@Override
	protected void setUp() {
		finder = new DavResourceFinder(getInstrumentation().getContext());
	}
	
	@Override
	protected void tearDown() throws IOException {
		finder.close();
	}
	

	public void testFindResourcesRobohydra() throws Exception {
		ServerInfo info = new ServerInfo(new URI(TestConstants.ROBOHYDRA_BASE), "test", "test", true);
		finder.findResources(info);
		
		/*** CardDAV ***/
		assertTrue(info.isCardDAV());
		List<ResourceInfo> collections = info.getAddressBooks();
		// two address books
		assertEquals(2, collections.size());
		// first one
		ResourceInfo collection = collections.get(0);
		assertEquals(TestConstants.roboHydra.resolve("/dav/addressbooks/test/default-v4.vcf/").toString(), collection.getURL());
		assertEquals("Default Address Book", collection.getDescription());
		assertEquals(VCardVersion.V4_0, collection.getVCardVersion());
		// second one
		collection = collections.get(1);
		assertEquals("https://my.server/absolute:uri/my-address-book/", collection.getURL());
		assertEquals("Absolute URI VCard3 Book", collection.getDescription());
		assertEquals(VCardVersion.V3_0, collection.getVCardVersion());
		
		/*** CalDAV ***/
		assertTrue(info.isCalDAV());
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
