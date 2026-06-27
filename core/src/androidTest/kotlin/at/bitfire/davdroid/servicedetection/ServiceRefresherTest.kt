/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.util.toUrl
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class ServiceRefresherTest {

    companion object {
        const val BASE_URL = "https://dav.example.com"

        private const val PATH_CARDDAV = "/carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_GROUPPRINCIPAL_0 = "/groups/0"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL = "/addressbooks-homeset"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL = "/addressbooks-homeset-non-personal"

        val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml; charset=UTF-8")

        fun multistatus(href: String, props: String) =
            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                    "<response><href>$href</href><propstat><prop>$props</prop><status>HTTP/1.1 200 OK</status></propstat></response>" +
                    "</multistatus>"
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var serviceRefresherFactory: ServiceRefresher.Factory

    private lateinit var client: HttpClient
    private lateinit var service: Service

    private fun buildMockEngine() = MockEngine { request ->
        if (request.method.value != "PROPFIND")
            return@MockEngine respond("", HttpStatusCode.NotFound)

        val path = request.url.encodedPath.trimEnd('/')
        val props = when (path) {
            PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                "<resourcetype><principal/></resourcetype>" +
                        "<displayname>Mr. Wobbles</displayname>" +
                        "<CARD:addressbook-home-set>" +
                        "   <href>$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL</href>" +
                        "</CARD:addressbook-home-set>" +
                        "<group-membership>" +
                        "   <href>$PATH_CARDDAV$SUBPATH_GROUPPRINCIPAL_0</href>" +
                        "</group-membership>"

            PATH_CARDDAV + SUBPATH_GROUPPRINCIPAL_0 ->
                "<resourcetype><principal/></resourcetype>" +
                        "<displayname>All address books</displayname>" +
                        "<CARD:addressbook-home-set>" +
                        "   <href>$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL</href>" +
                        "   <href>$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL</href>" +
                        "</CARD:addressbook-home-set>"

            else -> ""
        }
        respond(multistatus(path, props), HttpStatusCode.MultiStatus, xmlHeaders)
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        client = HttpClient(buildMockEngine())

        val serviceId = db.serviceDao().insertOrReplace(
            Service(id = 0, accountName = "test", type = Service.TYPE_CARDDAV, principal = null)
        )
        service = db.serviceDao().get(serviceId)!!
    }

    @After
    fun tearDown() {
        client.close()
    }


    @Test
    fun testDiscoverHomesets() = runTest {
        val baseUrl = Url("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL")

        serviceRefresherFactory.create(service, client)
            .discoverHomesets(baseUrl)

        val savedHomesets = db.homeSetDao().getByService(service.id)
        assertEquals(2, savedHomesets.size)

        // Home set from current-user-principal
        val personalHomeset = savedHomesets[1]
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL/".toUrl(), personalHomeset.url)
        assertEquals(service.id, personalHomeset.serviceId)
        // personal should be true for homesets detected at first query of current-user-principal (Even if they occur in a group principal as well!!!)
        assertEquals(true, personalHomeset.personal)

        // Home set found in a group principal
        val groupHomeset = savedHomesets[0]
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL/".toUrl(), groupHomeset.url)
        assertEquals(service.id, groupHomeset.serviceId)
        // personal should be false for homesets not detected at the first query of current-user-principal (IE. in groups)
        assertEquals(false, groupHomeset.personal)
    }

}
