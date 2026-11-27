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
class CopyDocumentOperationTest : WebDavOperationTest() {

    @Inject
    lateinit var operation: CopyDocumentOperation

    @BindValue
    @JvmField
    val httpClientBuilder = mockHttpClientBuilder()


    @Test
    fun testCopy_folder_usesTrailingSlashes() = runTest {
        val source = insertDocument(root, "source", true)
        val folder = insertDocument(source, "folder", true)
        val destination = insertDocument(root, "destination", true)

        operation(folder.id.toString(), destination.id.toString())

        val copy = mockEngine.requestHistory.single()
        assertEquals("https://mock.example.com/webdav/source/folder/", copy.url.toString())
        assertEquals("https://mock.example.com/webdav/destination/folder/", copy.headers["Destination"])
    }

    @Test
    fun testCopy_file_noTrailingSlash() = runTest {
        val source = insertDocument(root, "source", true)
        val file = insertDocument(source, "file.txt", false)
        val destination = insertDocument(root, "destination", true)

        operation(file.id.toString(), destination.id.toString())

        val copy = mockEngine.requestHistory.single()
        assertEquals("https://mock.example.com/webdav/source/file.txt", copy.url.toString())
        assertEquals("https://mock.example.com/webdav/destination/file.txt", copy.headers["Destination"])
    }

}
