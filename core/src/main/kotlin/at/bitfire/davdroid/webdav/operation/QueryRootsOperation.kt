/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.DocumentsContract.Root
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger
import javax.inject.Inject

class QueryRootsOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()
    private val mountDao = db.webDavMountDao()

    operator fun invoke(projection: Array<out String>?): Cursor {
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

}