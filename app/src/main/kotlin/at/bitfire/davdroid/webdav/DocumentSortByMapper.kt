/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.provider.DocumentsContract.Document
import at.bitfire.davdroid.db.WebDavDocument
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
    fun mapContentProviderToSql(orderBy: String): String {
        // Map incoming orderBy to a list of pair of column and direction (true for ASC), like
        // [ Pair("displayName", Boolean), … ]
        val requestedFields = orderBy
            // Split by commas to divide each order column
            .split(',')
            // Trim any leading or trailing spaces
            .map { it.trim() }

        val requestedCriteria = mutableListOf<Pair<String, Boolean>>()
        for (field in requestedFields) {
            val idx = field.indexOfFirst { it == ' ' }
            if (idx == -1)
                // no whitespace, only name → use ASC as default, like in SQL
                requestedCriteria += field to true
            else {
                // whitespace, name and sort order
                val name = field.substring(0, idx)
                val directionStr = field.substring(idx).trim()
                val ascending = directionStr.equals("ASC", true)
                requestedCriteria += name to ascending
            }
        }

        // If displayName doesn't appear in sort order, append it in case that the other criteria generate
        // the same order. For instance, if files are ordered by ascending size and all have 0 bytes, then
        // another order by name is useful.
        if (!requestedCriteria.any { it.first == Document.COLUMN_DISPLAY_NAME })
            requestedCriteria += Document.COLUMN_DISPLAY_NAME to true

        // Generate SQL
        val sqlSortBy = mutableListOf<String>()     // list of valid SQL ORDER BY elements like "displayName ASC"
        for ((requestedColumn, ascending) in requestedCriteria) {
            // Only take columns that are registered in the columns map
            val sqlFieldName = columnsMap[requestedColumn]
            if (sqlFieldName == null) {
                logger.warning("Ignoring unknown column in sortOrder: $requestedColumn")
                continue
            }

            // Finally, convert the column name from document to room, including the sort direction.
            sqlSortBy += "$sqlFieldName ${if (ascending) "ASC" else "DESC"}"
        }
        return sqlSortBy.joinToString(", ")
    }

}