/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.WebDavDocumentDao
import at.bitfire.davdroid.db.WebDavMountDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.util.logging.Logger

/**
 * Actual implementation of the [DavDocumentsProvider]. Required because at the time when
 * [DavDocumentsProvider] (which is a content provider) is initialized, Hilt and thus DI is
 * not ready yet. So we implement everything in this class, which is properly late-initialized
 * with Hilt.
 */
class DavDocumentsProviderImpl(
    @ApplicationContext private val context: Context,
    private val documentDao: WebDavDocumentDao,
    private val logger: Logger,
    private val mountDao: WebDavMountDao
) {

    fun queryRoots(projection: Array<out String>?): Cursor {
        logger.fine("WebDAV queryRoots")
        val roots = MatrixCursor(projection ?: arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_SUMMARY
        ))

        runBlocking {
            for (mount in mountDao.getAll()) {
                val rootDocument = documentDao.getOrCreateRoot(mount)
                logger.info("Root ID: $rootDocument")

                roots.newRow().apply {
                    add(Root.COLUMN_ROOT_ID, mount.id)
                    add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                    add(Root.COLUMN_TITLE, context.getString(R.string.webdav_provider_root_title))
                    add(Root.COLUMN_DOCUMENT_ID, rootDocument.id.toString())
                    add(Root.COLUMN_SUMMARY, mount.name)
                    add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)

                    val quotaAvailable = rootDocument.quotaAvailable
                    if (quotaAvailable != null)
                        add(Root.COLUMN_AVAILABLE_BYTES, quotaAvailable)

                    val quotaUsed = rootDocument.quotaUsed
                    if (quotaAvailable != null && quotaUsed != null)
                        add(Root.COLUMN_CAPACITY_BYTES, quotaAvailable + quotaUsed)
                }
            }
        }

        return roots
    }

    fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
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