/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class QueryChildDocumentsOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var operation: QueryChildDocumentsOperation

    @BindValue
    @JvmField
    val httpClientBuilder: DavHttpClientBuilder = mockk()

    private lateinit var mount: WebDavMount
    private lateinit var rootDocument: WebDavDocument

    @Before
    fun setUp() {
        hiltRule.inject()
        every { httpClientBuilder.buildKtor(any(), any()) } answers { HttpClient(buildDefaultEngine()) }

        // mock server delivers HTTP without encryption
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)

        // create WebDAV mount and root document in DB
        runBlocking {
            val mountId = db.webDavMountDao().insert(
                WebDavMount(0, "Cat food storage", Url("https://dav.example.com$PATH_WEBDAV_ROOT"))
            )
            mount = db.webDavMountDao().getById(mountId)
            rootDocument = db.webDavDocumentDao().getOrCreateRoot(mount)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            db.webDavMountDao().deleteAsync(mount)
        }
    }


    @Test
    fun testDoQueryChildren_insert() = runTest {
        // Query
        operation.queryChildren(rootDocument)

        // Assert new children were inserted into db
        assertEquals(3, db.webDavDocumentDao().getChildren(rootDocument.id).size)
        assertEquals("Library", db.webDavDocumentDao().getChildren(rootDocument.id)[0].displayName)
        assertEquals("MeowMeow_Cats.docx", db.webDavDocumentDao().getChildren(rootDocument.id)[1].displayName)
        assertEquals("Secret_Document.pages", db.webDavDocumentDao().getChildren(rootDocument.id)[2].displayName)
    }

    @Test
    fun testDoQueryChildren_update() = runTest {
        // Create parent and root in database
        assertEquals("Cat food storage", db.webDavDocumentDao().get(rootDocument.id)!!.displayName)

        // Create a folder
        val folderId = db.webDavDocumentDao().insert(
            WebDavDocument(0, mount.id, rootDocument.id, "My_Books", true, "My Books")
        )
        assertEquals("My_Books", db.webDavDocumentDao().get(folderId)!!.name)
        assertEquals("My Books", db.webDavDocumentDao().get(folderId)!!.displayName)

        // Query - should update the parent displayname and folder name
        operation.queryChildren(rootDocument)

        // Assert parent and children were updated in database
        assertEquals("Cats WebDAV", db.webDavDocumentDao().get(rootDocument.id)!!.displayName)
        assertEquals("Library", db.webDavDocumentDao().getChildren(rootDocument.id)[0].name)
        assertEquals("Library", db.webDavDocumentDao().getChildren(rootDocument.id)[0].displayName)
    }

    @Test
    fun testDoQueryChildren_delete() = runTest {
        // Create a folder
        val folderId = db.webDavDocumentDao().insert(
            WebDavDocument(0, mount.id, rootDocument.id, "deleteme", true, "Should be deleted")
        )
        assertEquals("deleteme", db.webDavDocumentDao().get(folderId)!!.name)

        // Query - discovers serverside deletion
        operation.queryChildren(rootDocument)

        // Assert folder got deleted
        assertEquals(null, db.webDavDocumentDao().get(folderId))
    }

    @Test
    fun testDoQueryChildren_propfindFails_doesNotDeleteChildren() = runTest {
        // Create a child document in DB
        val childId = db.webDavDocumentDao().insert(
            WebDavDocument(0, mount.id, rootDocument.id, "some_file.txt", false, "Some File")
        )
        assertNotNull(db.webDavDocumentDao().get(childId))

        every { httpClientBuilder.buildKtor(any(), any()) } answers {
            HttpClient(MockEngine { respond("", HttpStatusCode.InternalServerError) })
        }

        // Query - PROPFIND fails
        operation.queryChildren(rootDocument)

        // Child must NOT have been deleted despite the PROPFIND failure
        assertNotNull(db.webDavDocumentDao().get(childId))
        assertEquals("some_file.txt", db.webDavDocumentDao().get(childId)!!.name)
    }

    @Test
    fun testDoQueryChildren_updateTwoDirectoriesSimultaneously() = runTest {
        // Create two directories
        val parent1Id = db.webDavDocumentDao().insert(WebDavDocument(0, mount.id, rootDocument.id, "parent1", true))
        val parent2Id = db.webDavDocumentDao().insert(WebDavDocument(0, mount.id, rootDocument.id, "parent2", true))
        val parent1 = db.webDavDocumentDao().get(parent1Id)!!
        val parent2 = db.webDavDocumentDao().get(parent2Id)!!
        assertEquals("parent1", parent1.name)
        assertEquals("parent2", parent2.name)

        // Query - find children of two nodes simultaneously
        operation.queryChildren(parent1)
        operation.queryChildren(parent2)

        // Assert the two folders names have changed
        assertEquals("childOne.txt", db.webDavDocumentDao().getChildren(parent1Id)[0].name)
        assertEquals("childTwo.txt", db.webDavDocumentDao().getChildren(parent2Id)[0].name)
    }


    // mock engine

    private val xmlHeaders = headersOf("Content-Type", "application/xml; charset=UTF-8")

    private fun buildDefaultEngine() = MockEngine { request ->
        val requestPath = request.url.encodedPath.trimEnd('/')

        if (!request.method.value.equals("PROPFIND", ignoreCase = true))
            return@MockEngine respond("", HttpStatusCode.NotFound)

        val propsMap = mapOf(
            PATH_WEBDAV_ROOT to listOf(
                Resource("",
                    "<resourcetype><collection/></resourcetype>" +
                            "<displayname>Cats WebDAV</displayname>"
                ),
                Resource("Secret_Document.pages",
                    "<displayname>Secret_Document.pages</displayname>"
                ),
                Resource("MeowMeow_Cats.docx",
                    "<displayname>MeowMeow_Cats.docx</displayname>"
                ),
                Resource("Library",
                    "<resourcetype><collection/></resourcetype>" +
                            "<displayname>Library</displayname>"
                )
            ),
            "$PATH_WEBDAV_ROOT/parent1" to listOf(
                Resource("childOne.txt", "<displayname>childOne.txt</displayname>")
            ),
            "$PATH_WEBDAV_ROOT/parent2" to listOf(
                Resource("childTwo.txt", "<displayname>childTwo.txt</displayname>")
            )
        )

        val resources = propsMap[requestPath]
            ?: return@MockEngine respond("", HttpStatusCode.NotFound)

        val responses = resources.joinToString("") { resource ->
            "<response><href>$requestPath/${resource.name}</href>" +
                    "<propstat><prop>${resource.props}</prop></propstat></response>"
        }
        val multistatus =
            "<multistatus xmlns='DAV:' " +
                    "xmlns:CARD='urn:ietf:params:xml:ns:carddav' " +
                    "xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                    responses +
                    "</multistatus>"

        respond(multistatus, HttpStatusCode.MultiStatus, xmlHeaders)
    }

    private data class Resource(val name: String, val props: String)


    companion object {
        private const val PATH_WEBDAV_ROOT = "/webdav"
    }

}
