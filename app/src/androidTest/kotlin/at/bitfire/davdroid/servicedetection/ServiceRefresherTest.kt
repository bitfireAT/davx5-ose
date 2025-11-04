/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class ServiceRefresherTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var serviceRefresherFactory: ServiceRefresher.Factory

    private lateinit var client: HttpClient
    private lateinit var mockServer: MockWebServer
    private lateinit var service: Service

    @Before
    fun setUp() {
        hiltRule.inject()

        // Start mock web server
        mockServer = MockWebServer().apply {
            dispatcher = TestDispatcher(logger)
            start()
        }

        // build HTTP client
        client = httpClientBuilder.build()
        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)

        // insert test service
        val serviceId = db.serviceDao().insertOrReplace(
            Service(id = 0, accountName = "test", type = Service.TYPE_CARDDAV, principal = null)
        )
        service = db.serviceDao().get(serviceId)!!
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }


    @Test
    fun testDiscoverHomesets() {
        val baseUrl = mockServer.url(PATH_CARDDAV + SUBPATH_PRINCIPAL)

        // Query home sets
        serviceRefresherFactory.create(service, client.okHttpClient)
            .discoverHomesets(baseUrl)

        // Check home set has been saved correctly to database
        val savedHomesets = db.homeSetDao().getByService(service.id)
        assertEquals(2, savedHomesets.size)

        // Home set from current-user-principal
        val personalHomeset = savedHomesets[1]
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL/"), personalHomeset.url)
        assertEquals(service.id, personalHomeset.serviceId)
        // personal should be true for homesets detected at first query of current-user-principal (Even if they occur in a group principal as well!!!)
        assertEquals(true, personalHomeset.personal)

        // Home set found in a group principal
        val groupHomeset = savedHomesets[0]
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL/"), groupHomeset.url)
        assertEquals(service.id, groupHomeset.serviceId)
        // personal should be false for homesets not detected at the first query of current-user-principal (IE. in groups)
        assertEquals(false, groupHomeset.personal)
    }


    companion object {
        private const val PATH_CARDDAV = "/carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_GROUPPRINCIPAL_0 = "/groups/0"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL = "/addressbooks-homeset"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL = "/addressbooks-homeset-non-personal"

    }

    class TestDispatcher(
        private val logger: Logger
    ) : Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path!!.trimEnd('/')
            logger.info("Query: ${request.method} on $path ")

            if (request.method.equals("PROPFIND", true)) {
                val properties = when (path) {
                    PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                        "<resourcetype><principal/></resourcetype>" +
                                "<displayname>Mr. Wobbles</displayname>" +
                                "<CARD:addressbook-home-set>" +
                                "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL}</href>" +
                                "</CARD:addressbook-home-set>" +
                                "<group-membership>" +
                                "   <href>${PATH_CARDDAV}${SUBPATH_GROUPPRINCIPAL_0}</href>" +
                                "</group-membership>"

                    PATH_CARDDAV + SUBPATH_GROUPPRINCIPAL_0 ->
                        "<resourcetype><principal/></resourcetype>" +
                                "<displayname>All address books</displayname>" +
                                "<CARD:addressbook-home-set>" +
                                "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL}</href>" +
                                "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL}</href>" +
                                "</CARD:addressbook-home-set>"

                    else -> ""
                }
                return MockResponse()
                    .setResponseCode(207)
                    .setBody(
                        "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                                "<response>" +
                                "   <href>$path</href>" +
                                "   <propstat><prop>" +
                                properties +
                                "   </prop></propstat>" +
                                "</response>" +
                                "</multistatus>"
                    )
            }

            return MockResponse().setResponseCode(404)
        }

    }

}