/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import android.content.Context
import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockkObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class CollectionListRefresherTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var refresherFactory: CollectionListRefresher.Factory

    @Inject
    lateinit var settings: SettingsManager

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private val mockServer = MockWebServer()
    private lateinit var client: HttpClient

    @Before
    fun setup() {
        hiltRule.inject()

        // Start mock web server
        mockServer.dispatcher = TestDispatcher(logger)
        mockServer.start()

        client = HttpClient.Builder(context).build()

        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @After
    fun teardown() {
        mockServer.shutdown()
        db.close()
    }


    @Test
    fun testDiscoverHomesets() {
        val service = createTestService(Service.TYPE_CARDDAV)!!
        val baseUrl = mockServer.url(PATH_CARDDAV + SUBPATH_PRINCIPAL)

        // Query home sets
        refresherFactory.create(service, client.okHttpClient).discoverHomesets(baseUrl)

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


    // refreshHomesetsAndTheirCollections

    @Test
    fun refreshHomesetsAndTheirCollections_addsNewCollection() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        // save homeset in DB
        val homesetId = db.homeSetDao().insert(
            HomeSet(id=0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
        )

        // Refresh
        refresherFactory.create(service, client.okHttpClient).refreshHomesetsAndTheirCollections()

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
        val service = createTestService(Service.TYPE_CARDDAV)!!

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
        refresherFactory.create(service, client.okHttpClient).refreshHomesetsAndTheirCollections()

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
        val service = createTestService(Service.TYPE_CARDDAV)!!

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
        refresherFactory.create(service, client.okHttpClient).refreshHomesetsAndTheirCollections()

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
        val service = createTestService(Service.TYPE_CARDDAV)!!

        // save homeset in DB - which is empty (zero address books) on the serverside
        val homesetId = db.homeSetDao().insert(
            HomeSet(id=0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_EMPTY"))
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
        refresherFactory.create(service, client.okHttpClient).refreshHomesetsAndTheirCollections()

        // Check the collection, is now marked as homeless
        assertEquals(null, db.collectionDao().get(collectionId)!!.homeSetId)
    }

    @Test
    fun refreshHomesetsAndTheirCollections_addsOwnerUrls() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        // save a homeset in DB
        val homesetId = db.homeSetDao().insert(
            HomeSet(id=0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
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
        refresherFactory.create(service, client.okHttpClient).refreshHomesetsAndTheirCollections()

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


    // refreshHomelessCollections

    @Test
    fun refreshHomelessCollections_updatesExistingCollection() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

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
        val service = createTestService(Service.TYPE_CARDDAV)!!

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
        val service = createTestService(Service.TYPE_CARDDAV)!!

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


    // refreshPrincipals

    @Test
    fun refreshPrincipals_inaccessiblePrincipal() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

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
        refresherFactory.create(service, client.okHttpClient).refreshPrincipals()

        // Check principal was not updated
        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL_INACCESSIBLE"), principals[0].url)
        assertEquals(null, principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_updatesPrincipal() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

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
        refresherFactory.create(service, client.okHttpClient).refreshPrincipals()

        // Check principal now got a display name
        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL"), principals[0].url)
        assertEquals("Mr. Wobbles", principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_deletesPrincipalsWithoutCollections() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        // place principal without collections in DB
        db.principalDao().insert(
            Principal(
                0,
                service.id,
                mockServer.url("$PATH_CARDDAV$SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS/")
            )
        )

        // Refresh principals - detecting it does not own collections
        refresherFactory.create(service, client.okHttpClient).refreshPrincipals()

        // Check principal was deleted
        val principals = db.principalDao().getByService(service.id)
        assertEquals(0, principals.size)
    }

    // Others

    @Test
    fun shouldPreselect_none() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        mockkObject(settings) {
            every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_NONE
            every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

            val collection = Collection(
                0,
                service.id,
                0,
                type = Collection.TYPE_ADDRESSBOOK,
                url = mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")
            )
            val homesets = listOf(
                HomeSet(0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
            )

            val refresher = refresherFactory.create(service, client.okHttpClient)
            assertFalse(refresher.shouldPreselect(collection, homesets))
        }
    }

    @Test
    fun shouldPreselect_all() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        mockkObject(settings) {
            every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_ALL
            every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

            val collection = Collection(
                0,
                service.id,
                0,
                type = Collection.TYPE_ADDRESSBOOK,
                url = mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")
            )
            val homesets = listOf(
                HomeSet(0, service.id, false, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
            )

            val refresher = refresherFactory.create(service, client.okHttpClient)
            assertTrue(refresher.shouldPreselect(collection, homesets))
        }
    }

    @Test
    fun shouldPreselect_all_blacklisted() {
        val service = createTestService(Service.TYPE_CARDDAV)!!
        val url = mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")

        mockkObject(settings) {
            every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_ALL
            every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns url.toString()

            val collection = Collection(
                0,
                service.id,
                0,
                type = Collection.TYPE_ADDRESSBOOK,
                url = url
            )
            val homesets = listOf(
                HomeSet(0, service.id, false, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
            )

            val refresher = refresherFactory.create(service, client.okHttpClient)
            assertFalse(refresher.shouldPreselect(collection, homesets))
        }
    }

    @Test
    fun shouldPreselect_personal_notPersonal() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        mockkObject(settings) {
            every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
            every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

            val collection = Collection(
                0,
                service.id,
                0,
                type = Collection.TYPE_ADDRESSBOOK,
                url = mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")
            )
            val homesets = listOf(
                HomeSet(0, service.id, false, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
            )

            val refresher = refresherFactory.create(service, client.okHttpClient)
            assertFalse(refresher.shouldPreselect(collection, homesets))
        }
    }

    @Test
    fun shouldPreselect_personal_isPersonal() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        mockkObject(settings) {
            every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
            every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

            val collection = Collection(
                0,
                service.id,
                0,
                type = Collection.TYPE_ADDRESSBOOK,
                url = mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")
            )
            val homesets = listOf(
                HomeSet(0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
            )

            val refresher = refresherFactory.create(service, client.okHttpClient)
            assertTrue(refresher.shouldPreselect(collection, homesets))
        }
    }

    @Test
    fun shouldPreselect_personal_isPersonalButBlacklisted() {
        val service = createTestService(Service.TYPE_CARDDAV)!!
        val collectionUrl = mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")

        mockkObject(settings) {
            every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
            every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns collectionUrl.toString()

            val collection = Collection(
                0,
                service.id,
                0,
                type = Collection.TYPE_ADDRESSBOOK,
                url = collectionUrl
            )
            val homesets = listOf(
                HomeSet(0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL"))
            )

            val refresher = refresherFactory.create(service, client.okHttpClient)
            assertFalse(refresher.shouldPreselect(collection, homesets))
        }
    }

    // Test helpers and dependencies
    
    private fun createTestService(serviceType: String) : Service? {
        val service = Service(id=0, accountName="test", type=serviceType, principal = null)
        val serviceId = db.serviceDao().insertOrReplace(service)
        return db.serviceDao().get(serviceId)
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
