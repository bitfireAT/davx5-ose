/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import androidx.test.core.app.ApplicationProvider
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.test.createTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
class CollectionsWithoutHomeSetRefresherTest {

    companion object {
        const val BASE_URL = "https://dav.example.com"

        private const val PATH_CARDDAV = "/carddav"
        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/my-contacts"
        private const val SUBPATH_ADDRESSBOOK_INACCESSIBLE = "/addressbooks/inaccessible-contacts"

        val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml; charset=UTF-8")

        fun multistatus(href: String, props: String) =
            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav' xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                    "<response><href>$href</href><propstat><prop>$props</prop><status>HTTP/1.1 200 OK</status></propstat></response>" +
                    "</multistatus>"
    }

    private lateinit var db: AppDatabase
    private lateinit var client: HttpClient
    private lateinit var service: Service

    private fun buildMockEngine() = MockEngine { request ->
        if (request.method.value != "PROPFIND")
            return@MockEngine respond("", HttpStatusCode.NotFound)

        when (val path = request.url.encodedPath.trimEnd('/')) {
            PATH_CARDDAV + SUBPATH_ADDRESSBOOK ->
                respond(
                    multistatus(
                        path,
                        "<resourcetype><collection/><CARD:addressbook/></resourcetype>" +
                                "<displayname>My Contacts</displayname>" +
                                "<CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                                "<owner><href>$PATH_CARDDAV$SUBPATH_PRINCIPAL</href></owner>"
                    ),
                    HttpStatusCode.MultiStatus, xmlHeaders
                )
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    @Before
    fun setUp() {
        db = createTestDatabase()
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

    private fun makeCollectionRepository() = DavCollectionRepository(
        ApplicationProvider.getApplicationContext(),
        db,
        Logger.getLogger("test"),
        { mockk(relaxed = true) },
        { mockk(relaxed = true) },
        mockk<DavServiceRepository>(relaxed = true)
    )


    // refreshCollectionsWithoutHomeSet

    @Test
    fun refreshCollectionsWithoutHomeSet_updatesExistingCollection() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl()
            )
        )

        CollectionsWithoutHomeSetRefresher(service, client, db, makeCollectionRepository())
            .refreshCollectionsWithoutHomeSet()

        assertEquals(
            Collection(
                collectionId, service.id, null,
                1, // will have gotten an owner too
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl(),
                displayName = "My Contacts",
                description = "My Contacts Description"
            ),
            db.collectionDao().get(collectionId)
        )
    }

    @Test
    fun refreshCollectionsWithoutHomeSet_deletesInaccessibleCollectionsWithoutHomeSet() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_INACCESSIBLE".toHttpUrl()
            )
        )

        CollectionsWithoutHomeSetRefresher(service, client, db, makeCollectionRepository())
            .refreshCollectionsWithoutHomeSet()

        assertEquals(null, db.collectionDao().get(collectionId))
    }

    @Test
    fun refreshCollectionsWithoutHomeSet_addsOwnerUrls() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl()
            )
        )

        assertEquals(0, db.principalDao().getByService(service.id).size)
        CollectionsWithoutHomeSetRefresher(service, client, db, makeCollectionRepository())
            .refreshCollectionsWithoutHomeSet()

        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL".toHttpUrl(), principals[0].url)
        assertEquals(null, principals[0].displayName)
        assertEquals(
            principals[0].id,
            db.collectionDao().get(collectionId)!!.ownerId
        )
    }

}
