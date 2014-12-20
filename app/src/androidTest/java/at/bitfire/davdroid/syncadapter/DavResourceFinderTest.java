package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.resource.ServerInfo.ResourceInfo;
import at.bitfire.davdroid.test.Constants;
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
		ServerInfo info = new ServerInfo(new URI(Constants.ROBOHYDRA_BASE), "test", "test", true);
		finder.findResources(info);
		
		// CardDAV
		assertTrue(info.isCardDAV());
		List<ResourceInfo> collections = info.getAddressBooks();
		assertEquals(1, collections.size());
		
		assertEquals("Default Address Book", collections.get(0).getDescription());
		assertEquals(VCardVersion.V4_0, collections.get(0).getVCardVersion());
		
		// CalDAV
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
		ServerInfo roboHydra = new ServerInfo(new URI(Constants.ROBOHYDRA_BASE), "test", "test", true);
		assertEquals(Constants.roboHydra.resolve("/"), finder.getInitialContextURL(roboHydra, "caldav"));
		assertEquals(Constants.roboHydra.resolve("/"), finder.getInitialContextURL(roboHydra, "carddav"));
		
		// with SRV records and well-known paths
		ServerInfo iCloud = new ServerInfo(new URI("mailto:test@icloud.com"), "", "", true);
		assertEquals(new URI("https://contacts.icloud.com/"), finder.getInitialContextURL(iCloud, "carddav"));
		assertEquals(new URI("https://caldav.icloud.com/"), finder.getInitialContextURL(iCloud, "caldav"));
	}

}
