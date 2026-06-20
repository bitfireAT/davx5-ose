/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
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
import javax.inject.Inject

@HiltAndroidTest
class MoveDocumentOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var operation: MoveDocumentOperation

    private lateinit var server: MockWebServer

    private val documentDao = db.webDavDocumentDao()
    private val mountDao = db.webDavMountDao()
    private lateinit var mount: WebDavMount

    @Before
    fun setUp() {
        hiltRule.inject()

        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when {
                        request.method.equals("MOVE", ignoreCase = true) -> MockResponse().setResponseCode(204)
                        else -> MockResponse().setResponseCode(404)
                    }
            }
            start()
        }

        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)

        // set up WebDAV mount
        runBlocking {
            val mountId = mountDao.insert(WebDavMount(0, "Test Mount", server.url("/webdav/")))
            mount = mountDao.getById(mountId)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        runBlocking { mountDao.deleteAsync(mount) }
    }


    @Test
    fun testMove_updatesDb() = runTest {
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
