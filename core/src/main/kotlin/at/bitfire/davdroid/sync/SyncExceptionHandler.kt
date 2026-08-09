/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.ktor.exception.ServiceUnavailableException
import at.bitfire.dav4jvm.ktor.exception.UnauthorizedException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.storage.LocalStorageException
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import java.io.IOException
import java.security.cert.CertificateException
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
    @Assisted private val syncResult: SyncResult,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    syncNotificationManagerFactory: SyncNotificationManager.Factory
) {

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, dataType: SyncDataType, syncResult: SyncResult): SyncExceptionHandler
    }

    private val syncNotificationManager = syncNotificationManagerFactory.create(accountId)

    /**
     * Dismisses a previously shown sync error notification for the given local collection.
     */
    fun dismissPreviousErrors(localCollectionTag: String) =
        syncNotificationManager.dismissCollectionError(localCollectionTag)

    /**
     * Handles an exception that terminated a sync run: rethrows exceptions that must abort/reschedule
     * the sync, otherwise updates [syncResult] and notifies the user.
     */
    suspend fun handleException(
        exception: Throwable,
        localCollectionTag: String,
        localCollectionTitle: String,
        local: LocalResource?,
        remote: Url?
    ) {
        when (exception) {
            /* LocalStorageException with cause DeadObjectException may occur when syncing takes too long
            and process is demoted to "cached". In this case, we re-throw to the base Syncer which will
            treat it as a soft error and re-schedule the sync process. */
            is LocalStorageException if exception.cause is DeadObjectException ->
                throw exception

            // sync was cancelled or account has been removed: re-throw to Syncer
            is CancellationException,
            is InvalidAccountException ->
                throw exception

            // specific I/O errors
            is SSLHandshakeException -> {
                logger.log(Level.WARNING, "SSL handshake failed", exception)

                // when a certificate is rejected by cert4android, the cause will be a CertificateException
                if (exception.cause !is CertificateException)
                    notifyException(exception, localCollectionTag, localCollectionTitle, local, remote)
            }

            // specific HTTP errors
            is ServiceUnavailableException -> {
                logger.log(Level.WARNING, "Got 503 Service unavailable, trying again later", exception)
                // determine when to retry
                syncResult.delayUntil = exception.getDelayUntil().epochSecond
                syncResult.softError = true
            }

            // all others
            else ->
                notifyException(exception, localCollectionTag, localCollectionTitle, local, remote)
        }
    }

    /**
     * Logs the exception, updates [syncResult] and shows a notification to the user.
     */
    private suspend fun notifyException(
        exception: Throwable,
        localCollectionTag: String,
        localCollectionTitle: String,
        local: LocalResource?,
        remote: Url?
    ) {
        val message: String
        when (exception) {
            is IOException -> {
                logger.log(Level.WARNING, "I/O error", exception)
                syncResult.softError = true
                message = context.getString(R.string.sync_error_io, exception.localizedMessage)
            }

            is UnauthorizedException -> {
                logger.log(Level.SEVERE, "Not authorized anymore", exception)
                syncResult.hardError = true
                message = context.getString(R.string.sync_error_authentication_failed)
            }

            is HttpException, is DavException -> {
                logger.log(Level.SEVERE, "HTTP/DAV exception", exception)
                syncResult.hardError = true
                message = context.getString(R.string.sync_error_http_dav, exception.localizedMessage)
            }

            is LocalStorageException, is RemoteException -> {
                logger.log(Level.SEVERE, "Couldn't access local storage", exception)
                syncResult.hardError = true
                message = context.getString(R.string.sync_error_local_storage, exception.localizedMessage)
            }

            else -> {
                logger.log(Level.SEVERE, "Unclassified sync error", exception)
                syncResult.hardError = true
                message = exception.localizedMessage ?: exception::class.java.simpleName
            }
        }

        syncNotificationManager.notifyException(
            syncDataType = dataType,
            notificationTag = localCollectionTag,
            message = message,
            title = localCollectionTitle,
            exception = exception,
            local = local,
            remote = remote
        )
    }

    /**
     * Logs the exception and notifies the user that a resource couldn't be processed and was ignored.
     */
    suspend fun handleInvalidResourceException(
        exception: Throwable,
        localCollectionTag: String,
        collectionInfo: Collection,
        fileName: String
    ) {
        logger.log(Level.WARNING, "Ignoring invalid resource $fileName", exception)
        syncNotificationManager.notifyInvalidResource(
            dataType = dataType,
            notificationTag = localCollectionTag,
            collection = collectionInfo,
            exception = exception,
            fileName = fileName,
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
