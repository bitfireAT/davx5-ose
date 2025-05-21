/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.provider.DocumentsContract.Document
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.util.trimToNull
import java.util.logging.Logger
import javax.inject.Inject

class DocumentSortByMapper @Inject constructor(
    private val logger: Logger
) {

    /**
     * Contains a map that maps the column names from the documents provider to [WebDavDocument].
     */
    private val columnsMap = mapOf(
        Document.COLUMN_DOCUMENT_ID to "id",
        Document.COLUMN_DISPLAY_NAME to "displayName",
        Document.COLUMN_MIME_TYPE to "mimeType",
        Document.COLUMN_SIZE to "size",
        Document.COLUMN_LAST_MODIFIED to "lastModified"
    )

    /**
     * Maps an incoming `orderBy` column from a [android.content.ContentProvider] query to
     * a validated SQL ORDER BY-clause for [WebDavDocument]s.
     *
     * @param orderBy   orderBy of content provider documents
     *
     * @return value of the ORDER BY-clause, like "name ASC"
     */
    fun mapContentProviderToSql(orderBy: String): String? {
        val requestedColumns = orderBy
            // Split by commas to divide each order column
            .split(',')
            // Trim any leading or trailing spaces
            .map { it.trim() }
            // Get the column name, and the ordering direction.
            // Note that the latter one is optional, so set to null if there isn't any space. For example:
            // `displayName` should return `"displayName" to null`, but
            // `displayName ASC` should return `"displayName" to "ASC"`.
            // Also note that we trim it just in case there are multiple spaces between the column and direction.
            .map { pair ->
                pair.substringBefore(' ') to pair.substringAfter(' ').trim().takeIf { pair.contains(' ') }
            }

        val sqlElements = mutableListOf<String>()
        for ((docCol, dir) in requestedColumns) {
            // Remove all columns not registered in the columns map
            if (!columnsMap.containsKey(docCol)) {
                logger.warning("Queried an order by of an unknown column: $docCol")
                continue
            }

            // Finally, convert the column name from document to room, including the sort direction in
            // case it's included.
            val roomCol = columnsMap.getValue(docCol)
            sqlElements += dir?.let { "$roomCol $dir" } ?: roomCol
        }
        return sqlElements.joinToString(", ")
            // Return null if the request is empty
            .trimToNull()
    }


    companion object {

        /**
         * Default ORDER BY value to use when content provider doesn't specify a sort order.
         */
        const val DEFAULT_ORDER = "isDirectory DESC, name ASC"

    }

}