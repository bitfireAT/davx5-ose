/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import android.security.NetworkSecurityPolicy
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.servicedetection.DavResourceFinder.Configuration.ServiceInfo
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.util.SensitiveString.Companion.toSensitiveString
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
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

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var resourceFinderFactory: DavResourceFinder.Factory

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var finder: DavResourceFinder

    @Before
    fun setUp() {
        hiltRule.inject()

        server = MockWebServer().apply {
            dispatcher = TestDispatcher(logger)
            start()
        }

        val credentials = Credentials(username = "mock", password = "12345".toSensitiveString())
        client = httpClientBuilder
                .authenticate(domain = null, getCredentials = { credentials })
                .build()
        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)

        val baseURI = URI.create("/")
        finder = resourceFinderFactory.create(baseURI, credentials)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }


    @Test
    fun testRememberIfAddressBookOrHomeset() {
        // recognize home set
        var info = ServiceInfo()
        DavResource(client, server.url(PATH_CARDDAV + SUBPATH_PRINCIPAL))
                .propfind(0, CardDAV.AddressbookHomeSet) { response, _ ->
            finder.scanResponse(CardDAV.Addressbook, response, info)
        }
        assertEquals(0, info.collections.size)
        assertEquals(1, info.homeSets.size)
        assertEquals(server.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET/"), info.homeSets.first())

        // recognize address book
        info = ServiceInfo()
        DavResource(client, server.url(PATH_CARDDAV + SUBPATH_ADDRESSBOOK))
                .propfind(0, WebDAV.ResourceType) { response, _ ->
            finder.scanResponse(CardDAV.Addressbook, response, info)
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

    @Test
    fun testQueryEmailAddress() {
        var info = ServiceInfo()
        assertArrayEquals(
                arrayOf("email1@example.com", "email2@example.com"),
                finder.queryEmailAddress(server.url(PATH_CALDAV + SUBPATH_PRINCIPAL)).toTypedArray()
        )
        assertTrue(finder.queryEmailAddress(server.url(PATH_CARDDAV + SUBPATH_PRINCIPAL)).isEmpty())
    }


    // mock server

    class TestDispatcher(
        private val logger: Logger
    ): Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (!checkAuth(request)) {
                val authenticate = MockResponse().setResponseCode(401)
                authenticate.setHeader("WWW-Authenticate", "Basic realm=\"test\"")
                return authenticate
            }

            val path = request.path!!

            if (request.method.equals("OPTIONS", true)) {
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
            } else if (request.method.equals("PROPFIND", true)) {
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

                    PATH_CALDAV + SUBPATH_PRINCIPAL ->
                        props = "<CAL:calendar-user-address-set>" +
                                "  <href>urn:unknown-entry</href>" +
                                "  <href>mailto:email1@example.com</href>" +
                                "  <href>mailto:email2@example.com</href>" +
                                "</CAL:calendar-user-address-set>"

                    else -> props = null
                }
                logger.info("Sending props: $props")
                return MockResponse()
                        .setResponseCode(207)
                        .setBody("<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                                "<response>" +
                                "   <href>${request.path}</href>" +
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
