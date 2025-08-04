/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
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
class CollectionListRefresherTest {

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var refresherFactory: CollectionListRefresher.Factory

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


    // refreshHomelessCollections

    @Test
    fun refreshHomelessCollections_updatesExistingCollection() {
        // place homeless collection in DB
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
            )
        )

        // Refresh
        refresherFactory.create(service, client.okHttpClient).refreshHomelessCollections()

        // Check the collection got updated - with display name and description
        assertEquals(
            Collection(
                collectionId,
                service.id,
                null,
                1, // will have gotten an owner too
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description"
            ),
            db.collectionDao().get(collectionId)
        )
    }

    @Test
    fun refreshHomelessCollections_deletesInaccessibleCollections() {
        // place homeless collection in DB - it is also inaccessible
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_INACCESSIBLE")
            )
        )

        // Refresh - should delete collection
        refresherFactory.create(service, client.okHttpClient).refreshHomelessCollections()

        // Check the collection got deleted
        assertEquals(null, db.collectionDao().get(collectionId))
    }

    @Test
    fun refreshHomelessCollections_addsOwnerUrls() {
        // place homeless collection in DB
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
            )
        )

        // Refresh homeless collections
        assertEquals(0, db.principalDao().getByService(service.id).size)
        refresherFactory.create(service, client.okHttpClient).refreshHomelessCollections()

        // Check principal saved and the collection was updated with its reference
        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL"), principals[0].url)
        assertEquals(null, principals[0].displayName)
        assertEquals(
            principals[0].id,
            db.collectionDao().get(collectionId)!!.ownerId
        )
    }


    companion object {

        private const val PATH_CALDAV = "/caldav"
        private const val PATH_CARDDAV = "/carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_PRINCIPAL_INACCESSIBLE = "/inaccessible-principal"
        private const val SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS = "/principal2"
        private const val SUBPATH_GROUPPRINCIPAL_0 = "/groups/0"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL = "/addressbooks-homeset"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL = "/addressbooks-homeset-non-personal"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_EMPTY = "/addressbooks-homeset-empty"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/my-contacts"
        private const val SUBPATH_ADDRESSBOOK_INACCESSIBLE = "/addressbooks/inaccessible-contacts"

    }

    class TestDispatcher(
        private val logger: Logger
    ): Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path!!.trimEnd('/')

            if (request.method.equals("PROPFIND", true)) {
                val properties = when (path) {
                    PATH_CALDAV,
                    PATH_CARDDAV ->
                        "<current-user-principal>" +
                        "   <href>$path${SUBPATH_PRINCIPAL}</href>" +
                        "</current-user-principal>"

                    PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                        "<resourcetype><principal/></resourcetype>" +
                        "<displayname>Mr. Wobbles</displayname>" +
                        "<CARD:addressbook-home-set>" +
                        "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL}</href>" +
                        "</CARD:addressbook-home-set>" +
                        "<group-membership>" +
                        "   <href>${PATH_CARDDAV}${SUBPATH_GROUPPRINCIPAL_0}</href>" +
                        "</group-membership>"

                    PATH_CARDDAV + SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS ->
                        "<CARD:addressbook-home-set>" +
                        "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_EMPTY}</href>" +
                        "</CARD:addressbook-home-set>" +
                        "<displayname>Mr. Wobbles Jr.</displayname>"

                    PATH_CARDDAV + SUBPATH_GROUPPRINCIPAL_0 ->
                        "<resourcetype><principal/></resourcetype>" +
                        "<displayname>All address books</displayname>" +
                        "<CARD:addressbook-home-set>" +
                        "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL}</href>" +
                        "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL}</href>" +
                        "</CARD:addressbook-home-set>"

                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK,
                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL ->
                        "<resourcetype>" +
                        "   <collection/>" +
                        "   <CARD:addressbook/>" +
                        "</resourcetype>" +
                        "<displayname>My Contacts</displayname>" +
                        "<CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                        "<owner>" +
                        "   <href>${PATH_CARDDAV + SUBPATH_PRINCIPAL}</href>" +
                        "</owner>"

                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET_NON_PERSONAL ->
                        "<resourcetype>" +
                        "   <collection/>" +
                        "   <CARD:addressbook/>" +
                        "</resourcetype>" +
                        "<displayname>Freds Contacts (not mine)</displayname>" +
                        "<CARD:addressbook-description>Not personal contacts</CARD:addressbook-description>" +
                        "<owner>" +
                        "   <href>${PATH_CARDDAV + SUBPATH_PRINCIPAL}</href>" + // OK, user is allowed to own non-personal contacts
                        "</owner>"

                    PATH_CALDAV + SUBPATH_PRINCIPAL ->
                        "<CAL:calendar-user-address-set>" +
                        "  <href>urn:unknown-entry</href>" +
                        "  <href>mailto:email1@example.com</href>" +
                        "  <href>mailto:email2@example.com</href>" +
                        "</CAL:calendar-user-address-set>"

                    SUBPATH_ADDRESSBOOK_HOMESET_EMPTY -> ""

                    else -> ""
                }

                var responseBody = ""
                var responseCode = 207
                when (path) {
                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL ->
                        responseBody =
                            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                            "<response>" +
                            "   <href>${PATH_CARDDAV + SUBPATH_ADDRESSBOOK}</href>" +
                            "   <propstat><prop>" +
                                    properties +
                            "   </prop></propstat>" +
                            "   <status>HTTP/1.1 200 OK</status>" +
                            "</response>" +
                            "</multistatus>"

                    PATH_CARDDAV + SUBPATH_PRINCIPAL_INACCESSIBLE,
                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK_INACCESSIBLE ->
                        responseCode = 404

                    else ->
                        responseBody =
                            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                            "<response>" +
                            "   <href>$path</href>" +
                            "   <propstat><prop>"+
                                    properties +
                            "   </prop></propstat>" +
                            "</response>" +
                            "</multistatus>"
                }

                logger.info("Queried: $path")
                logger.info("Response: $responseBody")
                return MockResponse()
                    .setResponseCode(responseCode)
                    .setBody(responseBody)
            }

            return MockResponse().setResponseCode(404)
        }

    }

}