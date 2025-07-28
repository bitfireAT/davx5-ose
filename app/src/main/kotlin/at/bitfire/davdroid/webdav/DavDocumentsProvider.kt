/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.graphics.Point
import android.os.CancellationSignal
import android.provider.DocumentsProvider
import android.util.Log
import at.bitfire.davdroid.webdav.di.WebdavComponentManager
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Provides functionality on WebDav documents.
 *
 * Hilt constructor injection can't be used for content providers because SingletonComponent
 * may not ready yet when the content provider is created. So we use an explicit EntryPoint.
 */
class DavDocumentsProvider: DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DavDocumentsProviderEntryPoint {
        fun copyDocumentOperation(): CopyDocumentOperation
        fun createDocumentOperation(): CreateDocumentOperation
        fun deleteDocumentOperation(): DeleteDocumentOperation
        fun isChildDocumentOperation(): IsChildDocumentOperation
        fun moveDocumentOperation(): MoveDocumentOperation
        fun openDocumentOperation(): OpenDocumentOperation
        fun openDocumentThumbnailOperation(): OpenDocumentThumbnailOperation
        fun queryChildDocumentsOperation(): QueryChildDocumentsOperation
        fun queryDocumentOperation(): QueryDocumentOperation
        fun queryRootsOperation(): QueryRootsOperation
        fun renameDocumentOperation(): RenameDocumentOperation
        fun webdavComponentManager(): WebdavComponentManager
    }

    /**
     * This scope is used to cancel current operations on [shutdown].
     */
    val providerScope = CoroutineScope(SupervisorJob())

    private val entryPoint: DavDocumentsProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication<DavDocumentsProviderEntryPoint>(context!!)
    }


    override fun onCreate(): Boolean {
        Log.v("DavDocumentsProvider", "onCreate()")
        return true
    }

    override fun shutdown() {
        Log.v("DavDocumentsProvider", "onDestroy()")
        providerScope.cancel()
        entryPoint.webdavComponentManager().resetComponent()
    }


    /*** query ***/

    override fun queryRoots(projection: Array<out String>?) =
        entryPoint.queryRootsOperation().invoke(projection)

    override fun queryDocument(documentId: String, projection: Array<out String>?) =
        entryPoint.queryDocumentOperation().invoke(documentId, projection)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        entryPoint.queryChildDocumentsOperation().invoke(providerScope, parentDocumentId, projection, sortOrder)

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        entryPoint.isChildDocumentOperation().invoke(parentDocumentId, documentId)


    /*** copy/create/delete/move/rename ***/

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String) =
        entryPoint.copyDocumentOperation().invoke(sourceDocumentId, targetParentDocumentId)

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? =
        entryPoint.createDocumentOperation().invoke(parentDocumentId, mimeType, displayName)

    override fun deleteDocument(documentId: String) =
        entryPoint.deleteDocumentOperation().invoke(documentId)

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String) =
        entryPoint.moveDocumentOperation().invoke(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)

    override fun renameDocument(documentId: String, displayName: String): String? =
        entryPoint.renameDocumentOperation().invoke(documentId, displayName)


    /*** read/write ***/

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?) =
        entryPoint.openDocumentOperation().invoke(documentId, mode, signal)

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point, signal: CancellationSignal?) =
        entryPoint.openDocumentThumbnailOperation().invoke(documentId, sizeHint, signal)

}