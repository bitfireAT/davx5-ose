/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.security.NetworkSecurityPolicy
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavMount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class MoveDocumentOperationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var operation: MoveDocumentOperation

    @Inject
    lateinit var logger: Logger

    private lateinit var server: MockWebServer

    private lateinit var mount: WebDavMount
    private lateinit var rootDocument: WebDavDocument
    private lateinit var sourceParent: WebDavDocument
    private lateinit var destinationParent: WebDavDocument
    private lateinit var sourceFile: WebDavDocument

    @Before
    fun setUp() {
        hiltRule.inject()

        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    logger.info("MockWebServer: ${request.method} ${request.path}")
                    return when {
                        request.method.equals("MOVE", ignoreCase = true) ->
                            MockResponse().setResponseCode(204)
                        else ->
                            MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }

        // NetworkSecurityPolicy must allow cleartext traffic (test environment)
        assert(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)

        runBlocking {
            val mountId = db.webDavMountDao().insert(
                WebDavMount(0, "Test Mount", server.url("/webdav/"))
            )
            mount = db.webDavMountDao().getById(mountId)
            rootDocument = db.webDavDocumentDao().getOrCreateRoot(mount)

            val sourceParentId = db.webDavDocumentDao().insert(
                WebDavDocument(0, mount.id, rootDocument.id, "source", true)
            )
            sourceParent = db.webDavDocumentDao().get(sourceParentId)!!

            val destinationParentId = db.webDavDocumentDao().insert(
                WebDavDocument(0, mount.id, rootDocument.id, "destination", true)
            )
            destinationParent = db.webDavDocumentDao().get(destinationParentId)!!

            val sourceFileId = db.webDavDocumentDao().insert(
                WebDavDocument(0, mount.id, sourceParent.id, "file.txt", false)
            )
            sourceFile = db.webDavDocumentDao().get(sourceFileId)!!
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        runBlocking {
            db.webDavMountDao().deleteAsync(mount)
        }
    }


    @Test
    fun testMove_updatesDb() {
        val newId = operation.invoke(
            sourceFile.id.toString(),
            sourceParent.id.toString(),
            destinationParent.id.toString()
        )

        runBlocking {
            // Old entry is gone
            assertNull(db.webDavDocumentDao().get(sourceFile.id))

            // New entry exists at destination
            val movedDoc = db.webDavDocumentDao().getByParentAndName(
                mount.id, destinationParent.id, sourceFile.name
            )
            assertNotNull(movedDoc)
            assertEquals(destinationParent.id, movedDoc!!.parentId)
            assertEquals(sourceFile.name, movedDoc.name)

            // Returned ID matches the new entry
            assertEquals(newId.toLong(), movedDoc.id)
        }
    }


}
