/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.migration.AutoMigration12
import at.bitfire.davdroid.db.migration.AutoMigration16
import at.bitfire.davdroid.db.migration.AutoMigration18
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class PrincipalsRefresherTest {

    companion object {
        const val BASE_URL = "https://dav.example.com"

        private const val PATH_CARDDAV = "/carddav"

        private const val SUBPATH_PRINCIPAL = "/principal"
        private const val SUBPATH_PRINCIPAL_INACCESSIBLE = "/inaccessible-principal"
        private const val SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS = "/principal2"
        private const val SUBPATH_ADDRESSBOOK = "/addressbooks/my-contacts"

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
            PATH_CARDDAV + SUBPATH_PRINCIPAL ->
                respond(
                    multistatus(
                        path,
                        "<resourcetype><principal/></resourcetype>" +
                                "<displayname>Mr. Wobbles</displayname>"
                    ),
                    HttpStatusCode.MultiStatus, xmlHeaders
                )
            else -> respond("", HttpStatusCode.NotFound)
        }
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


    @Test
    fun refreshPrincipals_inaccessiblePrincipal() = runTest {
        val principalId = db.principalDao().insert(
            Principal(
                0, service.id,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL_INACCESSIBLE".toHttpUrl(),
                null
            )
        )
        db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, principalId,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl()
            )
        )

        PrincipalsRefresher(service, client, db, Logger.getLogger("test")).refreshPrincipals()

        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL_INACCESSIBLE".toHttpUrl(), principals[0].url)
        assertEquals(null, principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_updatesPrincipal() = runTest {
        val principalId = db.principalDao().insert(
            Principal(
                0, service.id,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL".toHttpUrl(),
                null
            )
        )
        db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, principalId,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toHttpUrl()
            )
        )

        PrincipalsRefresher(service, client, db, Logger.getLogger("test")).refreshPrincipals()

        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL".toHttpUrl(), principals[0].url)
        assertEquals("Mr. Wobbles", principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_deletesPrincipalsWithoutCollections() = runTest {
        db.principalDao().insert(
            Principal(
                0, service.id,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS/".toHttpUrl()
            )
        )

        PrincipalsRefresher(service, client, db, Logger.getLogger("test")).refreshPrincipals()

        assertEquals(0, db.principalDao().getByService(service.id).size)
    }

}
