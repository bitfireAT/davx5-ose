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
        /** Just log a warning – no error that must be reported. */
        data class LogWarning(val logMessage: String) : SyncErrorAction

        /** Temporary/recoverable error. */
        data class SoftError(
            val logMessage: String,
            val delayUntil: Instant? = null,
            val notifyMessage: String? = null
        ) : SyncErrorAction

        /** Non-recoverable error requiring the user's attention. */
        data class HardError(val logMessage: String, val notifyMessage: String) : SyncErrorAction

        /** Sync must be aborted/rescheduled: re-throw to [Syncer]. */
        data class Rethrow(val throwable: Throwable) : SyncErrorAction
    }

    /**
     * Classifies a sync exception into a [SyncErrorAction].
     *
     * @param exception     exception thrown during sync
     */
    @VisibleForTesting
    internal fun classifySyncException(exception: Throwable): SyncErrorAction {
        /* A DeadObjectException anywhere in the cause chain means the content provider process died:
        either because it crashed, or because of this Android 14+ behavior:

        1. Holding a ContentProviderClient doesn't keep the provider process "important" - its priority
           is derived from whatever's currently bound to it, recomputed continuously.
        2. Since we're just a background sync worker (not a foreground service), our own importance is
           low, so the provider's derived importance can drop into the "cached" range even while we're
           still connected to it.
        3. Android's app freezer suspends cached processes to save battery/CPU.
        4. If we then make a synchronous call into it while frozen, Android treats this as a bug on our
           side and kills the frozen process.
        5. That kill is what surfaces here as DeadObjectException.

        See AOSP frameworks/base:
        - OomAdjuster.computeProviderHostOomAdjLSP() - derives a provider's importance (adj) from its client,
          showing why holding a connection doesn't pin its priority.
        - com.android.server.am.CachedAppOptimizer - implements freezer and kill-on-sync-call-while-frozen policy.
        - android.os.BinderProxy - where DeadObjectException is actually thrown once the process is dead.

        Either way, retrying later should work, so rethrow the unwrapped exception as a soft error. */
        exception.causedBy<DeadObjectException>()?.let {
            // return unwrapped for explicitness
            return SyncErrorAction.Rethrow(it)
        }

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
