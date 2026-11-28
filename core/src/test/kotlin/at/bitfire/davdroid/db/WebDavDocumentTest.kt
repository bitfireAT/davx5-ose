/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.provider.DocumentsContract.Document
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDavDocumentTest {

    private fun rootDirectory() = WebDavDocument(
        id = 1,
        mountId = 1,
        parentId = null,
        name = "root",
        isDirectory = true
    )

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
            mountId = 1,
            parentId = 1,
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

}
