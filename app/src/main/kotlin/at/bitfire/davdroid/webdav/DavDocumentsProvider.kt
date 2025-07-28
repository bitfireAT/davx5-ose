/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.buildRootsUri
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetContentLength
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.GetLastModified
import at.bitfire.dav4jvm.property.webdav.QuotaAvailableBytes
import at.bitfire.dav4jvm.property.webdav.QuotaUsedBytes
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.R
import at.bitfire.davdroid.webdav.operation.CopyDocumentOperation
import at.bitfire.davdroid.webdav.operation.CreateDocumentOperation
import at.bitfire.davdroid.webdav.operation.DeleteDocumentOperation
import at.bitfire.davdroid.webdav.operation.IsChildDocumentOperation
import at.bitfire.davdroid.webdav.operation.MoveDocumentOperation
import at.bitfire.davdroid.webdav.operation.OpenDocumentOperation
import at.bitfire.davdroid.webdav.operation.OpenDocumentThumbnailOperation
import at.bitfire.davdroid.webdav.operation.QueryChildDocumentsOperation
import at.bitfire.davdroid.webdav.operation.QueryDocumentOperation
import at.bitfire.davdroid.webdav.operation.QueryRootsOperation
import at.bitfire.davdroid.webdav.operation.RenameDocumentOperation
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider

/**
 * Actual implementation of the [DavDocumentsProviderWrapper]. Required because at the time when
 * [DavDocumentsProviderWrapper] (which is a content provider) is initialized, Hilt and thus DI is
 * not ready yet. So we implement everything in this class, which is properly late-initialized
 * with Hilt.
 */
class DavDocumentsProvider @AssistedInject constructor(
    @Assisted private val externalScope: CoroutineScope,
    private val copyDocumentOperation: Provider<CopyDocumentOperation>,
    private val createDocumentOperation: Provider<CreateDocumentOperation>,
    private val deleteDocumentOperation: Provider<DeleteDocumentOperation>,
    private val isChildDocumentOperation: Provider<IsChildDocumentOperation>,
    private val moveDocumentOperation: Provider<MoveDocumentOperation>,
    private val openDocumentOperation: Provider<OpenDocumentOperation>,
    private val openDocumentThumbnailOperation: Provider<OpenDocumentThumbnailOperation>,
    private val queryChildDocumentsOperation: Provider<QueryChildDocumentsOperation>,
    private val queryDocumentOperation: Provider<QueryDocumentOperation>,
    private val queryRootsOperation: Provider<QueryRootsOperation>,
    private val renameDocumentOperation: Provider<RenameDocumentOperation>
) {
    
    @AssistedFactory
    interface Factory {
        fun create(externalScope: CoroutineScope): DavDocumentsProvider
    }

    fun queryRoots(projection: Array<out String>?) =
        queryRootsOperation.get().invoke(projection)

    fun queryDocument(documentId: String, projection: Array<out String>?) =
        queryDocumentOperation.get().invoke(documentId, projection)

    /**
     * Gets old or new children of given parent.
     *
     * Dispatches a worker querying the server for new children of given parent, and instantly
     * returns old children (or nothing, on initial call).
     * Once the worker finishes its query, it notifies the [android.content.ContentResolver] about
     * change, which calls this method again. The worker being done
     */
    fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        queryChildDocumentsOperation.get().invoke(externalScope, parentDocumentId, projection, sortOrder)

    fun isChildDocument(parentDocumentId: String, documentId: String) =
        isChildDocumentOperation.get().invoke(parentDocumentId, documentId)


    /*** copy/create/delete/move/rename ***/

    fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String) =
        copyDocumentOperation.get().invoke(sourceDocumentId, targetParentDocumentId)

    fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? =
        createDocumentOperation.get().invoke(parentDocumentId, mimeType, displayName)

    fun deleteDocument(documentId: String) =
        deleteDocumentOperation.get().invoke(documentId)

    fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String) =
        moveDocumentOperation.get().invoke(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)

    fun renameDocument(documentId: String, displayName: String): String? =
        renameDocumentOperation.get().invoke(documentId, displayName)


    /*** read/write ***/

    fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        openDocumentOperation.get().invoke(documentId, mode, signal)

    fun openDocumentThumbnail(documentId: String, sizeHint: Point, signal: CancellationSignal?) =
        openDocumentThumbnailOperation.get().invoke(documentId, sizeHint, signal)


    companion object {
        val DAV_FILE_FIELDS = arrayOf(
            ResourceType.NAME,
            CurrentUserPrivilegeSet.NAME,
            DisplayName.NAME,
            GetETag.NAME,
            GetContentType.NAME,
            GetContentLength.NAME,
            GetLastModified.NAME,
            QuotaAvailableBytes.NAME,
            QuotaUsedBytes.NAME,
        )

        const val MAX_NAME_ATTEMPTS = 5
        const val THUMBNAIL_TIMEOUT_MS = 15000L

        fun notifyMountsChanged(context: Context) {
            context.contentResolver.notifyChange(buildRootsUri(context.getString(R.string.webdav_authority)), null)
        }

    }

}