/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.migration.AutoMigration12
import at.bitfire.davdroid.db.migration.AutoMigration16
import at.bitfire.davdroid.db.migration.AutoMigration18
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavHomeSetRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class HomeSetRefresherTest {

    companion object {
        const val BASE_URL = "https://dav.example.com"

        private const val PATH_CARDDAV = "/carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL = "/addressbooks-homeset"
        private const val SUBPATH_ADDRESSBOOK_HOMESET_EMPTY = "/addressbooks-homeset-empty"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/my-contacts"

        val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml; charset=UTF-8")

        fun multistatus(href: String, props: String) =
            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                    "<response><href>$href</href><propstat><prop>$props</prop><status>HTTP/1.1 200 OK</status></propstat></response>" +
                    "</multistatus>"
    }

    @get:Rule
    val mockKRule = MockKRule(this)

    @MockK(relaxed = true)
    lateinit var settings: SettingsManager

    private lateinit var db: AppDatabase
    private lateinit var client: HttpClient
    private lateinit var service: Service

    private fun buildMockEngine() = MockEngine { request ->
        if (request.method.value != "PROPFIND")
            return@MockEngine respond("", HttpStatusCode.NotFound)

        val path = request.url.encodedPath.trimEnd('/')
        val props = when (path) {
            PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL ->
                "<resourcetype><collection/><CARD:addressbook/></resourcetype>" +
                        "<displayname>My Contacts</displayname>" +
                        "<CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                        "<owner><href>$PATH_CARDDAV$SUBPATH_PRINCIPAL</href></owner>"
            PATH_CARDDAV + SUBPATH_ADDRESSBOOK_HOMESET_EMPTY -> ""
            else -> return@MockEngine respond("", HttpStatusCode.NotFound)
        }
        respond(
            multistatus(PATH_CARDDAV + SUBPATH_ADDRESSBOOK, props),
            HttpStatusCode.MultiStatus, xmlHeaders
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries()
            .addAutoMigrationSpec(AutoMigration18())
            .addAutoMigrationSpec(AutoMigration16())
            .addAutoMigrationSpec(AutoMigration12(ApplicationProvider.getApplicationContext(), Logger.getLogger("test")))
            .fallbackToDestructiveMigration()
            .build()
        client = HttpClient(buildMockEngine())

        val serviceId = db.serviceDao().insertOrReplace(
            Service(id = 0, accountName = "test", type = Service.TYPE_CARDDAV, principal = null)
        )
        service = db.serviceDao().get(serviceId)!!
    }

    @After
    fun tearDown() {
        client.close()
        db.close()
    }

    private fun makeRefresher() = HomeSetRefresher(
        service, client, db, Logger.getLogger("test"),
        DavCollectionRepository(
            ApplicationProvider.getApplicationContext(),
            db,
            Logger.getLogger("test"),
            { mockk(relaxed = true) },
            { mockk(relaxed = true) },
            mockk<DavServiceRepository>(relaxed = true)
        ),
        DavHomeSetRepository(db),
        settings
    )


    // refreshHomesetsAndTheirCollections

    @Test
    fun refreshHomesetsAndTheirCollections_addsNewCollection() = runTest {
        val homesetId = db.homeSetDao().insert(
            HomeSet(id = 0, service.id, true, "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL".toHttpUrl())
        )

        makeRefresher().refreshHomesetsAndTheirCollections()

        assertEquals(
            Collection(
                1,
                service.id,
                homesetId,
                1, // will have gotten an owner too
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl(),
                displayName = "My Contacts",
                description = "My Contacts Description"
            ),
            db.collectionDao().getByService(service.id).first()
        )
    }

    @Test
    fun refreshHomesetsAndTheirCollections_updatesExistingCollection() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl(),
                displayName = "My Contacts",
                description = "My Contacts Description"
            )
        )

        makeRefresher().refreshHomesetsAndTheirCollections()

        assertEquals(
            Collection(
                collectionId,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl(),
                displayName = "My Contacts",
                description = "My Contacts Description"
            ),
            db.collectionDao().get(collectionId)
        )
    }

    @Test
    fun refreshHomesetsAndTheirCollections_preservesCollectionFlags() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl(),
                displayName = "My Contacts",
                description = "My Contacts Description",
                forceReadOnly = true,
                sync = true
            )
        )

        makeRefresher().refreshHomesetsAndTheirCollections()

        assertEquals(
            Collection(
                collectionId,
                service.id,
                null,
                null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl(),
                displayName = "My Contacts",
                description = "My Contacts Description",
                forceReadOnly = true,
                sync = true
            ),
            db.collectionDao().get(collectionId)
        )
    }

    @Test
    fun refreshHomesetsAndTheirCollections_marksRemovedCollectionsAsHomeless() = runTest {
        val homesetId = db.homeSetDao().insert(
            HomeSet(id = 0, service.id, true, "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_EMPTY".toHttpUrl())
        )

        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                homesetId,
                null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl()
            )
        )

        makeRefresher().refreshHomesetsAndTheirCollections()

        assertEquals(null, db.collectionDao().get(collectionId)!!.homeSetId)
    }

    @Test
    fun refreshHomesetsAndTheirCollections_addsOwnerUrls() = runTest {
        val homesetId = db.homeSetDao().insert(
            HomeSet(id = 0, service.id, true, "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_HOMESET_PERSONAL".toHttpUrl())
        )

        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0,
                service.id,
                homesetId,
                null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl()
            )
        )

        assertEquals(0, db.principalDao().getByService(service.id).size)
        makeRefresher().refreshHomesetsAndTheirCollections()

        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL".toHttpUrl(), principals[0].url)
        assertEquals(null, principals[0].displayName)
        assertEquals(
            principals[0].id,
            db.collectionDao().get(collectionId)!!.ownerId
        )
    }


    // shouldPreselect

    @Test
    fun shouldPreselect_none() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_NONE
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            0, service.id, 0, type = Collection.TYPE_ADDRESSBOOK,
            url = "$BASE_URL/addressbook-homeset/addressbook/".toHttpUrl()
        )
        val homesets = listOf(
            HomeSet(
                id = 0, serviceId = service.id, personal = true,
                url = "$BASE_URL/addressbook-homeset/".toHttpUrl()
            )
        )

        assertFalse(makeRefresher().shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_all() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_ALL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            0, service.id, 0, type = Collection.TYPE_ADDRESSBOOK,
            url = "$BASE_URL/addressbook-homeset/addressbook/".toHttpUrl()
        )
        val homesets = listOf(
            HomeSet(
                id = 0, serviceId = service.id, personal = false,
                url = "$BASE_URL/addressbook-homeset/".toHttpUrl()
            )
        )

        assertTrue(makeRefresher().shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_all_blacklisted() {
        val url = "$BASE_URL/addressbook-homeset/addressbook/".toHttpUrl()

        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_ALL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns url.toString()

        val collection = Collection(
            id = 0, serviceId = service.id, homeSetId = 0,
            type = Collection.TYPE_ADDRESSBOOK, url = url
        )
        val homesets = listOf(
            HomeSet(
                id = 0, serviceId = service.id, personal = false,
                url = "$BASE_URL/addressbook-homeset/".toHttpUrl()
            )
        )

        assertFalse(makeRefresher().shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_personal_notPersonal() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            id = 0, serviceId = service.id, homeSetId = 0,
            type = Collection.TYPE_ADDRESSBOOK,
            url = "$BASE_URL/addressbook-homeset/addressbook/".toHttpUrl()
        )
        val homesets = listOf(
            HomeSet(
                id = 0, serviceId = service.id, personal = false,
                url = "$BASE_URL/addressbook-homeset/".toHttpUrl()
            )
        )

        assertFalse(makeRefresher().shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_personal_isPersonal() {
        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns ""

        val collection = Collection(
            0, service.id, 0, type = Collection.TYPE_ADDRESSBOOK,
            url = "$BASE_URL/addressbook-homeset/addressbook/".toHttpUrl()
        )
        val homesets = listOf(
            HomeSet(
                id = 0, serviceId = service.id, personal = true,
                url = "$BASE_URL/addressbook-homeset/".toHttpUrl()
            )
        )

        assertTrue(makeRefresher().shouldPreselect(collection, homesets))
    }

    @Test
    fun shouldPreselect_personal_isPersonalButBlacklisted() {
        val collectionUrl = "$BASE_URL/addressbook-homeset/addressbook/".toHttpUrl()

        every { settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS) } returns Settings.PRESELECT_COLLECTIONS_PERSONAL
        every { settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED) } returns collectionUrl.toString()

        val collection = Collection(
            id = 0, serviceId = service.id, homeSetId = 0,
            type = Collection.TYPE_ADDRESSBOOK, url = collectionUrl
        )
        val homesets = listOf(
            HomeSet(
                id = 0, serviceId = service.id, personal = true,
                url = "$BASE_URL/addressbook-homeset/".toHttpUrl()
            )
        )

        assertFalse(makeRefresher().shouldPreselect(collection, homesets))
    }

}
