package at.bitfire.davdroid.syncadapter;

import java.util.List;

import ezvcard.VCardVersion;
import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.resource.ServerInfo.ResourceInfo;
import at.bitfire.davdroid.test.Constants;

public class DavResourceFinderTest extends InstrumentationTestCase {
	
	public void testFindResources() throws Exception {
		ServerInfo info = new ServerInfo(Constants.ROBOHYDRA_BASE, "test", "test", true);
		DavResourceFinder.findResources(getInstrumentation().getContext(), info);
		
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

}
