/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

}
