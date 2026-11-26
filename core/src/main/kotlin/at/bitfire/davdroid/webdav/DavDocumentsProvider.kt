/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsProvider
import androidx.core.app.TaskStackBuilder
import at.bitfire.dav4jvm.ktor.exception.UnauthorizedException
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
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
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.util.concurrent.CancellationException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Provides functionality on WebDav documents.
 *
 * Hilt constructor injection can't be used for content providers because SingletonComponent
 * may not ready yet when the content provider is created. So we use an explicit EntryPoint.
 *
 * Note: A DocumentsProvider is a ContentProvider and thus has no well-defined lifecycle. It
 * is created by Android when it's first accessed and then stays in memory until the process
 * is killed.
 *
 * As a ContentProvider, this class is responsible for bridging its blocking API to the suspending operations
 * (see [runOperation]).
 */
class DavDocumentsProvider : DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DavDocumentsProviderEntryPoint {
        fun copyDocumentOperation(): CopyDocumentOperation
        fun createDocumentOperation(): CreateDocumentOperation
        fun deleteDocumentOperation(): DeleteDocumentOperation
        fun isChildDocumentOperation(): IsChildDocumentOperation
        fun logger(): Logger
        fun moveDocumentOperation(): MoveDocumentOperation
        fun openDocumentOperation(): OpenDocumentOperation
        fun openDocumentThumbnailOperation(): OpenDocumentThumbnailOperation
        fun queryChildDocumentsOperation(): QueryChildDocumentsOperation
        fun queryDocumentOperation(): QueryDocumentOperation
        fun queryRootsOperation(): QueryRootsOperation
        fun renameDocumentOperation(): RenameDocumentOperation
    }

    private val entryPoint: DavDocumentsProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication<DavDocumentsProviderEntryPoint>(context!!)
    }


    override fun onCreate() = true

    /* Note: shutdown() is NOT called automatically by Android; a content provider lives until
    the process is killed. */

    /**
     * Runs an operation blocking and converts thrown exceptions with [toDocumentsProviderException] so that
     * they can be passed over IPC. Converted exceptions are logged because their stack trace doesn't reach the caller.
     *
     * See also the "throws" declarations for the methods in [DocumentsProvider].
     *
     * @return result of [block]
     */
    private fun <T> runOperation(block: suspend () -> T): T =
        runBlocking {
            try {
                block()
            } catch (e: Exception) {
                // IPC can only send some exception types back to the calling app
                val exForIpc = e.toDocumentsProviderException(context!!)
                if (exForIpc !== e)
                    entryPoint.logger().log(Level.WARNING, "WebDAV operation failed", e)
                throw exForIpc
            }
        }


    /*** query ***/

    override fun queryRoots(projection: Array<out String>?) = runOperation {
        entryPoint.queryRootsOperation().invoke(projection)
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?) = runOperation {
        entryPoint.queryDocumentOperation().invoke(documentId, projection)
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        runOperation {
            entryPoint.queryChildDocumentsOperation().invoke(parentDocumentId, projection, sortOrder)
        }

    override fun isChildDocument(parentDocumentId: String, documentId: String) = runOperation {
        entryPoint.isChildDocumentOperation().invoke(parentDocumentId, documentId)
    }


    /*** copy/create/delete/move/rename ***/

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String) = runOperation {
        entryPoint.copyDocumentOperation().invoke(sourceDocumentId, targetParentDocumentId)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String? = runOperation {
        entryPoint.createDocumentOperation().invoke(parentDocumentId, mimeType, displayName)
    }

    override fun deleteDocument(documentId: String) = runOperation {
        entryPoint.deleteDocumentOperation().invoke(documentId)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ) = runOperation {
        entryPoint.moveDocumentOperation().invoke(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String? = runOperation {
        entryPoint.renameDocumentOperation().invoke(documentId, displayName)
    }


    /*** read/write ***/

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?) = runOperation {
        entryPoint.openDocumentOperation().invoke(documentId, mode, signal)
    }

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point, signal: CancellationSignal?) = runOperation {
        entryPoint.openDocumentThumbnailOperation().invoke(documentId, sizeHint, signal)
    }

}

/**
 * Converts an exception into one that IPC can send back to the calling app. Only the types
 * handled by [android.database.DatabaseUtils.writeExceptionToParcel] (content providers, e.g. [FileNotFoundException])
 * and [android.os.Parcel.writeException] can be sent.
 *
 * Converting exceptions into a [FileNotFoundException] seems to be common practice, for instance in the
 * "Create a custom document provider" guide (`openDocument()`) and the `MyCloudProvider` of android/storage-samples.
 *
 * Note that for queries and thumbnails, [android.provider.DocumentsProvider] itself catches [FileNotFoundException]
 * and returns `null` to the caller.
 *
 * @return exception to throw from a `DocumentsProvider` method
 */
internal fun Exception.toDocumentsProviderException(context: Context): Exception =
    when {
        // must come before IllegalStateException, which it extends
        this is CancellationException ->
            OperationCanceledException(message)

        this is UnauthorizedException && Build.VERSION.SDK_INT >= 26 ->
            AuthenticationRequiredException(
                this,
                TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(Intent(context, WebdavMountsActivity::class.java))
                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

        this is FileNotFoundException ||
        this is OperationCanceledException ||
        this is SecurityException ||
        this is IllegalArgumentException ||
        this is IllegalStateException ||
        this is UnsupportedOperationException ->
            this

        else ->
            FileNotFoundException(message ?: javaClass.simpleName).apply {
                initCause(this@toDocumentsProviderException)
            }
    }

