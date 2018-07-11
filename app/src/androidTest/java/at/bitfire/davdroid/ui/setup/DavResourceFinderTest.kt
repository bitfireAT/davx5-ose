/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.support.test.InstrumentationRegistry.getTargetContext
import android.support.test.filters.SmallTest
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.property.AddressbookHomeSet
import at.bitfire.dav4android.property.ResourceType
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.ui.setup.DavResourceFinder.Configuration.ServiceInfo
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URI

class DavResourceFinderTest {

    companion object {
        private const val PATH_NO_DAV = "/nodav"
        private const val PATH_CALDAV = "/caldav"
        private const val PATH_CARDDAV = "/carddav"
        private const val PATH_CALDAV_AND_CARDDAV = "/both-caldav-carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_ADDRESSBOOK_HOMESET = "/addressbooks"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/private-contacts"
    }

    val server = MockWebServer()

    lateinit var finder: DavResourceFinder
    lateinit var client: HttpClient
    lateinit var loginInfo: LoginInfo

    @Before
    fun initServerAndClient() {
        server.setDispatcher(TestDispatcher())
        server.start()

        loginInfo = LoginInfo(URI.create("/"), Credentials("mock", "12345"))
        finder = DavResourceFinder(getTargetContext(), loginInfo)

        client = HttpClient.Builder()
                .addAuthentication(null, loginInfo.credentials)
                .build()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }


    @Test
    @SmallTest
    fun testRememberIfAddressBookOrHomeset() {
        // recognize home set
        var info = ServiceInfo()
        DavResource(client.okHttpClient, server.url(PATH_CARDDAV + SUBPATH_PRINCIPAL))
                .propfind(0, AddressbookHomeSet.NAME) { response, _ ->
            finder.scanCardDavResponse(response, info)
        }
        assertEquals(0, info.collections.size)
        assertEquals(1, info.homeSets.size)
        assertEquals(server.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET/"), info.homeSets.first())

        // recognize address book
        info = ServiceInfo()
        DavResource(client.okHttpClient, server.url(PATH_CARDDAV + SUBPATH_ADDRESSBOOK))
                .propfind(0, ResourceType.NAME) { response, _ ->
            finder.scanCardDavResponse(response, info)
        }
        assertEquals(1, info.collections.size)
        assertEquals(server.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"), info.collections.keys.first())
        assertEquals(0, info.homeSets.size)
    }

    @Test
    fun testProvidesService() {
        assertFalse(finder.providesService(server.url(PATH_NO_DAV), DavResourceFinder.Service.CALDAV))
        assertFalse(finder.providesService(server.url(PATH_NO_DAV), DavResourceFinder.Service.CARDDAV))

        assertTrue(finder.providesService(server.url(PATH_CALDAV), DavResourceFinder.Service.CALDAV))
        assertFalse(finder.providesService(server.url(PATH_CALDAV), DavResourceFinder.Service.CARDDAV))

        assertTrue(finder.providesService(server.url(PATH_CARDDAV), DavResourceFinder.Service.CARDDAV))
        assertFalse(finder.providesService(server.url(PATH_CARDDAV), DavResourceFinder.Service.CALDAV))

        assertTrue(finder.providesService(server.url(PATH_CALDAV_AND_CARDDAV), DavResourceFinder.Service.CALDAV))
        assertTrue(finder.providesService(server.url(PATH_CALDAV_AND_CARDDAV), DavResourceFinder.Service.CARDDAV))
    }

    @Test
    fun testGetCurrentUserPrincipal() {
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_NO_DAV), DavResourceFinder.Service.CALDAV))
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_NO_DAV), DavResourceFinder.Service.CARDDAV))

        assertEquals(
                server.url(PATH_CALDAV + SUBPATH_PRINCIPAL),
                finder.getCurrentUserPrincipal(server.url(PATH_CALDAV), DavResourceFinder.Service.CALDAV)
        )
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_CALDAV), DavResourceFinder.Service.CARDDAV))

        assertEquals(
                server.url(PATH_CARDDAV + SUBPATH_PRINCIPAL),
                finder.getCurrentUserPrincipal(server.url(PATH_CARDDAV), DavResourceFinder.Service.CARDDAV)
        )
        assertNull(finder.getCurrentUserPrincipal(server.url(PATH_CARDDAV), DavResourceFinder.Service.CALDAV))
    }


    // mock server

    class TestDispatcher: Dispatcher() {

        override fun dispatch(rq: RecordedRequest): MockResponse {
            if (!checkAuth(rq)) {
                val authenticate = MockResponse().setResponseCode(401)
                authenticate.setHeader("WWW-Authenticate", "Basic realm=\"test\"")
                return authenticate
            }

            val path = rq.path

            if (rq.method.equals("OPTIONS", true)) {
                val dav = when {
                    path.startsWith(PATH_CALDAV) -> "calendar-access"
                    path.startsWith(PATH_CARDDAV) -> "addressbook"
                    path.startsWith(PATH_CALDAV_AND_CARDDAV) -> "calendar-access, addressbook"
                    else -> null
                }
                val response = MockResponse().setResponseCode(200)
                if (dav != null)
                    response.addHeader("DAV", dav)
                return response
            } else if (rq.method.equals("PROPFIND", true)) {
                val props: String?
                when (path) {
                    PATH_CALDAV,
                    PATH_CARDDAV ->
                        props = "<current-user-principal><href>$path$SUBPATH_PRINCIPAL</href></current-user-principal>"

                    PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                        props = "<CARD:addressbook-home-set>" +
                                "   <href>$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET</href>" +
                                "</CARD:addressbook-home-set>"

                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK ->
                        props = "<resourcetype>" +
                                "   <collection/>" +
                                "   <CARD:addressbook/>" +
                                "</resourcetype>"

                    else -> props = null
                }
                Logger.log.info("Sending props: $props")
                return MockResponse()
                        .setResponseCode(207)
                        .setBody("<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav'>" +
                                "<response>" +
                                "   <href>${rq.path}</href>" +
                                "   <propstat><prop>$props</prop></propstat>" +
                                "</response>" +
                                "</multistatus>")
            }

            return MockResponse().setResponseCode(404)
        }

        private fun checkAuth(rq: RecordedRequest) =
            rq.getHeader("Authorization") == "Basic bW9jazoxMjM0NQ=="

    }

}
