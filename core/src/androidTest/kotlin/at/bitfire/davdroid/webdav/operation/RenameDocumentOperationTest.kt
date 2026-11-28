/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import at.bitfire.dav4jvm.ktor.exception.NotFoundException
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class RenameDocumentOperationTest : WebDavOperationTest() {

    @Inject
    lateinit var operation: RenameDocumentOperation

    @BindValue
    @JvmField
    val httpClientBuilder = mockHttpClientBuilder()


    @Test
    fun testRename_withDifferingDisplayName_proppatchesDisplayName() = runTest {
        val doc = insertDocument(root, "g5d5q4edj00n", true, "Upload")
        handler = { request -> respondProppatch(request) }

        val result = operation(doc.id.toString(), "Photos")

        assertNull(result)
        val request = mockEngine.requestHistory.single()
        assertEquals("PROPPATCH", request.method.value)
        assertTrue(request.body.text().contains("displayname"))
        assertTrue(request.body.text().contains("Photos"))
        val updated = documentDao.get(doc.id)!!
        assertEquals("g5d5q4edj00n", updated.name)
        assertEquals("Photos", updated.displayName)
    }

    @Test
    fun testRename_withDifferingDisplayName_stripsControlChars() = runTest {
        val doc = insertDocument(root, "g5d5q4edj00n", true, "Upload")
        handler = { request -> respondProppatch(request) }

        operation(doc.id.toString(), "Pho\u0007tos")

        assertEquals("Photos", documentDao.get(doc.id)!!.displayName)
    }

    @Test
    fun testRename_withoutDisplayName_moves() = runTest {
        val doc = insertDocument(root, "file.txt", false)

        val result = operation(doc.id.toString(), "file2.txt")

        assertNull(result)
        val move = mockEngine.requestHistory.single()
        assertEquals("MOVE", move.method.value)
        assertTrue(move.headers["Destination"]!!.endsWith("/file2.txt"))
        val updated = documentDao.get(doc.id)!!
        assertEquals("file2.txt", updated.name)
        assertNull(updated.displayName)
    }

    @Test
    fun testRename_withDisplayNameEqualToName_moves() = runTest {
        val doc = insertDocument(root, "file.txt", false, "file.txt")

        operation(doc.id.toString(), "file2.txt")

        assertEquals(listOf("MOVE"), mockEngine.requestHistory.map { it.method.value })
        val updated = documentDao.get(doc.id)!!
        assertEquals("file2.txt", updated.name)
        assertNull(updated.displayName)
    }

    @Test
    fun testRename_proppatchHttpError_fallsBackToMove() = runTest {
        val doc = insertDocument(root, "g5d5q4edj00n", true, "Upload")
        handler = { request ->
            when (request.method.value) {
                "PROPPATCH" -> respond("", HttpStatusCode.Forbidden)
                else -> respond("", HttpStatusCode.Created)
            }
        }

        operation(doc.id.toString(), "Photos")

        assertEquals(listOf("PROPPATCH", "MOVE"), mockEngine.requestHistory.map { it.method.value })
        val updated = documentDao.get(doc.id)!!
        assertEquals("Photos", updated.name)
        assertNull(updated.displayName)
    }

    @Test
    fun testRename_proppatchPropstatForbidden_fallsBackToMove() = runTest {
        val doc = insertDocument(root, "g5d5q4edj00n", true, "Upload")
        handler = { request ->
            when (request.method.value) {
                "PROPPATCH" -> respondProppatch(request, HttpStatusCode.Forbidden)
                else -> respond("", HttpStatusCode.Created)
            }
        }

        operation(doc.id.toString(), "Photos")

        assertEquals(listOf("PROPPATCH", "MOVE"), mockEngine.requestHistory.map { it.method.value })
        val updated = documentDao.get(doc.id)!!
        assertEquals("Photos", updated.name)
        assertNull(updated.displayName)
    }

    @Test
    fun testRename_proppatchNoMultistatus_treatsAsSuccess() = runTest {
        val doc = insertDocument(root, "g5d5q4edj00n", true, "Upload")
        handler = { request ->
            when (request.method.value) {
                "PROPPATCH" -> respond("", HttpStatusCode.NoContent)
                else -> respond("", HttpStatusCode.Created)
            }
        }

        operation(doc.id.toString(), "Photos")

        assertEquals(listOf("PROPPATCH"), mockEngine.requestHistory.map { it.method.value })
        val updated = documentDao.get(doc.id)!!
        assertEquals("g5d5q4edj00n", updated.name)
        assertEquals("Photos", updated.displayName)
    }

    @Test
    fun testRename_resourceGone_throwsNotFound() = runTest {
        val doc = insertDocument(root, "g5d5q4edj00n", true, "Upload")
        handler = { respond("", HttpStatusCode.NotFound) }

        assertThrows(NotFoundException::class.java) {
            runBlocking { operation(doc.id.toString(), "Photos") }
        }
        assertEquals(listOf("PROPPATCH", "MOVE"), mockEngine.requestHistory.map { it.method.value })
    }

    @Test
    fun testRename_nameTaken_usesNextName() = runTest {
        handler = { request ->
            if (request.headers["Destination"].orEmpty().endsWith("/new.txt"))
                respond("", HttpStatusCode.PreconditionFailed)
            else
                respond("", HttpStatusCode.Created)
        }
        val doc = insertDocument(root, "old.txt", false)

        operation(doc.id.toString(), "new.txt")

        assertEquals("new_1.txt", documentDao.get(doc.id)!!.name)
    }

    @Test
    fun testRename_folder_usesTrailingSlashes() = runTest {
        val folder = insertDocument(root, "old", true)

        operation(folder.id.toString(), "new")

        val move = mockEngine.requestHistory.single()
        assertEquals("https://mock.example.com/webdav/old/", move.url.toString())
        assertEquals("https://mock.example.com/webdav/new/", move.headers["Destination"])
    }


    // helpers

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

}
