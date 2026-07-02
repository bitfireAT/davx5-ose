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
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.mockk.every
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
class MoveDocumentOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var operation: MoveDocumentOperation

    private val mockEngine = MockEngine { request ->
        when {
            request.method.value.equals("MOVE", ignoreCase = true) -> respond("", HttpStatusCode.NoContent)
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
        every { httpClientBuilder.buildKtor(any(), any()) } answers { HttpClient(mockEngine) }

        mountDao = db.webDavMountDao()
        runBlocking {
            val mountId = mountDao.insert(WebDavMount(0, "Test Mount", Url("https://mock.example.com/webdav/")))
            mount = mountDao.getById(mountId)
        }
    }

    @After
    fun tearDown() {
        runBlocking { mountDao.deleteAsync(mount) }
    }


    @Test
    fun testMove_updatesDb() = runTest {
        val documentDao = db.webDavDocumentDao()
        val root = documentDao.getOrCreateRoot(mount)

        // folder and file to move
        val sourceParentId = documentDao.insert(WebDavDocument(0, mount.id, root.id, "source", true))
        val sourceParent = documentDao.get(sourceParentId)!!
        val sourceFileId = documentDao.insert(WebDavDocument(0, mount.id, sourceParent.id, "file.txt", false))
        val sourceFile = documentDao.get(sourceFileId)!!

        // destination folder
        val destinationParentId = documentDao.insert(WebDavDocument(0, mount.id, root.id, "destination", true))
        val destinationParent = documentDao.get(destinationParentId)!!

        // move
        val newId = operation.invoke(
            sourceFile.id.toString(),
            sourceParent.id.toString(),
            destinationParent.id.toString()
        )

        // verify that moved document is in destination folder
        val movedDoc = documentDao.get(newId.toLong())
        assertEquals(destinationParent.id, movedDoc!!.parentId)
        assertEquals(sourceFile.name, movedDoc.name)
    }

}
