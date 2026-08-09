/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.ktor.exception.ServiceUnavailableException
import at.bitfire.dav4jvm.ktor.exception.UnauthorizedException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.util.causedBy
import at.bitfire.synctools.storage.LocalStorageException
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import java.io.IOException
import java.security.cert.CertificateException
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.SSLHandshakeException

/**
 * Decides what to do about an exception that occurred during synchronization of one collection
 * (update [SyncResult], log, notify the user), so that [SyncManager] only has to orchestrate the
 * sync and doesn't need to know about error classification/presentation itself.
 */
class SyncExceptionHandler @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted private val dataType: SyncDataType,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    syncNotificationManagerFactory: SyncNotificationManager.Factory
) {

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, dataType: SyncDataType): SyncExceptionHandler
    }

    private val syncNotificationManager = syncNotificationManagerFactory.create(accountId)


    /**
     * Dismisses a previously shown sync error notification for the given local collection.
     */
    fun dismissPreviousErrors(collectionId: Long) =
        syncNotificationManager.dismissCollectionError(collectionId)


    /** Outcome of [SyncExceptionHandler.handleException], returned to [SyncManager]. */
    sealed interface SyncErrorResult {
        data object HardError : SyncErrorResult
        data class SoftError(val delayUntil: Instant? = null) : SyncErrorResult
        data object NoError : SyncErrorResult
    }

    /**
     * Handles an exception that terminated a sync run by doing an appropriate action.
     *
     * @param exception             exception thrown during sync
     * @param collectionInfo        affected collection, used to derive the notification tag and title
     * @param local                 local resource that was being processed when the exception occurred, if any
     * @param remote                remote URL that was being processed when the exception occurred, if any
     *
     * @return whether [exception] represents a hard error, soft error or no error
     *
     * @throws CancellationException if the sync was canceled
     * @throws DeadObjectException if the content provider process died (found anywhere in [exception]'s cause chain)
     */
    suspend fun handleException(
        exception: Throwable,
        collectionInfo: Collection,
        local: LocalResource?,
        remote: Url?
    ): SyncErrorResult =
        when (val action = classifySyncException(exception)) {
            is SyncErrorAction.LogWarning -> {
                logger.log(Level.WARNING, action.logMessage, exception)
                SyncErrorResult.NoError
            }

            is SyncErrorAction.SoftError -> {
                logger.log(Level.WARNING, action.logMessage, exception)
                action.notifyMessage?.let { message ->
                    syncNotificationManager.notifyException(
                        syncDataType = dataType,
                        collectionId = collectionInfo.id,
                        message = message,
                        title = collectionInfo.title(),
                        e = exception,
                        local = local,
                        remote = remote
                    )
                }
                SyncErrorResult.SoftError(action.delayUntil)
            }

            is SyncErrorAction.HardError -> {
                logger.log(Level.SEVERE, action.logMessage, exception)
                syncNotificationManager.notifyException(
                    syncDataType = dataType,
                    collectionId = collectionInfo.id,
                    message = action.notifyMessage,
                    title = collectionInfo.title(),
                    e = exception,
                    local = local,
                    remote = remote
                )
                SyncErrorResult.HardError
            }

            is SyncErrorAction.Rethrow ->
                throw action.throwable
        }


    /**
     * What [SyncExceptionHandler] internally decides to do about an exception.
     */
    @VisibleForTesting
    internal sealed interface SyncErrorAction {
        /** Nothing to report: log a warning and continue, no [SyncResult] flag, no notification. */
        data class LogWarning(val logMessage: String) : SyncErrorAction

        /** Temporary/recoverable error. */
        data class SoftError(
            val logMessage: String,
            val delayUntil: Instant? = null,
            val notifyMessage: String? = null
        ) : SyncErrorAction

        /** Non-recoverable error requiring the user's attention. */
        data class HardError(val logMessage: String, val notifyMessage: String) : SyncErrorAction

        /** Sync must be aborted/rescheduled: [throwable] is rethrown by the caller. */
        data class Rethrow(val throwable: Throwable) : SyncErrorAction
    }

    /**
     * Classifies a sync exception into a [SyncErrorAction].
     *
     * @param exception     exception thrown during sync
     */
    @VisibleForTesting
    internal fun classifySyncException(exception: Throwable): SyncErrorAction {
        // A DeadObjectException anywhere in the cause chain means the content provider process died —
        // crashed outright, or (Android 14+) was killed after receiving a binder call while frozen/cached.
        // Either way, retrying later should work, so rethrow the unwrapped exception as a soft error.
        // (See AOSP: android.os.BinderProxy, android.content.ContentProviderClient, CachedAppOptimizer.)
        exception.causedBy<DeadObjectException>()?.let { return SyncErrorAction.Rethrow(it) }

        return when (exception) {
            // Sync was canceled: re-throw to Syncer
            is CancellationException ->
                SyncErrorAction.Rethrow(exception)

            // Special IOException (check before generic IOException)
            is SSLHandshakeException ->
                // when a certificate is rejected by cert4android, the cause will be a CertificateException
                if (exception.cause is CertificateException)
                    SyncErrorAction.LogWarning("SSL handshake failed (certificate rejected)")
                else
                    SyncErrorAction.SoftError(
                        logMessage = "SSL handshake failed",
                        notifyMessage = context.getString(R.string.sync_error_io, exception.localizedMessage)
                    )

            is IOException -> SyncErrorAction.SoftError(
                logMessage = "I/O error",
                notifyMessage = context.getString(R.string.sync_error_io, exception.localizedMessage)
            )

            // HTTP/DAV exceptions (again, check specialized classes before generic)
            is UnauthorizedException -> SyncErrorAction.HardError(
                logMessage = "Not authorized anymore",
                notifyMessage = context.getString(R.string.sync_error_authentication_failed)
            )

            is ServiceUnavailableException -> SyncErrorAction.SoftError(
                logMessage = "Got 503 Service unavailable, trying again later",
                delayUntil = exception.getDelayUntil()
            )

            is HttpException, is DavException -> SyncErrorAction.HardError(
                logMessage = "HTTP/DAV exception",
                notifyMessage = context.getString(R.string.sync_error_http_dav, exception.localizedMessage)
            )

            // Local storage exception
            is LocalStorageException, is RemoteException -> SyncErrorAction.HardError(
                logMessage = "Couldn't access local storage",
                notifyMessage = context.getString(R.string.sync_error_local_storage, exception.localizedMessage)
            )

            // Unknown/unclassified exception
            else -> SyncErrorAction.HardError(
                logMessage = "Unclassified sync error",
                notifyMessage = exception.localizedMessage ?: exception::class.java.simpleName
            )
        }
    }


    /**
     * Logs the exception and notifies the user that a resource couldn't be processed and was ignored.
     *
     * @param exception             exception thrown while processing the resource
     * @param collectionId          ID of the affected collection, used to derive the notification tag
     * @param remote                URL of the resource that couldn't be processed, if it could be resolved
     */
    suspend fun handleInvalidResourceException(
        exception: Throwable,
        collectionId: Long,
        remote: Url?
    ) {
        logger.log(Level.WARNING, "Ignoring invalid resource $remote", exception)
        syncNotificationManager.notifyInvalidResource(
            dataType = dataType,
            collectionId = collectionId,
            e = exception,
            remote = remote,
            title = context.getString(
                when (dataType) {
                    SyncDataType.CONTACTS -> R.string.sync_invalid_contact
                    SyncDataType.EVENTS -> R.string.sync_invalid_event
                    SyncDataType.TASKS -> R.string.sync_invalid_task
                }
            )
        )
    }

}
