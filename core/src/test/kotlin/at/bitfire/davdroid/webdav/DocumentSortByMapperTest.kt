/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.provider.DocumentsContract.Document
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.util.logging.Logger

class DocumentSortByMapperTest {

    private val mapper = DocumentSortByMapper(Logger.getGlobal())

    @Test
    fun test_MapContentProviderToSql() {
        // Valid column without direction
        assertEquals(
            "displayName ASC",
            mapper.mapContentProviderToSql(Document.COLUMN_DISPLAY_NAME)
        )
        // Valid column with direction
        assertEquals(
            "displayName DESC",
            mapper.mapContentProviderToSql("${Document.COLUMN_DISPLAY_NAME} DESC")
        )
        // Valid column with direction and multiple spaces
        assertEquals(
            "displayName ASC",
            mapper.mapContentProviderToSql("${Document.COLUMN_DISPLAY_NAME}   ASC")
        )
        // Invalid column without direction
        assertEquals(
            "displayName ASC",
            mapper.mapContentProviderToSql("invalid")
        )
        // Invalid column with direction
        assertEquals(
            "displayName ASC",
            mapper.mapContentProviderToSql("invalid ASC")
        )
        // Valid and invalid columns with and without directions
        assertEquals(
            "displayName ASC, mimeType DESC",
            mapper.mapContentProviderToSql(
                "${Document.COLUMN_DISPLAY_NAME}, invalid DESC, ${Document.COLUMN_MIME_TYPE} DESC"
            )
        )
    }

}