/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.toUrl
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
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

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var refresherFactory: CollectionsWithoutHomeSetRefresher.Factory

    @BindValue
    @MockK(relaxed = true)
    lateinit var settings: SettingsManager

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


    // refreshCollectionsWithoutHomeSet

    @Test
    fun refreshCollectionsWithoutHomeSet_updatesExistingCollection() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toUrl()
            )
        )

        refresherFactory.create(service, client).refreshCollectionsWithoutHomeSet()

        assertEquals(
            Collection(
                collectionId, service.id, null,
                1, // will have gotten an owner too
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toUrl(),
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
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK_INACCESSIBLE".toUrl()
            )
        )

        refresherFactory.create(service, client).refreshCollectionsWithoutHomeSet()

        assertEquals(null, db.collectionDao().get(collectionId))
    }

    @Test
    fun refreshCollectionsWithoutHomeSet_addsOwnerUrls() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, null,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toUrl()
            )
        )

        assertEquals(0, db.principalDao().getByService(service.id).size)
        refresherFactory.create(service, client).refreshCollectionsWithoutHomeSet()

        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL".toUrl(), principals[0].url)
        assertEquals(null, principals[0].displayName)
        assertEquals(
            principals[0].id,
            db.collectionDao().get(collectionId)!!.ownerId
        )
    }

}
