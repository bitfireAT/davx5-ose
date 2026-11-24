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

}
