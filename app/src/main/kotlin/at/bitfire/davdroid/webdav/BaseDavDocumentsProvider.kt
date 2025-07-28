/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.graphics.Point
import android.os.CancellationSignal
import android.provider.DocumentsProvider
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
 * may not ready yet when the content provider is created. So we move the actual implementation
 * to [DavDocumentsProvider] which uses proper DI.
 */
class BaseDavDocumentsProvider(): DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DavDocumentsProviderEntryPoint {
        fun impl(): DavDocumentsProvider
    }

    /**
     * This scope is used to cancel current operations on [shutdown].
     */
    val documentProviderScope = CoroutineScope(SupervisorJob())

    private lateinit var impl: DavDocumentsProvider


    override fun onCreate(): Boolean {
        val entryPoint = EntryPointAccessors.fromApplication<DavDocumentsProviderEntryPoint>(context!!)
        impl = entryPoint.impl()
        return true
    }

    override fun shutdown() {
        documentProviderScope.cancel()
    }


    /*** query ***/

    override fun queryRoots(projection: Array<out String>?) =
        impl.queryRoots(projection)

    override fun queryDocument(documentId: String, projection: Array<out String>?) =
        impl.queryDocument(documentId, projection)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        impl.queryChildDocuments(parentDocumentId, projection, sortOrder)

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        impl.isChildDocument(parentDocumentId, documentId)


    /*** copy/create/delete/move/rename ***/

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String) =
        impl.copyDocument(sourceDocumentId, targetParentDocumentId)

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? =
        impl.createDocument(parentDocumentId, mimeType, displayName)

    override fun deleteDocument(documentId: String) =
        impl.deleteDocument(documentId)

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String) =
        impl.moveDocument(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)

    override fun renameDocument(documentId: String, displayName: String): String? =
        impl.renameDocument(documentId, displayName)


    /*** read/write ***/

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?) =
        impl.openDocument(documentId, mode, signal)

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point, signal: CancellationSignal?) =
        impl.openDocumentThumbnail(documentId, sizeHint, signal)

}