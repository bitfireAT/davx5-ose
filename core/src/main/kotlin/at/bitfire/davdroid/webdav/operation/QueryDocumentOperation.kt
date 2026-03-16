/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.database.Cursor
import android.provider.DocumentsContract.Document
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.webdav.DocumentsCursor
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class QueryDocumentOperation @Inject constructor(
    db: AppDatabase,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()
    private val mountDao = db.webDavMountDao()

    operator fun invoke(documentId: String, projection: Array<out String>?): Cursor {
        logger.fine("WebDAV queryDocument $documentId ${projection?.joinToString("+")}")

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val parent = doc.parentId?.let { parentId ->
            documentDao.get(parentId)
        }

        return DocumentsCursor(projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_ICON,
            Document.COLUMN_SUMMARY
        )).apply {
            val bundle = doc.toBundle(parent)
            logger.fine("queryDocument($documentId) = $bundle")

            // override display names of root documents
            if (parent == null) {
                val mount = runBlocking { mountDao.getById(doc.mountId) }
                bundle.putString(Document.COLUMN_DISPLAY_NAME, mount.name)
            }

            addRow(bundle)
        }
    }

}