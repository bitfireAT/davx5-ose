/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class HomeSetRefresherTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var homeSetRefresherFactory: HomeSetRefresher.Factory

    @BindValue
    @MockK(relaxed = true)
    lateinit var settings: SettingsManager

    private lateinit var client: OkHttpClient
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


    // refreshHomesetsAndTheirCollections

    @Test
    fun refreshHomesetsAndTheirCollections_addsNewCollection() = runTest {
        // save homeset in DB
        val homesetId = db.homeSetDao().insert(
            HomeSet(id = 0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
        )

        // Refresh
        homeSetRefresherFactory.create(service, client)
            .refreshHomesetsAndTheirCollections()

        // Check the collection defined in homeset is now in the database
        assertEquals(
            Collection(
                1,
                service.id,
                homesetId,
                1, // will have gotten an owner too
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description"
            ),
            db.collectionDao().getByService(service.id).first()
        )
    }

    @Test
    fun refreshHomesetsAndTheirCollections_updatesExistingCollection() {
        // save "old" collection in DB
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description"
            )
        )

        // Refresh
        homeSetRefresherFactory.create(service, client).refreshHomesetsAndTheirCollections()

        // Check the collection got updated
        assertEquals(
            Collection(
                collectionId,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description"
            ),
            db.collectionDao().get(collectionId)
        )
    }

    @Test
    fun refreshHomesetsAndTheirCollections_preservesCollectionFlags() {
        // save "old" collection in DB - with set flags
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description",
                forceReadOnly = true,
                sync = true
            )
        )

        // Refresh
        homeSetRefresherFactory.create(service, client).refreshHomesetsAndTheirCollections()

        // Check the collection got updated
        assertEquals(
            Collection(
                collectionId,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description",
                forceReadOnly = true,
                sync = true
            ),
            db.collectionDao().get(collectionId)
        )
    }

    @Test
    fun refreshHomesetsAndTheirCollections_marksRemovedCollectionsAsHomeless() {
        // save homeset in DB - which is empty (zero address books) on the serverside
        val homesetId = db.homeSetDao().insert(
            HomeSet(id = 0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_EMPTY"))
        )

        // place collection in DB - as part of the homeset
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                homesetId,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")
            )
        )

        // Refresh - should mark collection as homeless, because serverside homeset is empty.
        homeSetRefresherFactory.create(service, client).refreshHomesetsAndTheirCollections()

        // Check the collection, is now marked as homeless
        assertEquals(null, db.collectionDao().get(collectionId)!!.homeSetId)
    }

    @Test
    fun refreshHomesetsAndTheirCollections_addsOwnerUrls() {
        // save a homeset in DB
        val homesetId = db.homeSetDao().insert(
            HomeSet(id = 0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
        )

        // place collection in DB - as part of the homeset
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                homesetId, // part of above home set
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")
            )
        )

        // Refresh - homesets and their collections
        assertEquals(0, db.principalDao().getByService(service.id).size)
        homeSetRefresherFactory.create(service, client).refreshHomesetsAndTheirCollections()

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


    // other

    @Test
    fun shouldPreselect_none() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_NONE
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            0,
            service.id,
            0,
            type = Collection.TYPE_ADDRESSBOOK,
            url = mockServer.url("/addressbook-homeset/addressbook/")
        )
        val homesets = listOf(
            HomeSet(
                id = 0,
                serviceId = service.id,
                personal = true,
                url = mockServer.url("/addressbook-homeset/")
            )
        )

        val refresher = homeSetRefresherFactory.create(service, client)
        assertFalse(refresher.shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_all() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_ALL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            0,
            service.id,
            0,
            type = Collection.TYPE_ADDRESSBOOK,
            url = mockServer.url("/addressbook-homeset/addressbook/")
        )
        val homesets = listOf(
            HomeSet(
                id = 0,
                serviceId = service.id,
                personal = false,
                url = mockServer.url("/addressbook-homeset/")
            )
        )

        val refresher = homeSetRefresherFactory.create(service, client)
        assertTrue(refresher.shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_all_blacklisted() {
        val url = mockServer.url("/addressbook-homeset/addressbook/")

        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_ALL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns url.toString()

        val collection = Collection(
            id = 0,
            serviceId = service.id,
            homeSetId = 0,
            type = Collection.TYPE_ADDRESSBOOK,
            url = url
        )
        val homesets = listOf(
            HomeSet(
                id = 0,
                serviceId = service.id,
                personal = false,
                url = mockServer.url("/addressbook-homeset/")
            )
        )

        val refresher = homeSetRefresherFactory.create(service, client)
        assertFalse(refresher.shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_personal_notPersonal() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            id = 0,
            serviceId = service.id,
            homeSetId = 0,
            type = Collection.TYPE_ADDRESSBOOK,
            url = mockServer.url("/addressbook-homeset/addressbook/")
        )
        val homesets = listOf(
            HomeSet(
                id = 0,
                serviceId = service.id,
                personal = false,
                url = mockServer.url("/addressbook-homeset/")
            )
        )

        val refresher = homeSetRefresherFactory.create(service, client)
        assertFalse(refresher.shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_personal_isPersonal() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            0,
            service.id,
            0,
            type = Collection.TYPE_ADDRESSBOOK,
            url = mockServer.url("/addressbook-homeset/addressbook/")
        )
        val homesets = listOf(
            HomeSet(
                id = 0,
                serviceId = service.id,
                personal = true,
                url = mockServer.url("/addressbook-homeset/")
            )
        )

        val refresher = homeSetRefresherFactory.create(service, client)
        assertTrue(refresher.shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_personal_isPersonalButBlacklisted() {
        val collectionUrl = mockServer.url("/addressbook-homeset/addressbook/")

        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns collectionUrl.toString()

        val collection = Collection(
            id = 0,
            serviceId = service.id,
            homeSetId = 0,
            type = Collection.TYPE_ADDRESSBOOK,
            url = collectionUrl
        )
        val homesets = listOf(
            HomeSet(
                id = 0,
                serviceId = service.id,
                personal = true,
                url = mockServer.url("/addressbook-homeset/")
            )
        )

        val refresher = homeSetRefresherFactory.create(service, client)
        assertFalse(refresher.shouldPreselect(collection, homesets))
    }


    companion object {

        private const val PATH_CARDDAV = "/carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
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

                    SUBPATH_ADDRESSBOOK_HOMESET_EMPTY -> ""

                    else -> ""
                }

                logger.info("Queried: $path")
                return MockResponse()
                    .setResponseCode(207)
                    .setBody(
                        "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                                "<response>" +
                                "   <href>${PATH_CARDDAV + SUBPATH_ADDRESSBOOK}</href>" +
                                "   <propstat><prop>" +
                                properties +
                                "   </prop></propstat>" +
                                "   <status>HTTP/1.1 200 OK</status>" +
                                "</response>" +
                                "</multistatus>"
                    )
            }

            return MockResponse().setResponseCode(404)
        }

    }

}