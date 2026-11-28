/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import at.bitfire.davdroid.db.WebDavDocument
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class QueryChildDocumentsOperationTest : WebDavOperationTest() {

    @Inject
    lateinit var operation: QueryChildDocumentsOperation

    @BindValue
    @JvmField
    val httpClientBuilder = mockHttpClientBuilder()

    @Before
    fun setUp() {
        handler = { request -> respondPropfind(request) }
    }


    @Test
    fun testDoQueryChildren_insert() = runTest {
        // Query
        operation.queryChildren(root)

        // Assert new children were inserted into db
        assertEquals(3, documentDao.getChildren(root.id).size)
        assertEquals("Library", documentDao.getChildren(root.id)[0].displayName)
        assertEquals("MeowMeow_Cats.docx", documentDao.getChildren(root.id)[1].displayName)
        assertEquals("Secret_Document.pages", documentDao.getChildren(root.id)[2].displayName)
    }

    @Test
    fun testDoQueryChildren_update() = runTest {
        // Create parent and root in database
        assertEquals("Test Mount", documentDao.get(root.id)!!.displayName)

        // Create a folder
        val folderId = documentDao.insert(
            WebDavDocument(mountId = root.mountId, parentId = root.id, name = "My_Books", isDirectory = true, displayName = "My Books")
        )
        assertEquals("My_Books", documentDao.get(folderId)!!.name)
        assertEquals("My Books", documentDao.get(folderId)!!.displayName)

        // Query - should update the parent displayname and folder name
        operation.queryChildren(root)

        // Assert parent and children were updated in database
        assertEquals("Cats WebDAV", documentDao.get(root.id)!!.displayName)
        assertEquals("Library", documentDao.getChildren(root.id)[0].name)
        assertEquals("Library", documentDao.getChildren(root.id)[0].displayName)
    }

    @Test
    fun testDoQueryChildren_delete() = runTest {
        // Create a folder
        val folder = insertDocument(root, "deleteme", true)

        // Query - discovers serverside deletion
        operation.queryChildren(root)

        // Assert folder got deleted
        assertEquals(null, documentDao.get(folder.id))
    }

    @Test
    fun testDoQueryChildren_propfindFails_doesNotDeleteChildren() = runTest {
        // Create a child document in DB
        val child = insertDocument(root, "some_file.txt", false)
        handler = { respond("", HttpStatusCode.InternalServerError) }

        // Query - PROPFIND fails
        operation.queryChildren(root)

        // Child must NOT have been deleted despite the PROPFIND failure
        assertNotNull(documentDao.get(child.id))
        assertEquals("some_file.txt", documentDao.get(child.id)!!.name)
    }

    @Test
    fun testDoQueryChildren_updateTwoDirectoriesSimultaneously() = runTest {
        // Create two directories
        val parent1 = insertDocument(root, "parent1", true)
        val parent2 = insertDocument(root, "parent2", true)

        // Query - find children of two nodes simultaneously
        operation.queryChildren(parent1)
        operation.queryChildren(parent2)

        // Assert the two folders names have changed
        assertEquals("childOne.txt", documentDao.getChildren(parent1.id)[0].name)
        assertEquals("childTwo.txt", documentDao.getChildren(parent2.id)[0].name)
    }

    @Test
    fun testDoQueryChildren_folder_usesTrailingSlash() = runTest {
        val folder = insertDocument(root, "parent1", true)

        operation.queryChildren(folder)

        val propfind = mockEngine.requestHistory.single()
        assertEquals("PROPFIND", propfind.method.value)
        assertEquals("https://mock.example.com/webdav/parent1/", propfind.url.toString())
    }

    @Test
    fun testDoQueryChildren_blankDisplayName_storedAsNull() = runTest {
        val parent = insertDocument(root, "parent3", true)

        operation.queryChildren(parent)

        val child = documentDao.getChildren(parent.id).single()
        assertEquals("blank.txt", child.name)
        assertNull(child.displayName)
    }

    @Test
    fun testDoQueryChildren_displayNameWithSurroundingWhitespace_trimmed() = runTest {
        val parent = insertDocument(root, "parent4", true)

        operation.queryChildren(parent)

        assertEquals("Spaced", documentDao.getChildren(parent.id).single().displayName)
    }


    // mock server

    private val xmlHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())

    /** Answers PROPFIND requests to the folder URLs (with trailing slash) below. */
    private fun MockRequestHandleScope.respondPropfind(request: HttpRequestData): HttpResponseData {
        val requestPath = request.url.encodedPath

        if (!request.method.value.equals("PROPFIND", ignoreCase = true))
            return respond("", HttpStatusCode.NotFound)

        val propsMap = mapOf(
            "/webdav/" to listOf(
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
            "/webdav/parent1/" to listOf(
                Resource("childOne.txt", "<displayname>childOne.txt</displayname>")
            ),
            "/webdav/parent2/" to listOf(
                Resource("childTwo.txt", "<displayname>childTwo.txt</displayname>")
            ),
            "/webdav/parent3/" to listOf(
                Resource("blank.txt", "<displayname> </displayname>")
            ),
            "/webdav/parent4/" to listOf(
                Resource("spaced.txt", "<displayname> Spaced </displayname>")
            )
        )

        val resources = propsMap[requestPath]
            ?: return respond("", HttpStatusCode.NotFound)

        val responses = resources.joinToString("") { resource ->
            "<response><href>$requestPath${resource.name}</href>" +
                    "<propstat><prop>${resource.props}</prop></propstat></response>"
        }
        val multistatus =
            "<multistatus xmlns='DAV:' " +
                    "xmlns:CARD='urn:ietf:params:xml:ns:carddav' " +
                    "xmlns:CAL='urn:ietf:params:xml:ns:caldav'>" +
                    responses +
                    "</multistatus>"

        return respond(multistatus, HttpStatusCode.MultiStatus, xmlHeaders)
    }

    private data class Resource(val name: String, val props: String)

}
