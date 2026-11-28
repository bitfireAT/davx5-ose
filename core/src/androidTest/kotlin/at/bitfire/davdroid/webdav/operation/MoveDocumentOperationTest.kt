/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class MoveDocumentOperationTest : WebDavOperationTest() {

    @Inject
    lateinit var operation: MoveDocumentOperation

    @BindValue
    @JvmField
    val httpClientBuilder = mockHttpClientBuilder()


    @Test
    fun testMove_updatesDb() = runTest {
        val source = insertDocument(root, "source", true)
        val file = insertDocument(source, "file.txt", false)
        val destination = insertDocument(root, "destination", true)

        val newId = operation(file.id.toString(), source.id.toString(), destination.id.toString())

        val movedDoc = documentDao.get(newId.toLong())!!
        assertEquals(destination.id, movedDoc.parentId)
        assertEquals("file.txt", movedDoc.name)
    }

    @Test
    fun testMove_folder_usesTrailingSlashes() = runTest {
        val source = insertDocument(root, "source", true)
        val folder = insertDocument(source, "folder", true)
        val destination = insertDocument(root, "destination", true)

        operation(folder.id.toString(), source.id.toString(), destination.id.toString())

        val move = mockEngine.requestHistory.single()
        assertEquals("https://mock.example.com/webdav/source/folder/", move.url.toString())
        assertEquals("https://mock.example.com/webdav/destination/folder/", move.headers["Destination"])
    }

    @Test
    fun testMove_file_noTrailingSlash() = runTest {
        val source = insertDocument(root, "source", true)
        val file = insertDocument(source, "file.txt", false)
        val destination = insertDocument(root, "destination", true)

        operation(file.id.toString(), source.id.toString(), destination.id.toString())

        val move = mockEngine.requestHistory.single()
        assertEquals("https://mock.example.com/webdav/source/file.txt", move.url.toString())
        assertEquals("https://mock.example.com/webdav/destination/file.txt", move.headers["Destination"])
    }

}
