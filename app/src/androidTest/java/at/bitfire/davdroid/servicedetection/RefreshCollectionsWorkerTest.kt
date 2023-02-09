/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.servicedetection

import android.content.Context
import android.security.NetworkSecurityPolicy
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.property.AddressbookHomeSet
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.TestUtils.workScheduledOrRunning
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.setup.LoginModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*

import java.net.URI
import javax.inject.Inject

@HiltAndroidTest
class RefreshCollectionsWorkerTest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Before
    fun setUp() {
        hiltRule.inject()

        // The test application is an instance of HiltTestApplication, which doesn't initialize notification channels.
        // However, we need notification channels for the ongoing work notifications.
        NotificationUtils.createChannels(context)

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    
    // Test dependencies

    companion object {
        private const val PATH_CALDAV = "/caldav"
        private const val PATH_CARDDAV = "/carddav"
        private const val PATH_CALDAV_AND_CARDDAV = "/both-caldav-carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_ADDRESSBOOK_HOMESET = "/addressbooks-homeset"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_EMPTY = "/addressbooks-homeset-empty"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/my-contacts"
        private const val SUBPATH_ADDRESSBOOK_INACCESSIBLE = "/addressbooks/inaccessible-contacts"
    }

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var settings: SettingsManager

    var mockServer =  MockWebServer()

    lateinit var client: HttpClient
    lateinit var loginModel: LoginModel

    @Before
    fun mockServerSetup() {
        // Start mock web server
        mockServer.dispatcher = TestDispatcher()
        mockServer.start()

        loginModel = LoginModel()
        loginModel.baseURI = URI.create("/")
        loginModel.credentials = Credentials("mock", "12345")

        client = HttpClient.Builder()
            .addAuthentication(null, loginModel.credentials!!)
            .build()

        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @After
    fun cleanUp() {
        mockServer.shutdown()
        db.close()
    }

    
    // Actual tests
    
    @Test
    fun testRefreshCollections_enqueuesWorker() {
        val service = createTestService(Service.TYPE_CALDAV)!!
        val workerName = RefreshCollectionsWorker.refreshCollections(context, service.id)
        assertTrue(workScheduledOrRunning(context, workerName))
    }

    @Test
    fun testOnStopped_stopsRefreshThread() {
        val service = createTestService(Service.TYPE_CALDAV)!!
        val workerName = RefreshCollectionsWorker.refreshCollections(context, service.id)
        WorkManager.getInstance(context).cancelUniqueWork(workerName)
        assertFalse(workScheduledOrRunning(context, workerName))

        // here we should test whether stopping the work really interrupts the refresh thread
    }

    @Test
    fun testQueryHomesets() {
        val service = createTestService(Service.TYPE_CARDDAV)!!
        val baseUrl = mockServer.url(PATH_CARDDAV + SUBPATH_PRINCIPAL)

        // Query home sets
        DavResource(client.okHttpClient, baseUrl).propfind(0, AddressbookHomeSet.NAME) { response, _ ->
            RefreshCollectionsWorker.Refresher(db, service, settings, client.okHttpClient)
                .queryHomeSets(baseUrl)
        }

        // Check home sets have been saved to database
        assertEquals(mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET/"), db.homeSetDao().getByService(service.id).first().url)
        assertEquals(1, db.homeSetDao().getByService(service.id).size)
    }


    // refreshHomesetsAndTheirCollections

    @Test
    fun refreshHomesetsAndTheirCollections_addsNewCollection() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        // save homeset in DB
        val homesetId = db.homeSetDao().insert(
            HomeSet(id=0, service.id, true, mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET"))
        )

        // Refresh
        RefreshCollectionsWorker.Refresher(db, service, settings, client.okHttpClient)
            .refreshHomesetsAndTheirCollections()

        // Check the collection defined in homeset is now in the database
        assertEquals(
            Collection(
                1,
                service.id,
                homesetId,
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
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description"
            )
        )

        // Refresh
        RefreshCollectionsWorker.Refresher(db, service, settings, client.okHttpClient)
            .refreshHomesetsAndTheirCollections()

        // Check the collection got updated
        assertEquals(
            Collection(
                collectionId,
                service.id,
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
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
                displayName = "My Contacts",
                description = "My Contacts Description",
                forceReadOnly = true,
                sync = true
            )
        )

        // Refresh
        RefreshCollectionsWorker.Refresher(db, service, settings, client.okHttpClient)
            .refreshHomesetsAndTheirCollections()

        // Check the collection got updated
        assertEquals(
            Collection(
                collectionId,
                service.id,
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
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/")
            )
        )

        // Refresh - should mark collection as homeless, because serverside homeset is empty
        RefreshCollectionsWorker.Refresher(db, service, settings, client.okHttpClient)
            .refreshHomesetsAndTheirCollections()

        // Check the collection, is now marked as homeless
        assertEquals(null, db.collectionDao().get(collectionId)!!.homeSetId)
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
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/"),
            )
        )

        // Refresh
        RefreshCollectionsWorker.Refresher(db, service, settings, client.okHttpClient)
            .refreshHomelessCollections()

        // Check the collection got updated - with display name and description
        assertEquals(
            Collection(
                collectionId,
                service.id,
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
    fun refreshHomelessCollections_deletesInaccessibleCollections() {
        val service = createTestService(Service.TYPE_CARDDAV)!!

        // place homeless collection in DB - it is also inaccessible
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                Collection.TYPE_ADDRESSBOOK,
                mockServer.url("$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_INACCESSIBLE")
            )
        )

        // Refresh - should delete collection
        RefreshCollectionsWorker.Refresher(db, service, settings, client.okHttpClient)
            .refreshHomelessCollections()

        // Check the collection got deleted
        assertEquals(null, db.collectionDao().get(collectionId))
    }
    
    // Test helpers and dependencies
    
    fun createTestService(serviceType: String) : Service? {
        val service = Service(id=0, accountName="test", type=serviceType, principal = null)
        val serviceId = db.serviceDao().insertOrReplace(service)
        return db.serviceDao().get(serviceId)
    }

    class TestDispatcher: Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path!!

            if (request.method.equals("PROPFIND", true)) {
                val properties = when (path) {
                    PATH_CALDAV,
                    PATH_CARDDAV ->
                        "<current-user-principal>" +
                        "   <href>$path${SUBPATH_PRINCIPAL}</href>" +
                        "</current-user-principal>"

                    PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                        "<CARD:addressbook-home-set>" +
                        "   <href>${PATH_CARDDAV}${SUBPATH_ADDRESSBOOK_HOMESET}</href>" +
                        "</CARD:addressbook-home-set>"

                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK + "/",
                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET ->
                        "<resourcetype>" +
                        "   <collection/>" +
                        "   <CARD:addressbook/>" +
                        "</resourcetype>" +
                        "<displayname>My Contacts</displayname>" +
                        "<CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>"

                    PATH_CALDAV + SUBPATH_PRINCIPAL ->
                        "<CAL:calendar-user-address-set>" +
                        "  <href>urn:unknown-entry</href>" +
                        "  <href>mailto:email1@example.com</href>" +
                        "  <href>mailto:email2@example.com</href>" +
                        "</CAL:calendar-user-address-set>"

                    else -> ""
                }

                var responseBody: String = ""
                var responseCode: Int = 207
                when (path) {
                    PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET ->
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

                Logger.log.info("Queried: $path")
                Logger.log.info("Response: $responseBody")
                return MockResponse()
                    .setResponseCode(responseCode)
                    .setBody(responseBody)
            }

            return MockResponse().setResponseCode(404)
        }

    }
}