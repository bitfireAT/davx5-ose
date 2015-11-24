package at.bitfire.davdroid.resource;

import android.test.InstrumentationTestCase;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import junit.framework.TestCase;

import java.io.IOException;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.Constants;

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
        ServerInfo serverInfo = new ServerInfo(url.uri(), "admin", "12345", true);
        DavResourceFinder finder = new DavResourceFinder(Constants.log, getInstrumentation().getTargetContext().getApplicationContext(), serverInfo);

        // positive test case
        server.enqueue(new MockResponse()
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
        HttpUrl principal = finder.getCurrentUserPrincipal(url);
        assertEquals(url.resolve("/principals/myself"), principal);

        // negative test case
        server.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml;charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                      "      <status>HTTP/1.0 200 OK</status>" +
                        "  </response>" +
                        "</multistatus>"));
        assertNull(finder.getCurrentUserPrincipal(url));
    }

}
