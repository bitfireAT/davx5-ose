/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.provider.DocumentsContract.Document
import junit.framework.TestCase.assertEquals
import org.junit.Test

class DavDocumentsProviderTest {
    @Test
    fun `test processOrderByQuery`() {
        // Valid column without direction
        assertEquals(
            "displayName",
            DavDocumentsProvider.processOrderByQuery(Document.COLUMN_DISPLAY_NAME),
        )
        // Valid column with direction
        assertEquals(
            "displayName ASC",
            DavDocumentsProvider.processOrderByQuery("${Document.COLUMN_DISPLAY_NAME} ASC"),
        )
        // Valid column with direction and multiple spaces
        assertEquals(
            "displayName ASC",
            DavDocumentsProvider.processOrderByQuery("${Document.COLUMN_DISPLAY_NAME}   ASC"),
        )
        // Invalid column without direction
        assertEquals(
            null,
            DavDocumentsProvider.processOrderByQuery("invalid"),
        )
        // Invalid column with direction
        assertEquals(
            null,
            DavDocumentsProvider.processOrderByQuery("invalid ASC"),
        )
        // Valid and invalid columns with and without directions
        assertEquals(
            "displayName, mimeType ASC",
            DavDocumentsProvider.processOrderByQuery(
                "${Document.COLUMN_DISPLAY_NAME}, invalid DESC, ${Document.COLUMN_MIME_TYPE} ASC"
            ),
        )
    }
}
