/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import junit.framework.TestCase.assertEquals
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class PrincipalsRefresherTest {

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var principalsRefresher: PrincipalsRefresher.Factory

    @BindValue
    @MockK(relaxed = true)
    lateinit var settings: SettingsManager

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

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
        client.close()
        mockServer.shutdown()
    }


    @Test
    fun refreshPrincipals_inaccessiblePrincipal() {
        // place principal without display name in db
        val principalId = db.principalDao().insert(
            Principal(
                0,
                service.id,
                mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL_INACCESSIBLE"), // no trailing slash
                null // no display name for now
            )
        )
        // add an associated collection - as the principal is rightfully removed otherwise
        db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                principalId, // create association with principal
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"), // with trailing slash
            )
        )

        // Refresh principals
        principalsRefresher.create(service, client.okHttpClient).refreshPrincipals()

        // Check principal was not updated
        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL_INACCESSIBLE"), principals[0].url)
        assertEquals(null, principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_updatesPrincipal() {
        // place principal without display name in db
        val principalId = db.principalDao().insert(
            Principal(
                0,
                service.id,
                mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL"), // no trailing slash
                null // no display name for now
            )
        )
        // add an associated collection - as the principal is rightfully removed otherwise
        db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                principalId, // create association with principal
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"), // with trailing slash
            )
        )

        // Refresh principals
        principalsRefresher.create(service, client.okHttpClient).refreshPrincipals()

        // Check principal now got a display name
        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL"), principals[0].url)
        assertEquals("Mr. Wobbles", principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_deletesPrincipalsWithoutCollections() {
        // place principal without collections in DB
        db.principalDao().insert(
            Principal(
                0,
                service.id,
                mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS/")
            )
        )

        // Refresh principals - detecting it does not own collections
        principalsRefresher.create(service, client.okHttpClient).refreshPrincipals()

        // Check principal was deleted
        val principals = db.principalDao().getByService(service.id)
        assertEquals(0, principals.size)
    }


    companion object {

        private const val PATH_CARDDAV = "/carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_PRINCIPAL_INACCESSIBLE = "/inaccessible-principal"
        private const val SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS = "/principal2"
        private const val SUBPATH_GROUPPRINCIPAL_0 = "/groups/0"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL = "/addressbooks-homeset"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_EMPTY = "/addressbooks-homeset-empty"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/my-contacts"

    }

    class TestDispatcher(
        private val logger: Logger
    ) : Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path!!.trimEnd('/')

            if (request.method.equals("PROPFIND", true)) {
                val properties = when (path) {

                    PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                        "<resourcetype><principal/></resourcetype>" +
                                "<displayname>Mr. Wobbles</displayname>" + "<CARD:addressbook-home-set>" + "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL}</href>" + "</CARD:addressbook-home-set>" + "<group-membership>" + "   <href>${PATH_CARDDAV}${SUBPATH_GROUPPRINCIPAL_0}</href>" +
                                "</group-membership>"

                    PATH_CARDDAV + SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS ->
                        "<CARD:addressbook-home-set>" +
                                "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_EMPTY}</href>" +
                                "</CARD:addressbook-home-set>" +
                                "<displayname>Mr. Wobbles Jr.</displayname>"


                    SUBPATH_ADDRESSBOOK_HOMESET_EMPTY -> ""

                    else -> ""
                }

                logger.info("Queried: $path")
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