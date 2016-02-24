package at.bitfire.davdroid.resource;

import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.net.URI;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.ui.setup.DavResourceFinder;
import at.bitfire.davdroid.ui.setup.LoginCredentials;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class DavResourceFinderTest extends InstrumentationTestCase {

    MockWebServer server = new MockWebServer();

    @Override
    protected void setUp() throws Exception {
        server.start();
    }

    @Override
    protected void tearDown() throws Exception {
        server.shutdown();
    }


    public void testGetCurrentUserPrincipal() throws IOException, HttpException, DavException {
        HttpUrl url = server.url("/dav");
        LoginCredentials credentials = new LoginCredentials(url.uri(), "admin", "12345", true);
        DavResourceFinder finder = new DavResourceFinder(getInstrumentation().getTargetContext().getApplicationContext(), credentials);

        // positive test case
        server.enqueue(new MockResponse()       // PROPFIND response
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml;charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <current-user-principal><href>/principals/myself</href></current-user-principal>" +
                        "      </prop>" +
                        "      <status>HTTP/1.0 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"));
        server.enqueue(new MockResponse()       // OPTIONS response
                .setResponseCode(200)
                .setHeader("DAV", "addressbook"));
        URI principal = finder.getCurrentUserPrincipal(url, DavResourceFinder.Service.CARDDAV);
        assertEquals(url.resolve("/principals/myself").uri(), principal);

        // negative test case: no current-user-principal
        server.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml;charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                      "      <status>HTTP/1.0 200 OK</status>" +
                        "  </response>" +
                        "</multistatus>"));
        assertNull(finder.getCurrentUserPrincipal(url, DavResourceFinder.Service.CARDDAV));

        // negative test case: requested service not available
        server.enqueue(new MockResponse()       // PROPFIND response
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml;charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <current-user-principal><href>/principals/myself</href></current-user-principal>" +
                        "      </prop>" +
                        "      <status>HTTP/1.0 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"));
        server.enqueue(new MockResponse()       // OPTIONS response
                .setResponseCode(200)
                .setHeader("DAV", "addressbook"));
        assertNull(finder.getCurrentUserPrincipal(url, DavResourceFinder.Service.CALDAV));
    }

}
