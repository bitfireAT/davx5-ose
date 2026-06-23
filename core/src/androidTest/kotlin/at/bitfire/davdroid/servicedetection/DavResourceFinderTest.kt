/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.log.FileLoggerFactory
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.servicedetection.DavResourceFinder.Configuration.ServiceInfo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidTest
class DavResourceFinderTest {

    companion object {
        const val BASE_URL = "https://dav.example.com"

        private const val PATH_NO_DAV = "/nodav"
        private const val PATH_CALDAV = "/caldav"
        private const val PATH_CARDDAV = "/carddav"
        private const val PATH_CALDAV_AND_CARDDAV = "/both-caldav-carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_ADDRESSBOOK_HOMESET = "/addressbooks"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/private-contacts"

        val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml; charset=UTF-8")

        fun multistatus(href: String, props: String) =
            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                    "<response><href>$href</href><propstat><prop>$props</prop><status>HTTP/1.1 200 OK</status></propstat></response>" +
                    "</multistatus>"
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Inject
    lateinit var httpClientBuilderProvider: Provider<HttpClientBuilder>

    @Inject
    lateinit var resourceFinderFactory: DavResourceFinder.Factory

    private lateinit var client: HttpClient
    private lateinit var finder: DavResourceFinder
    private lateinit var finderLog: FileLoggerFactory.FileLoggerContext

    private fun buildMockEngine() = MockEngine { request ->
        val path = request.url.encodedPath.trimEnd('/')
        when {
            request.method == HttpMethod.Options -> {
                val dav = when {
                    path.startsWith(PATH_CALDAV_AND_CARDDAV) -> "calendar-access, addressbook"
                    path.startsWith(PATH_CALDAV) -> "calendar-access"
                    path.startsWith(PATH_CARDDAV) -> "addressbook"
                    else -> null
                }
                val headers = if (dav != null) headersOf("DAV", dav) else headersOf()
                respond("", HttpStatusCode.OK, headers)
            }
            request.method.value == "PROPFIND" -> {
                val props = when (path) {
                    PATH_CALDAV, PATH_CARDDAV ->
                        "<current-user-principal><href>$path$SUBPATH_PRINCIPAL</href></current-user-principal>"
                    PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                        "<CARD:addressbook-home-set><href>$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET</href></CARD:addressbook-home-set>"
                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK ->
                        "<resourcetype><collection/><CARD:addressbook/></resourcetype>"
                    PATH_CALDAV + SUBPATH_PRINCIPAL ->
                        "<CAL:calendar-user-address-set>" +
                                "<href>urn:unknown-entry</href>" +
                                "<href>mailto:email1@example.com</href>" +
                                "<href>mailto:email2@example.com</href>" +
                                "</CAL:calendar-user-address-set>"
                    else -> ""
                }
                respond(multistatus(path, props), HttpStatusCode.MultiStatus, xmlHeaders)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        finderLog = FileLoggerFactory.forFile(tempFolder.newFile())
        client = HttpClient(buildMockEngine())
        finder = resourceFinderFactory.create(URI(BASE_URL), null, client, finderLog.logger)
    }

    @After
    fun tearDown() {
        finderLog.close()
        client.close()
    }


    @Test
    fun testFindInitialConfiguration_logsOutput() = runTest {
        val logFile = tempFolder.newFile()
        FileLoggerFactory.forFile(logFile).use { fileLoggerContext ->
            httpClientBuilderProvider.get()
                .setLogger(fileLoggerContext.logger)
                .buildKtor()
                .use { httpClient ->
                    resourceFinderFactory.create(
                        URI("http://localhost"), null, httpClient, fileLoggerContext.logger
                    ).findInitialConfiguration()
                }
        }
        val logs = logFile.readText()
        assertTrue("Logs should contain service detection logs", logs.contains("Checking user-given URL"))
    }

    @Test
    fun testRememberIfAddressBookOrHomeset() = runTest {
        // recognize home set
        var info = ServiceInfo()
        DavResource(client, Url("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL"))
            .propfind(0, CardDAV.AddressbookHomeSet) { response, _ ->
                finder.scanResponse(CardDAV.Addressbook, response, info)
            }
        assertEquals(0, info.collections.size)
        assertEquals(1, info.homeSets.size)
        assertEquals(Url("$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET/"), info.homeSets.first())

        // recognize address book
        info = ServiceInfo()
        DavResource(client, Url("$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK"))
            .propfind(0, WebDAV.ResourceType) { response, _ ->
                finder.scanResponse(CardDAV.Addressbook, response, info)
            }
        assertEquals(1, info.collections.size)
        assertEquals(Url("$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"), info.collections.keys.first())
        assertEquals(0, info.homeSets.size)
    }

    @Test
    fun testProvidesService() = runTest {
        assertFalse(finder.providesService(Url("$BASE_URL$PATH_NO_DAV"), DavResourceFinder.Service.CALDAV))
        assertFalse(finder.providesService(Url("$BASE_URL$PATH_NO_DAV"), DavResourceFinder.Service.CARDDAV))

        assertTrue(finder.providesService(Url("$BASE_URL$PATH_CALDAV"), DavResourceFinder.Service.CALDAV))
        assertFalse(finder.providesService(Url("$BASE_URL$PATH_CALDAV"), DavResourceFinder.Service.CARDDAV))

        assertTrue(finder.providesService(Url("$BASE_URL$PATH_CARDDAV"), DavResourceFinder.Service.CARDDAV))
        assertFalse(finder.providesService(Url("$BASE_URL$PATH_CARDDAV"), DavResourceFinder.Service.CALDAV))

        assertTrue(finder.providesService(Url("$BASE_URL$PATH_CALDAV_AND_CARDDAV"), DavResourceFinder.Service.CALDAV))
        assertTrue(finder.providesService(Url("$BASE_URL$PATH_CALDAV_AND_CARDDAV"), DavResourceFinder.Service.CARDDAV))
    }

    @Test
    fun testGetCurrentUserPrincipal() = runTest {
        assertNull(finder.getCurrentUserPrincipal(Url("$BASE_URL$PATH_NO_DAV"), DavResourceFinder.Service.CALDAV))
        assertNull(finder.getCurrentUserPrincipal(Url("$BASE_URL$PATH_NO_DAV"), DavResourceFinder.Service.CARDDAV))

        assertEquals(
            Url("$BASE_URL$PATH_CALDAV$SUBPATH_PRINCIPAL"),
            finder.getCurrentUserPrincipal(Url("$BASE_URL$PATH_CALDAV"), DavResourceFinder.Service.CALDAV)
        )
        assertNull(finder.getCurrentUserPrincipal(Url("$BASE_URL$PATH_CALDAV"), DavResourceFinder.Service.CARDDAV))

        assertEquals(
            Url("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL"),
            finder.getCurrentUserPrincipal(Url("$BASE_URL$PATH_CARDDAV"), DavResourceFinder.Service.CARDDAV)
        )
        assertNull(finder.getCurrentUserPrincipal(Url("$BASE_URL$PATH_CARDDAV"), DavResourceFinder.Service.CALDAV))
    }

    @Test
    fun testQueryEmailAddress() = runTest {
        assertArrayEquals(
            arrayOf("email1@example.com", "email2@example.com"),
            finder.queryEmailAddress(Url("$BASE_URL$PATH_CALDAV$SUBPATH_PRINCIPAL")).toTypedArray()
        )
        assertTrue(finder.queryEmailAddress(Url("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL")).isEmpty())
    }

}
