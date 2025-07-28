/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.buildChildDocumentsUri
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocumentDao
import at.bitfire.davdroid.webdav.DocumentSortByMapper
import at.bitfire.davdroid.webdav.DocumentsCursor
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import javax.inject.Inject

class QueryChildDocumentsOperation @Inject constructor(
    private val actor: DavDocumentsActor,
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val documentSortByMapper: Lazy<DocumentSortByMapper>,
    private val logger: Logger
) {

    private val authority = context.getString(R.string.webdav_authority)
    private val documentDao = db.webDavDocumentDao()

    @Synchronized
    operator fun invoke(externalScope: CoroutineScope, parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) {
        synchronized(QueryChildDocumentsOperation::class.java) {
            queryChildDocuments(externalScope, parentDocumentId, projection, sortOrder)
        }
    }

    private fun queryChildDocuments(externalScope: CoroutineScope, parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) {
        logger.fine("WebDAV queryChildDocuments $parentDocumentId $projection $sortOrder")
        val parentId = parentDocumentId.toLong()
        val parent = documentDao.get(parentId) ?: throw FileNotFoundException()

        val columns = projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )

        // Register watcher
        val result = DocumentsCursor(columns)
        val notificationUri = buildChildDocumentsUri(authority, parentDocumentId)
        result.setNotificationUri(context.contentResolver, notificationUri)

        // Dispatch worker querying for the children and keep track of it
        val running = runningQueryChildren.getOrPut(parentId) {
            externalScope.launch {
                actor.queryChildren(parent)
                // Once the query is done, set query as finished (not running)
                runningQueryChildren[parentId] = false
                // .. and notify - effectively calling this method again
                context.contentResolver.notifyChange(notificationUri, null)
            }
            true
        }

        if (running)        // worker still running
            result.loading = true
        else                // remove worker from list if done
            runningQueryChildren.remove(parentId)

        // Prepare SORT BY clause
        val mapper = documentSortByMapper.get()
        val sqlSortBy = if (sortOrder != null)
            mapper.mapContentProviderToSql(sortOrder)
        else
            WebDavDocumentDao.DEFAULT_ORDER

        // Regardless of whether the worker is done, return the children we already have
        val children = documentDao.getChildren(parentId, sqlSortBy)
        for (child in children) {
            val bundle = child.toBundle(parent)
            result.addRow(bundle)
        }

        return result
    }


    companion object {

        /** List of currently active [queryChildDocuments] runners.
         *
         *  Key: document ID (directory) for which children are listed.
         *  Value: whether the runner is still running (*true*) or has already finished (*false*).
         */
        private val runningQueryChildren = ConcurrentHashMap<Long, Boolean>()

    }

}