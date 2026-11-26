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
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.mockk.coEvery
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CopyDocumentOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var operation: CopyDocumentOperation

    private val requests = mutableListOf<HttpRequestData>()

    private val mockEngine = MockEngine { request ->
        requests += request
        when {
            request.method.value.equals("COPY", ignoreCase = true) -> respond("", HttpStatusCode.Created)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    @BindValue
    @JvmField
    val httpClientBuilder: DavHttpClientBuilder = mockk()

    private lateinit var mountDao: WebDavMountDao
    private lateinit var mount: WebDavMount

    @Before
    fun setUp() {
        hiltRule.inject()
        coEvery { httpClientBuilder.build(any(), any()) } answers { HttpClient(mockEngine) }

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
    fun testCopy_folder_usesTrailingSlashes() = runTest {
        val documentDao = db.webDavDocumentDao()
        val root = documentDao.getOrCreateRoot(mount)
        val sourceParentId = documentDao.insert(WebDavDocument(0, mount.id, root.id, "source", true))
        val folderId = documentDao.insert(WebDavDocument(0, mount.id, sourceParentId, "folder", true))
        val destinationParentId = documentDao.insert(WebDavDocument(0, mount.id, root.id, "destination", true))

        operation(folderId.toString(), destinationParentId.toString())

        val copy = requests.single()
        assertEquals("https://mock.example.com/webdav/source/folder/", copy.url.toString())
        assertEquals("https://mock.example.com/webdav/destination/folder/", copy.headers["Destination"])
    }

    @Test
    fun testCopy_file_noTrailingSlash() = runTest {
        val documentDao = db.webDavDocumentDao()
        val root = documentDao.getOrCreateRoot(mount)
        val sourceParentId = documentDao.insert(WebDavDocument(0, mount.id, root.id, "source", true))
        val fileId = documentDao.insert(WebDavDocument(0, mount.id, sourceParentId, "file.txt", false))
        val destinationParentId = documentDao.insert(WebDavDocument(0, mount.id, root.id, "destination", true))

        operation(fileId.toString(), destinationParentId.toString())

        val copy = requests.single()
        assertEquals("https://mock.example.com/webdav/source/file.txt", copy.url.toString())
        assertEquals("https://mock.example.com/webdav/destination/file.txt", copy.headers["Destination"])
    }

}
