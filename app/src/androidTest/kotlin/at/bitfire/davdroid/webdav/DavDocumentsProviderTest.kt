/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.network.HttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
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
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class DavDocumentsProviderTest {

    companion object {
        private const val PATH_WEBDAV_ROOT = "/webdav"
    }

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var credentialsStore: CredentialsStore
    
    @Inject
    lateinit var davDocumentsActorFactory: DavDocumentsProvider.DavDocumentsActor.Factory

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder
    
    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var testDispatcher: TestDispatcher

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient

    @Before
    fun setUp() {
        hiltRule.inject()

        server = MockWebServer().apply {
            dispatcher = testDispatcher
            start()
        }

        client = httpClientBuilder.build()

        // mock server delivers HTTP without encryption
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }


    @Test
    fun testDoQueryChildren_insert() = runTest {
        // Create parent and root in database
        val id = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", server.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(id)
        val parent = db.webDavDocumentDao().getOrCreateRoot(webDavMount)

        // Query
        val actor = davDocumentsActorFactory.create(
            cookieStore = mutableMapOf(),
            credentialsStore = credentialsStore
        )
        actor.queryChildren(parent)

        // Assert new children were inserted into db
        assertEquals(3, db.webDavDocumentDao().getChildren(parent.id).size)
        assertEquals("Library", db.webDavDocumentDao().getChildren(parent.id)[0].displayName)
        assertEquals("MeowMeow_Cats.docx", db.webDavDocumentDao().getChildren(parent.id)[1].displayName)
        assertEquals("Secret_Document.pages", db.webDavDocumentDao().getChildren(parent.id)[2].displayName)
    }

    @Test
    fun testDoQueryChildren_update() = runTest {
        // Create parent and root in database
        val mountId = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", server.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(mountId)
        val parent = db.webDavDocumentDao().getOrCreateRoot(webDavMount)
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
        val actor = davDocumentsActorFactory.create(
            cookieStore = mutableMapOf(),
            credentialsStore = credentialsStore
        )
        actor.queryChildren(parent)

        // Assert parent and children were updated in database
        assertEquals("Cats WebDAV", db.webDavDocumentDao().get(parent.id)!!.displayName)
        assertEquals("Library", db.webDavDocumentDao().getChildren(parent.id)[0].name)
        assertEquals("Library", db.webDavDocumentDao().getChildren(parent.id)[0].displayName)

    }

    @Test
    fun testDoQueryChildren_delete() = runTest {
        // Create parent and root in database
        val mountId = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", server.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(mountId)
        val parent = db.webDavDocumentDao().getOrCreateRoot(webDavMount)

        // Create a folder
        val folderId = db.webDavDocumentDao().insert(
            WebDavDocument(0, mountId, parent.id, "deleteme", true, "Should be deleted")
        )
        assertEquals("deleteme", db.webDavDocumentDao().get(folderId)!!.name)

        // Query - discovers serverside deletion
        val actor = davDocumentsActorFactory.create(
            cookieStore = mutableMapOf(),
            credentialsStore = credentialsStore
        )
        actor.queryChildren(parent)

        // Assert folder got deleted
        assertEquals(null, db.webDavDocumentDao().get(folderId))
    }

    @Test
    fun testDoQueryChildren_updateTwoDirectoriesSimultaneously() = runTest {
        // Create root in database
        val mountId = db.webDavMountDao().insert(WebDavMount(0, "Cat food storage", server.url(PATH_WEBDAV_ROOT)))
        val webDavMount = db.webDavMountDao().getById(mountId)
        val root = db.webDavDocumentDao().getOrCreateRoot(webDavMount)

        // Create two directories
        val parent1Id = db.webDavDocumentDao().insert(WebDavDocument(0, mountId, root.id, "parent1", true))
        val parent2Id = db.webDavDocumentDao().insert(WebDavDocument(0, mountId, root.id, "parent2", true))
        val parent1 = db.webDavDocumentDao().get(parent1Id)!!
        val parent2 = db.webDavDocumentDao().get(parent2Id)!!
        assertEquals("parent1", parent1.name)
        assertEquals("parent2", parent2.name)

        // Query - find children of two nodes simultaneously
        val actor = davDocumentsActorFactory.create(
            cookieStore = mutableMapOf(),
            credentialsStore = credentialsStore
        )
        actor.queryChildren(parent1)
        actor.queryChildren(parent2)

        // Assert the two folders names have changed
        assertEquals("childOne.txt", db.webDavDocumentDao().getChildren(parent1Id)[0].name)
        assertEquals("childTwo.txt", db.webDavDocumentDao().getChildren(parent2Id)[0].name)
    }


    // mock server

    class TestDispatcher @Inject constructor(
        private val logger: Logger
    ): Dispatcher() {

        data class Resource(
            val name: String,
            val props: String
        )

        override fun dispatch(request: RecordedRequest): MockResponse {
            logger.info("Request: $request")
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

                logger.info("Response: $multistatus")
                return MockResponse()
                    .setResponseCode(207)
                    .setBody(multistatus)
            }

            return MockResponse().setResponseCode(404)
        }

    }

}