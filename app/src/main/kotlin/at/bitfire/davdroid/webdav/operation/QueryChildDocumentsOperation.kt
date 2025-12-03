/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.buildChildDocumentsUri
import at.bitfire.dav4jvm.okhttp.DavCollection
import at.bitfire.dav4jvm.okhttp.Response
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetContentLength
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.GetLastModified
import at.bitfire.dav4jvm.property.webdav.QuotaAvailableBytes
import at.bitfire.dav4jvm.property.webdav.QuotaUsedBytes
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.db.WebDavDocumentDao
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentSortByMapper
import at.bitfire.davdroid.webdav.DocumentsCursor
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class QueryChildDocumentsOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val documentSortByMapper: Lazy<DocumentSortByMapper>,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val authority = context.getString(R.string.webdav_authority)
    private val documentDao = db.webDavDocumentDao()

    private val backgroundScope = CoroutineScope(SupervisorJob())

    operator fun invoke(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        synchronized(QueryChildDocumentsOperation::class.java) {
            queryChildDocuments(parentDocumentId, projection, sortOrder)
        }

    private fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): DocumentsCursor {
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
            backgroundScope.launch {
                queryChildren(parent)
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

    /**
     * Finds children of given parent [WebDavDocument]. After querying, it
     * updates existing children, adds new ones or removes deleted ones.
     *
     * There must never be more than one running instance per [parent]!
     *
     * @param parent    folder to search for children
     */
    internal suspend fun queryChildren(parent: WebDavDocument) {
        val oldChildren = documentDao.getChildren(parent.id).associateBy { it.name }.toMutableMap() // "name" of file/folder must be unique
        val newChildrenList = hashMapOf<String, WebDavDocument>()

        val parentUrl = parent.toHttpUrl(db)
        val client = httpClientBuilder.build(parent.mountId)
        val folder = DavCollection(client, parentUrl)

        try {
            runInterruptible(ioDispatcher) {
                folder.propfind(1, *DAV_FILE_FIELDS) { response, relation ->
                    logger.fine("$relation $response")

                    val resource: WebDavDocument =
                        when (relation) {
                            Response.HrefRelation.SELF ->       // it's about the parent
                                parent

                            Response.HrefRelation.MEMBER ->     // it's about a member
                                WebDavDocument(mountId = parent.mountId, parentId = parent.id, name = response.hrefName())

                            else -> {
                                // we didn't request this; log a warning and ignore it
                                logger.warning("Ignoring unexpected $response $relation in $parentUrl")
                                return@propfind
                            }
                        }

                    val updatedResource = resource.copy(
                        isDirectory = response[ResourceType::class.java]?.types?.contains(WebDAV.Collection)
                            ?: resource.isDirectory,
                        displayName = response[DisplayName::class.java]?.displayName,
                        mimeType = response[GetContentType::class.java]?.type?.toMediaTypeOrNull(),
                        eTag = response[GetETag::class.java]?.takeIf { !it.weak }?.eTag,
                        lastModified = response[GetLastModified::class.java]?.lastModified?.toEpochMilli(),
                        size = response[GetContentLength::class.java]?.contentLength,
                        mayBind = response[CurrentUserPrivilegeSet::class.java]?.mayBind,
                        mayUnbind = response[CurrentUserPrivilegeSet::class.java]?.mayUnbind,
                        mayWriteContent = response[CurrentUserPrivilegeSet::class.java]?.mayWriteContent,
                        quotaAvailable = response[QuotaAvailableBytes::class.java]?.quotaAvailableBytes,
                        quotaUsed = response[QuotaUsedBytes::class.java]?.quotaUsedBytes,
                    )

                    if (resource == parent)
                        documentDao.update(updatedResource)
                    else {
                        documentDao.insertOrUpdate(updatedResource)
                        newChildrenList[resource.name] = updatedResource
                    }

                    // remove resource from known child nodes, because not found on server
                    oldChildren.remove(resource.name)
                }
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't query children", e)
        }

        // Delete child nodes which were not rediscovered (deleted serverside)
        for ((_, oldChild) in oldChildren)
            documentDao.delete(oldChild)
    }


    companion object {

        val DAV_FILE_FIELDS = arrayOf(
            WebDAV.ResourceType,
            WebDAV.CurrentUserPrivilegeSet,
            WebDAV.DisplayName,
            WebDAV.GetETag,
            WebDAV.GetContentType,
            WebDAV.GetContentLength,
            WebDAV.GetLastModified,
            WebDAV.QuotaAvailableBytes,
            WebDAV.QuotaUsedBytes,
        )

        /** List of currently active [queryChildDocuments] runners.
         *
         *  Key: document ID (directory) for which children are listed.
         *  Value: whether the runner is still running (*true*) or has already finished (*false*).
         */
        private val runningQueryChildren = ConcurrentHashMap<Long, Boolean>()

    }

}