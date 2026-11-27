/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.provider.DocumentsContract.Document
import at.bitfire.dav4jvm.ktor.exception.HttpException
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CreateDocumentOperationTest : WebDavOperationTest() {

    @Inject
    lateinit var operation: CreateDocumentOperation

    @BindValue
    @JvmField
    val httpClientBuilder = mockHttpClientBuilder()


    @Test
    fun testCreate_nameTaken_usesNextName() = runTest {
        handler = { request ->
            if (request.url.encodedPath.endsWith("/file.txt"))
                respond("", HttpStatusCode.PreconditionFailed)
            else
                respond("", HttpStatusCode.Created)
        }

        val result = operation(root.id.toString(), "text/plain", "file.txt")

        assertEquals("file_1.txt", documentDao.get(result!!.toLong())!!.name)
    }

    @Test
    fun testCreate_allNamesTaken_returnsNull() = runTest {
        handler = { respond("", HttpStatusCode.PreconditionFailed) }

        val result = operation(root.id.toString(), "text/plain", "file.txt")

        assertNull(result)
    }

    @Test
    fun testCreate_serverError_throws() = runTest {
        handler = { respond("", HttpStatusCode.BadGateway) }

        val e = assertThrows(HttpException::class.java) {
            runBlocking { operation(root.id.toString(), "text/plain", "file.txt") }
        }
        assertEquals(502, e.statusCode)
    }

    @Test
    fun testCreate_folder_usesTrailingSlash() = runTest {
        operation(root.id.toString(), Document.MIME_TYPE_DIR, "dir")

        val mkCol = mockEngine.requestHistory.single()
        assertEquals("MKCOL", mkCol.method.value)
        assertEquals("https://mock.example.com/webdav/dir/", mkCol.url.toString())
    }

    @Test
    fun testCreate_file_noTrailingSlash() = runTest {
        operation(root.id.toString(), "text/plain", "file.txt")

        val put = mockEngine.requestHistory.single()
        assertEquals("PUT", put.method.value)
        assertEquals("https://mock.example.com/webdav/file.txt", put.url.toString())
    }

}
