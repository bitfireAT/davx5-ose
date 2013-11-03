package at.bitfire.davdroid.webdav.test;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpException;

import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.webdav.HttpPropfind;
import at.bitfire.davdroid.webdav.InvalidDavResponseException;
import at.bitfire.davdroid.webdav.WebDavCollection;
import at.bitfire.davdroid.webdav.WebDavResource;

public class WebDavCollectionTest extends InstrumentationTestCase {
	WebDavCollection dav;

	protected void setUp() throws Exception {
		dav = new WebDavCollection(new URI("https://wurd.dev001.net/radicale/test/"), "test", "test", true);
	}
	
	
	public void testAutoDetection() throws InvalidDavResponseException, IOException, HttpException {
		WebDavCollection myDav = dav;
		
		myDav.propfind(HttpPropfind.Mode.CURRENT_USER_PRINCIPAL);
		String currentUserPrincipal = myDav.getCurrentUserPrincipal();
		assertEquals("/radicale/test/", currentUserPrincipal);
		
		myDav = new WebDavCollection(dav, currentUserPrincipal);
		myDav.propfind(HttpPropfind.Mode.HOME_SETS);
		String homeSet = myDav.getAddressbookHomeSet();
		assertEquals("/radicale/test/", homeSet);
		assertEquals("/radicale/test/", myDav.getCalendarHomeSet());
		
		myDav = new WebDavCollection(dav, homeSet);
		myDav.propfind(HttpPropfind.Mode.MEMBERS_COLLECTIONS);
		assertEquals(3, myDav.getMembers().size());
		for (WebDavResource member : myDav.getMembers())
			assertTrue(member.isAddressBook() || member.isCalendar());
	}

}
