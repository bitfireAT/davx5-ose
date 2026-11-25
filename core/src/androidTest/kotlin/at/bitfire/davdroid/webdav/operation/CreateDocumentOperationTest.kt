/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.davdroid.db.AppDatabase
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.mockk.coEvery
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CreateDocumentOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var operation: CreateDocumentOperation

    private var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond("", HttpStatusCode.NotFound)
    }

    @BindValue
    @JvmField
    val httpClientBuilder: DavHttpClientBuilder = mockk()

    private lateinit var mountDao: WebDavMountDao
    private lateinit var mount: WebDavMount

    @Before
    fun setUp() {
        hiltRule.inject()
        coEvery { httpClientBuilder.build(any(), any()) } answers {
            HttpClient(MockEngine { request -> handler(request) })
        }

        mountDao = db.webDavMountDao()
        runBlocking {
            val mountId = mountDao.insert(WebDavMount(0, "Test Mount", Url("https://mock.example.com/webdav/")))
            mount = mountDao.getById(mountId)
        }
    }

    @After
    fun tearDown() {
        runBlocking { mountDao.delete(mount) }
    }


    @Test
    fun testCreate_nameTaken_usesNextName() = runTest {
        handler = { request ->
            if (request.url.encodedPath.endsWith("/file.txt"))
                respond("", HttpStatusCode.PreconditionFailed)
            else
                respond("", HttpStatusCode.Created)
        }
        val documentDao = db.webDavDocumentDao()
        val root = documentDao.getOrCreateRoot(mount)

        val result = operation(root.id.toString(), "text/plain", "file.txt")

        assertEquals("file_1.txt", documentDao.get(result!!.toLong())!!.name)
    }

    @Test
    fun testCreate_allNamesTaken_returnsNull() = runTest {
        handler = { respond("", HttpStatusCode.PreconditionFailed) }
        val root = db.webDavDocumentDao().getOrCreateRoot(mount)

        val result = operation(root.id.toString(), "text/plain", "file.txt")

        assertNull(result)
    }

    @Test
    fun testCreate_serverError_throws() = runTest {
        handler = { respond("", HttpStatusCode.BadGateway) }
        val root = db.webDavDocumentDao().getOrCreateRoot(mount)

        assertThrows(HttpException::class.java) {
            runBlocking { operation(root.id.toString(), "text/plain", "file.txt") }
        }
    }

}
