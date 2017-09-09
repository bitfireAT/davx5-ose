/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressbookHomeSet;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.log.Logger;
import at.bitfire.davdroid.ui.setup.DavResourceFinder.Configuration.ServiceInfo;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DavResourceFinderTest {

    MockWebServer server = new MockWebServer();

    DavResourceFinder finder;
    HttpClient client;
    LoginCredentials credentials;

    private static final String
            PATH_NO_DAV = "/nodav",

            PATH_CALDAV = "/caldav",
            PATH_CARDDAV = "/carddav",
            PATH_CALDAV_AND_CARDDAV = "/both-caldav-carddav",

            SUBPATH_PRINCIPAL = "/principal",
            SUBPATH_ADDRESSBOOK_HOMESET = "/addressbooks",
            SUBPATH_ADDRESSBOOK = "/addressbooks/private-contacts";

    @Before
    public void initServerAndClient() throws Exception {
        server.setDispatcher(new TestDispatcher());
        server.start();

        credentials = new LoginCredentials(URI.create("/"), "mock", "12345");
        finder = new DavResourceFinder(getTargetContext(), credentials);

        client = new HttpClient.Builder()
                .addAuthentication(null, credentials.getUserName(), credentials.getPassword())
                .build();
    }

    @After
    public void stopServer() throws Exception {
        server.shutdown();
    }

    @Test
    public void testRememberIfAddressBookOrHomeset() throws IOException, HttpException, DavException {
        ServiceInfo info;

        // before dav.propfind(), no info is available
        DavResource dav = new DavResource(client.getOkHttpClient(), server.url(PATH_CARDDAV + SUBPATH_PRINCIPAL));
        finder.rememberIfAddressBookOrHomeset(dav, info = new ServiceInfo());
        assertEquals(0, info.getCollections().size());
        assertEquals(0, info.getHomeSets().size());

        // recognize home set
        dav.propfind(0, AddressbookHomeSet.NAME);
        finder.rememberIfAddressBookOrHomeset(dav, info = new ServiceInfo());
        assertEquals(0, info.getCollections().size());
        assertEquals(1, info.getHomeSets().size());
        assertEquals(server.url(PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET + "/").uri(), info.getHomeSets().iterator().next());

        // recognize address book
        dav = new DavResource(client.getOkHttpClient(), server.url(PATH_CARDDAV + SUBPATH_ADDRESSBOOK));
        dav.propfind(0, ResourceType.NAME);
        finder.rememberIfAddressBookOrHomeset(dav, info = new ServiceInfo());
        assertEquals(1, info.getCollections().size());
        assertEquals(server.url(PATH_CARDDAV + SUBPATH_ADDRESSBOOK + "/").uri(), info.getCollections().keySet().iterator().next());
        assertEquals(0, info.getHomeSets().size());
    }

    @Test
    public void testProvidesService() throws IOException {
        assertFalse(finder.providesService(server.url(PATH_NO_DAV), DavResourceFinder.Service.CALDAV));
        assertFalse(finder.providesService(server.url(PATH_NO_DAV), DavResourceFinder.Service.CARDDAV));

        assertTrue(finder.providesService(server.url(PATH_CALDAV), DavResourceFinder.Service.CALDAV));
        assertFalse(finder.providesService(server.url(PATH_CALDAV), DavResourceFinder.Service.CARDDAV));

        assertTrue(finder.providesService(server.url(PATH_CARDDAV), DavResourceFinder.Service.CARDDAV));
        assertFalse(finder.providesService(server.url(PATH_CARDDAV), DavResourceFinder.Service.CALDAV));

        assertTrue(finder.providesService(server.url(PATH_CALDAV_AND_CARDDAV), DavResourceFinder.Service.CALDAV));
        assertTrue(finder.providesService(server.url(PATH_CALDAV_AND_CARDDAV), DavResourceFinder.Service.CARDDAV));
    }

    @Test
    public void testGetCurrentUserPrincipal() throws IOException, HttpException, DavException {
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_NO_DAV), DavResourceFinder.Service.CALDAV));
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_NO_DAV), DavResourceFinder.Service.CARDDAV));

        assertEquals(
                server.url(PATH_CALDAV + SUBPATH_PRINCIPAL).uri(),
                finder.getCurrentUserPrincipal(server.url(PATH_CALDAV), DavResourceFinder.Service.CALDAV)
        );
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_CALDAV), DavResourceFinder.Service.CARDDAV));

        assertEquals(
                server.url(PATH_CARDDAV + SUBPATH_PRINCIPAL).uri(),
                finder.getCurrentUserPrincipal(server.url(PATH_CARDDAV), DavResourceFinder.Service.CARDDAV)
        );
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_CARDDAV), DavResourceFinder.Service.CALDAV));
    }


    // mock server

    public class TestDispatcher extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest rq) throws InterruptedException {
            if (!checkAuth(rq)) {
                MockResponse authenticate = new MockResponse().setResponseCode(401);
                authenticate.setHeader("WWW-Authenticate", "Basic realm=\"test\"");
                return authenticate;
            }

            String path = rq.getPath();

            if ("OPTIONS".equalsIgnoreCase(rq.getMethod())) {
                String dav = null;
                if (path.startsWith(PATH_CALDAV))
                    dav = "calendar-access";
                else if (path.startsWith(PATH_CARDDAV))
                    dav = "addressbook";
                else if (path.startsWith(PATH_CALDAV_AND_CARDDAV))
                    dav = "calendar-access, addressbook";
                MockResponse response = new MockResponse().setResponseCode(200);
                if (dav != null)
                       response.addHeader("DAV", dav);
                return response;

            } else if ("PROPFIND".equalsIgnoreCase(rq.getMethod())) {
                String props = null;
                switch (path) {
                    case PATH_CALDAV:
                    case PATH_CARDDAV:
                        props = "<current-user-principal><href>" + path + SUBPATH_PRINCIPAL + "</href></current-user-principal>";
                        break;

                    case PATH_CARDDAV + SUBPATH_PRINCIPAL:
                        props = "<CARD:addressbook-home-set>" +
                                "   <href>" + PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET + "</href>" +
                                "</CARD:addressbook-home-set>";
                        break;
                    case PATH_CARDDAV + SUBPATH_ADDRESSBOOK:
                        props = "<resourcetype>" +
                                "   <collection/>" +
                                "   <CARD:addressbook/>" +
                                "</resourcetype>";
                        break;
                }
                Logger.log.info("Sending props: " + props);
                return new MockResponse()
                        .setResponseCode(207)
                        .setBody("<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav'>" +
                                "<response>" +
                                "   <href>" + rq.getPath() + "</href>" +
                                "   <propstat><prop>" + props + "</prop></propstat>" +
                                "</response>" +
                                "</multistatus>");
            }

            return new MockResponse().setResponseCode(404);
        }

        private boolean checkAuth(RecordedRequest rq) {
            return "Basic bW9jazoxMjM0NQ==".equals(rq.getHeader("Authorization"));
        }
    }

}
