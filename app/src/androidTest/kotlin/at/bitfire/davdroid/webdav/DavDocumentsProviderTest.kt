/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import android.content.Context
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.CookieJar
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DavDocumentsProviderTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Inject lateinit var db: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private var mockServer =  MockWebServer()

    private lateinit var client: HttpClient

    companion object {
        private const val PATH_WEBDAV_ROOT = "/webdav"
    }

    @Before
    fun mockServerSetup() {
        // Start mock web server
        mockServer.dispatcher = TestDispatcher()
        mockServer.start()

        client = HttpClient.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build()

        // mock server delivers HTTP without encryption
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @After
    fun cleanUp() {
        mockServer.shutdown()
        db.close()
    }


    @Test
    fun testDoQueryChildren_insert() {
        // Create parent and root in database
        val id = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", mockServer.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(id)
        val parent = db.webDavDocumentDao().getOrCreateRoot(webDavMount)
        val cookieStore = mutableMapOf<Long, CookieJar>()

        // Query
        DavDocumentsProvider.DavDocumentsActor(context, db, cookieStore, CredentialsStore(context), context.getString(R.string.webdav_authority))
            .queryChildren(parent)

        // Assert new children were inserted into db
        assertEquals(3, db.webDavDocumentDao().getChildren(parent.id).size)
        assertEquals("Secret_Document.pages", db.webDavDocumentDao().getChildren(parent.id)[0].displayName)
        assertEquals("MeowMeow_Cats.docx", db.webDavDocumentDao().getChildren(parent.id)[1].displayName)
        assertEquals("Library", db.webDavDocumentDao().getChildren(parent.id)[2].displayName)
    }

    @Test
    fun testDoQueryChildren_update() {
        // Create parent and root in database
        val mountId = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", mockServer.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(mountId)
        val parent = db.webDavDocumentDao().getOrCreateRoot(webDavMount)
        val cookieStore = mutableMapOf<Long, CookieJar>()
        assertEquals("Cat food storage", db.webDavDocumentDao().get(parent.id)!!.displayName)

        // Create a folder
        val folderId = db.webDavDocumentDao().insert(
            WebDavDocument(
                0,
                mountId,
                parent.id,
                "My_Books",
                true,
                "My Books",
            )
        )
        assertEquals("My_Books", db.webDavDocumentDao().get(folderId)!!.name)
        assertEquals("My Books", db.webDavDocumentDao().get(folderId)!!.displayName)

        // Query - should update the parent displayname and folder name
        DavDocumentsProvider.DavDocumentsActor(context, db, cookieStore, CredentialsStore(context), context.getString(R.string.webdav_authority))
            .queryChildren(parent)

        // Assert parent and children were updated in database
        assertEquals("Cats WebDAV", db.webDavDocumentDao().get(parent.id)!!.displayName)
        assertEquals("Library", db.webDavDocumentDao().getChildren(parent.id)[2].name)
        assertEquals("Library", db.webDavDocumentDao().getChildren(parent.id)[2].displayName)

    }

    @Test
    fun testDoQueryChildren_delete() {
        // Create parent and root in database
        val mountId = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", mockServer.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(mountId)
        val parent = db.webDavDocumentDao().getOrCreateRoot(webDavMount)
        val cookieStore = mutableMapOf<Long, CookieJar>()

        // Create a folder
        val folderId = db.webDavDocumentDao().insert(
            WebDavDocument(0, mountId, parent.id, "deleteme", true, "Should be deleted")
        )
        assertEquals("deleteme", db.webDavDocumentDao().get(folderId)!!.name)

        // Query - discovers serverside deletion
        DavDocumentsProvider.DavDocumentsActor(context, db, cookieStore, CredentialsStore(context), context.getString(R.string.webdav_authority))
            .queryChildren(parent)

        // Assert folder got deleted
        assertEquals(null, db.webDavDocumentDao().get(folderId))
    }

    @Test
    fun testDoQueryChildren_updateTwoParentsSimultaneous() {
        // Create root in database
        val mountId = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", mockServer.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(mountId)
        val root = db.webDavDocumentDao().getOrCreateRoot(webDavMount)
        val cookieStore = mutableMapOf<Long, CookieJar>()

        // Create two parents
        val parent1Id = db.webDavDocumentDao().insert(WebDavDocument(0, mountId, root.id, "parent1", true))
        val parent2Id = db.webDavDocumentDao().insert(WebDavDocument(0, mountId, root.id, "parent2", true))
        val parent1 = db.webDavDocumentDao().get(parent1Id)!!
        val parent2 = db.webDavDocumentDao().get(parent2Id)!!
        assertEquals("parent1", parent1.name)
        assertEquals("parent2", parent2.name)

        // Query - find children of two nodes simultaneously
        DavDocumentsProvider.DavDocumentsActor(context, db, cookieStore, CredentialsStore(context), context.getString(R.string.webdav_authority))
            .queryChildren(parent1)
        DavDocumentsProvider.DavDocumentsActor(context, db, cookieStore, CredentialsStore(context), context.getString(R.string.webdav_authority))
            .queryChildren(parent2)

        // Assert the two folders names have changed
        assertEquals("childOne.txt", db.webDavDocumentDao().getChildren(parent1Id)[0].name)
        assertEquals("childTwo.txt", db.webDavDocumentDao().getChildren(parent2Id)[0].name)
    }


    // mock server

    class TestDispatcher: Dispatcher() {

        data class Resource(
            val name: String,
            val props: String
        )

        override fun dispatch(request: RecordedRequest): MockResponse {
            val requestPath = request.path!!.trimEnd('/')

            if (request.method.equals("PROPFIND", true)) {

                val propsMap = mutableMapOf(
                    PATH_WEBDAV_ROOT to arrayOf(
                        Resource("",
                            "<resourcetype><collection/></resourcetype>" +
                            "<displayname>Cats WebDAV</displayname>"
                        ),
                        Resource("Secret_Document.pages",
                            "<displayname>Secret_Document.pages</displayname>",
                        ),
                        Resource("MeowMeow_Cats.docx",
                            "<displayname>MeowMeow_Cats.docx</displayname>"
                        ),
                        Resource("Library",
                            "<resourcetype><collection/></resourcetype>" +
                            "<displayname>Library</displayname>"
                        )
                    ),

                    "$PATH_WEBDAV_ROOT/parent1" to arrayOf(
                        Resource("childOne.txt",
                            "<displayname>childOne.txt</displayname>"
                        ),
                    ),
                    "$PATH_WEBDAV_ROOT/parent2" to arrayOf(
                        Resource("childTwo.txt",
                            "<displayname>childTwo.txt</displayname>"
                        )
                    )
                )

                val responses = propsMap[requestPath]?.joinToString { resource ->
                    "<response><href>$requestPath/${resource.name}</href><propstat><prop>" +
                            resource.props +
                    "</prop></propstat></response>"
                }

                val multistatus =
                    "<multistatus xmlns='DAV:' " +
                        "xmlns:CARD='urn:ietf:params:xml:ns:carddav' " +
                        "xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                        responses +
                    "</multistatus>"

                Logger.log.info("Query path: $requestPath")
                Logger.log.info("Response: $multistatus")
                return MockResponse()
                    .setResponseCode(207)
                    .setBody(multistatus)
            }

            return MockResponse().setResponseCode(404)
        }

    }

}