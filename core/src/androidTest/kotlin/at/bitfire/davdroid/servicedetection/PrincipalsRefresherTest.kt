/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.DavUtils.toUrl
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
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
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

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var principalsRefresher: PrincipalsRefresher.Factory

    @BindValue
    @MockK(relaxed = true)
    lateinit var settings: SettingsManager

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


    @Test
    fun refreshPrincipals_inaccessiblePrincipal() = runTest {
        val principalId = db.principalDao().insert(
            Principal(
                0, service.id,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL_INACCESSIBLE".toUrl(),
                null
            )
        )
        db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, principalId,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toUrl()
            )
        )

        principalsRefresher.create(service, client).refreshPrincipals()

        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL_INACCESSIBLE".toUrl(), principals[0].url)
        assertEquals(null, principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_updatesPrincipal() = runTest {
        val principalId = db.principalDao().insert(
            Principal(
                0, service.id,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL".toUrl(),
                null
            )
        )
        db.collectionDao().insertOrUpdateByUrl(
            Collection(
                0, service.id, null, principalId,
                Collection.TYPE_ADDRESSBOOK,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_ADDRESSBOOK/".toUrl()
            )
        )

        principalsRefresher.create(service, client).refreshPrincipals()

        val principals = db.principalDao().getByService(service.id)
        assertEquals(1, principals.size)
        assertEquals("$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL".toUrl(), principals[0].url)
        assertEquals("Mr. Wobbles", principals[0].displayName)
    }

    @Test
    fun refreshPrincipals_deletesPrincipalsWithoutCollections() = runTest {
        db.principalDao().insert(
            Principal(
                0, service.id,
                "$BASE_URL$PATH_CARDDAV$SUBPATH_PRINCIPAL_WITHOUT_COLLECTIONS/".toUrl()
            )
        )

        principalsRefresher.create(service, client).refreshPrincipals()

        assertEquals(0, db.principalDao().getByService(service.id).size)
    }

}
