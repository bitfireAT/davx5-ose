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
class CollectionsWithoutHomeSetRefresherTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var refresherFactory: CollectionsWithoutHomeSetRefresher.Factory

    @BindValue
    @MockK(relaxed = true)
    lateinit var settings: SettingsManager

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


    // refreshCollectionsWithoutHomeSet

    @Test
    fun refreshCollectionsWithoutHomeSet_updatesExistingCollection() {
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
        refresherFactory.create(service, client.okHttpClient).refreshCollectionsWithoutHomeSet()

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
    fun refreshCollectionsWithoutHomeSet_deletesInaccessibleCollectionsWithoutHomeSet() {
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
        refresherFactory.create(service, client.okHttpClient).refreshCollectionsWithoutHomeSet()

        // Check the collection got deleted
        assertEquals(null, db.collectionDao().get(collectionId))
    }

    @Test
    fun refreshCollectionsWithoutHomeSet_addsOwnerUrls() {
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
        refresherFactory.create(service, client.okHttpClient).refreshCollectionsWithoutHomeSet()

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
        private const val PATH_CARDDAV = "/carddav"
        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/my-contacts"
        private const val SUBPATH_ADDRESSBOOK_INACCESSIBLE = "/addressbooks/inaccessible-contacts"
    }

    class TestDispatcher(
        private val logger: Logger
    ): Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path!!.trimEnd('/')
            logger.info("${request.method} on $path")

            if (request.method.equals("PROPFIND", true)) {
                val properties = when (path) {
                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK ->
                        "<resourcetype>" +
                        "   <collection/>" +
                        "   <CARD:addressbook/>" +
                        "</resourcetype>" +
                        "<displayname>My Contacts</displayname>" +
                        "<CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                        "<owner>" +
                        "   <href>${PATH_CARDDAV + SUBPATH_PRINCIPAL}</href>" +
                        "</owner>"

                    else -> ""
                }

                return MockResponse()
                    .setResponseCode(207)
                    .setBody("<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                            "<response>" +
                            "   <href>$path</href>" +
                            "   <propstat><prop>"+
                            properties +
                            "   </prop></propstat>" +
                            "</response>" +
                            "</multistatus>")
            }

            return MockResponse().setResponseCode(404)
        }

    }

}