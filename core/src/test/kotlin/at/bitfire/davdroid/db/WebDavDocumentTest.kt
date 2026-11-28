/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.provider.DocumentsContract.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDavDocumentTest {

    private fun rootDirectory() = WebDavDocument(
        id = 1,
        mountId = 1,
        parentId = null,
        name = "",
        isDirectory = true
    )

    @Test
    fun `toBundle() uses displayName as display name`() {
        val doc = WebDavDocument(mountId = 1, parentId = null, name = "g5d5q4edj00n", displayName = "Upload")

        val bundle = doc.toBundle(null)

        assertEquals("Upload", bundle.getString(Document.COLUMN_DISPLAY_NAME))
    }

    @Test
    fun `toBundle() without displayName uses name as display name`() {
        val doc = WebDavDocument(mountId = 1, parentId = null, name = "file.txt")

        val bundle = doc.toBundle(null)

        assertEquals("file.txt", bundle.getString(Document.COLUMN_DISPLAY_NAME))
    }

    @Test
    fun `toBundle() doesn't set summary`() {
        val doc = WebDavDocument(mountId = 1, parentId = null, name = "g5d5q4edj00n", displayName = "Upload")

        val bundle = doc.toBundle(null)

        assertNull(bundle.getString(Document.COLUMN_SUMMARY))
    }

    @Test
    fun `toBundle() for root document offers copy and create, but not delete, move and rename`() {
        val root = rootDirectory()

        val flags = root.toBundle(null).getInt(Document.COLUMN_FLAGS)

        assertEquals(Document.FLAG_SUPPORTS_COPY or Document.FLAG_DIR_SUPPORTS_CREATE, flags)
    }

    @Test
    fun `toBundle() for non-root document offers delete, move and rename`() {
        val parent = rootDirectory()
        val doc = WebDavDocument(
            id = 2,
            mountId = parent.mountId,
            parentId = parent.id,
            name = "file.txt",
            isDirectory = false
        )

        val flags = doc.toBundle(parent).getInt(Document.COLUMN_FLAGS)

        assertEquals(
            Document.FLAG_SUPPORTS_COPY or
                    Document.FLAG_SUPPORTS_WRITE or
                    Document.FLAG_SUPPORTS_DELETE or
                    Document.FLAG_SUPPORTS_MOVE or
                    Document.FLAG_SUPPORTS_RENAME,
            flags
        )
    }

    @Test
    fun `toBundle() with write-content but without unbind on parent supports rename, but not delete`() {
        val parent = WebDavDocument(id = 1, mountId = 1, parentId = null, name = "", isDirectory = true, mayUnbind = false)
        val doc = WebDavDocument(mountId = 1, parentId = 1, name = "file.txt", mayWriteContent = true)

        val flags = doc.toBundle(parent).getInt(Document.COLUMN_FLAGS)

        assertEquals(Document.FLAG_SUPPORTS_RENAME, flags and Document.FLAG_SUPPORTS_RENAME)
        assertEquals(0, flags and Document.FLAG_SUPPORTS_DELETE)
    }

    @Test
    fun `toBundle() without write-content and without unbind on parent doesn't support rename`() {
        val parent = WebDavDocument(id = 1, mountId = 1, parentId = null, name = "", isDirectory = true, mayUnbind = false)
        val doc = WebDavDocument(mountId = 1, parentId = 1, name = "file.txt", mayWriteContent = false)

        val flags = doc.toBundle(parent).getInt(Document.COLUMN_FLAGS)

        assertEquals(0, flags and Document.FLAG_SUPPORTS_RENAME)
    }

}
