/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.db.WebDavMountDao
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import io.mockk.coEvery
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException
import javax.inject.Inject

@HiltAndroidTest
class RenameDocumentOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var operation: RenameDocumentOperation

    @BindValue
    @JvmField
    val httpClientBuilder: DavHttpClientBuilder = mockk()

    private val requests = mutableListOf<RecordedRequest>()
    private var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond("", HttpStatusCode.NotFound)
    }
    private val mockEngine = MockEngine { request ->
        requests += RecordedRequest(request.method.value, request.headers["Destination"], request.body.text())
        responder(request)
    }

    private lateinit var mountDao: WebDavMountDao
    private lateinit var mount: WebDavMount
    private lateinit var root: WebDavDocument

    @Before
    fun setUp() {
        hiltRule.inject()
        coEvery { httpClientBuilder.build(any(), any()) } answers { HttpClient(mockEngine) }

        mountDao = db.webDavMountDao()
        runBlocking {
            val mountId = mountDao.insert(WebDavMount(0, "Test Mount", Url("https://mock.example.com/webdav/")))
            mount = mountDao.getById(mountId)
            root = db.webDavDocumentDao().getOrCreateRoot(mount)
        }
    }

    @After
    fun tearDown() {
        runBlocking { mountDao.delete(mount) }
    }


    @Test
    fun testRename_withDifferingDisplayName_proppatchesDisplayName() = runTest {
        val docId = insertDocument(name = "g5d5q4edj00n", displayName = "Upload", isDirectory = true)
        responder = { request -> respondProppatch(request) }

        val result = operation(docId.toString(), "Photos")

        assertNull(result)
        assertEquals(listOf("PROPPATCH"), requests.map { it.method })
        assertTrue(requests[0].body.contains("displayname"))
        assertTrue(requests[0].body.contains("Photos"))
        val doc = db.webDavDocumentDao().get(docId)!!
        assertEquals("g5d5q4edj00n", doc.name)
        assertEquals("Photos", doc.displayName)
    }

    @Test
    fun testRename_withDifferingDisplayName_stripsControlChars() = runTest {
        val docId = insertDocument(name = "g5d5q4edj00n", displayName = "Upload", isDirectory = true)
        responder = { request -> respondProppatch(request) }

        operation(docId.toString(), "Pho\u0007tos")

        assertEquals("Photos", db.webDavDocumentDao().get(docId)!!.displayName)
    }

    @Test
    fun testRename_withoutDisplayName_moves() = runTest {
        val docId = insertDocument(name = "file.txt", displayName = null)
        responder = { respond("", HttpStatusCode.Created) }

        val result = operation(docId.toString(), "file2.txt")

        assertNull(result)
        assertEquals(listOf("MOVE"), requests.map { it.method })
        assertTrue(requests[0].destination!!.endsWith("/file2.txt"))
        val doc = db.webDavDocumentDao().get(docId)!!
        assertEquals("file2.txt", doc.name)
        assertNull(doc.displayName)
    }

    @Test
    fun testRename_withDisplayNameEqualToName_moves() = runTest {
        val docId = insertDocument(name = "file.txt", displayName = "file.txt")
        responder = { respond("", HttpStatusCode.Created) }

        operation(docId.toString(), "file2.txt")

        assertEquals(listOf("MOVE"), requests.map { it.method })
        val doc = db.webDavDocumentDao().get(docId)!!
        assertEquals("file2.txt", doc.name)
        assertNull(doc.displayName)
    }

    @Test
    fun testRename_proppatchHttpError_fallsBackToMove() = runTest {
        val docId = insertDocument(name = "g5d5q4edj00n", displayName = "Upload", isDirectory = true)
        responder = { request ->
            when (request.method.value) {
                "PROPPATCH" -> respond("", HttpStatusCode.Forbidden)
                else -> respond("", HttpStatusCode.Created)
            }
        }

        operation(docId.toString(), "Photos")

        assertEquals(listOf("PROPPATCH", "MOVE"), requests.map { it.method })
        val doc = db.webDavDocumentDao().get(docId)!!
        assertEquals("Photos", doc.name)
        assertNull(doc.displayName)
    }

    @Test
    fun testRename_proppatchPropstatForbidden_fallsBackToMove() = runTest {
        val docId = insertDocument(name = "g5d5q4edj00n", displayName = "Upload", isDirectory = true)
        responder = { request ->
            when (request.method.value) {
                "PROPPATCH" -> respondProppatch(request, HttpStatusCode.Forbidden)
                else -> respond("", HttpStatusCode.Created)
            }
        }

        operation(docId.toString(), "Photos")

        assertEquals(listOf("PROPPATCH", "MOVE"), requests.map { it.method })
        val doc = db.webDavDocumentDao().get(docId)!!
        assertEquals("Photos", doc.name)
        assertNull(doc.displayName)
    }

    @Test
    fun testRename_proppatchNoMultistatus_treatsAsSuccess() = runTest {
        val docId = insertDocument(name = "g5d5q4edj00n", displayName = "Upload", isDirectory = true)
        responder = { request ->
            when (request.method.value) {
                "PROPPATCH" -> respond("", HttpStatusCode.NoContent)
                else -> respond("", HttpStatusCode.Created)
            }
        }

        operation(docId.toString(), "Photos")

        assertEquals(listOf("PROPPATCH"), requests.map { it.method })
        val doc = db.webDavDocumentDao().get(docId)!!
        assertEquals("g5d5q4edj00n", doc.name)
        assertEquals("Photos", doc.displayName)
    }

    @Test
    fun testRename_resourceGone_throwsFileNotFound() = runTest {
        val docId = insertDocument(name = "g5d5q4edj00n", displayName = "Upload", isDirectory = true)
        responder = { respond("", HttpStatusCode.NotFound) }

        assertThrows(FileNotFoundException::class.java) {
            runBlocking { operation(docId.toString(), "Photos") }
        }
        assertEquals(listOf("PROPPATCH", "MOVE"), requests.map { it.method })
    }


    // helpers

    private suspend fun insertDocument(name: String, displayName: String?, isDirectory: Boolean = false): Long =
        db.webDavDocumentDao().insert(
            WebDavDocument(mountId = mount.id, parentId = root.id, name = name, isDirectory = isDirectory, displayName = displayName)
        )

    private fun MockRequestHandleScope.respondProppatch(request: HttpRequestData, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            "<multistatus xmlns='DAV:'><response><href>${request.url.encodedPath}</href>" +
                    "<propstat><prop><displayname/></prop><status>HTTP/1.1 ${status.value} ${status.description}</status></propstat>" +
                    "</response></multistatus>",
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
        )

    private fun OutgoingContent.text(): String =
        when (this) {
            is TextContent -> text
            is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
            else -> ""
        }

    private data class RecordedRequest(val method: String, val destination: String?, val body: String)

}
