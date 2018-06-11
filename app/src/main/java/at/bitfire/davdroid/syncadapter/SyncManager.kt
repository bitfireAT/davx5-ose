/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import at.bitfire.dav4android.DavResponse
import at.bitfire.dav4android.Property
import at.bitfire.dav4android.XmlUtils
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.dav4android.exception.ServiceUnavailableException
import at.bitfire.dav4android.exception.UnauthorizedException
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.DavService
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.SyncState
import at.bitfire.davdroid.resource.*
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.AccountSettingsActivity
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.MiscUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.ContactsStorageException
import okhttp3.HttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.io.IOException
import java.io.InterruptedIOException
import java.security.cert.CertificateException
import java.util.*
import java.util.logging.Level
import javax.net.ssl.SSLHandshakeException

abstract class SyncManager<out ResourceType: LocalResource<*>, out CollectionType: LocalCollection<ResourceType>>(
        val context: Context,
        val settings: ISettings,
        val account: Account,
        val accountSettings: AccountSettings,
        val extras: Bundle,
        val authority: String,
        val syncResult: SyncResult,
        val localCollection: CollectionType
): AutoCloseable {

    companion object {

        fun cancelNotifications(manager: NotificationManagerCompat, authority: String, account: Account) =
                manager.cancel(notificationTag(authority, account), NotificationUtils.NOTIFY_SYNC_ERROR)

        private fun notificationTag(authority: String, account: Account) =
                "$authority-${account.name}".hashCode().toString()

    }

    private val mainAccount = if (localCollection is LocalAddressBook)
        localCollection.mainAccount
    else
        account

    protected val notificationManager = NotificationManagerCompat.from(context)
    protected val notificationTag = notificationTag(authority, mainAccount)

    /** Local resource we're currently operating on. Used for error notifications. **/
    protected val currentLocalResource = LinkedList<LocalResource<*>>()
    /** Remote resource we're currently operating on. Used for error notifications. **/
    protected val currentRemoteResource = LinkedList<HttpUrl>()


    fun performSync() {
        // dismiss previous error notifications
        notificationManager.cancel(notificationTag, NotificationUtils.NOTIFY_SYNC_ERROR)

        try {
            Logger.log.info("Preparing synchronization")
            if (!prepare()) {
                Logger.log.info("No reason to synchronize, aborting")
                return
            }
            abortIfCancelled()

            Logger.log.info("Querying server capabilities")
            var remoteSyncState = queryCapabilities()
            abortIfCancelled()

            Logger.log.info("Sending local deletes/updates to server")
            val modificationsSent =
                    processLocallyDeleted() ||
                    uploadDirty()
            abortIfCancelled()

            if (modificationsSent || syncRequired(remoteSyncState))
                when (syncAlgorithm()) {
                    SyncAlgorithm.PROPFIND_REPORT -> {
                        Logger.log.info("Sync algorithm: full listing as one result (PROPFIND/REPORT)")
                        resetPresentRemotely()

                        // get current sync state
                        if (modificationsSent)
                            remoteSyncState = querySyncState()

                        // list all entries at current sync state (which may be the same as or newer than remoteSyncState)
                        Logger.log.info("Listing remote entries")
                        val remote = listAllRemote()
                        abortIfCancelled()

                        Logger.log.info("Comparing local/remote entries")
                        val changes = compareLocalRemote(remote)

                        Logger.log.info("Processing remote changes")
                        processRemoteChanges(changes)

                        Logger.log.info("Deleting entries which are not present remotely anymore")
                        deleteNotPresentRemotely()

                        Logger.log.info("Post-processing")
                        postProcess()

                        Logger.log.log(Level.INFO, "Saving sync state", remoteSyncState)
                        localCollection.lastSyncState = remoteSyncState
                    }
                    SyncAlgorithm.COLLECTION_SYNC -> {
                        var initialSync = false

                        var syncState = localCollection.lastSyncState?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }
                        if (syncState == null) {
                            Logger.log.info("Starting initial sync")
                            initialSync = true
                            resetPresentRemotely()
                        }

                        Logger.log.info("Listing changes since $syncState")
                        var changes: RemoteChanges? = try {
                            listRemoteChanges(syncState)
                        } catch(e: HttpException) {
                            if (e.errors.contains(Property.Name(XmlUtils.NS_WEBDAV, "valid-sync-token"))) {
                                Logger.log.info("Sync token invalid, retrying from scratch without sync-token")
                                syncState = null
                                initialSync = true
                                resetPresentRemotely()

                                listRemoteChanges(null)
                            } else
                                throw e
                        }

                        if (syncState?.initialSync == true) {
                            Logger.log.info("Continuing initial sync")
                            initialSync = true
                        }

                        while (changes != null) {
                            Logger.log.info("Processing received changes")
                            processRemoteChanges(changes)

                            // save sync state and keep whether we're in initial sync
                            syncState = changes.state ?: throw DavException("Received sync-collection without sync-token")
                            syncState.initialSync = initialSync
                            Logger.log.log(Level.INFO, "Saving sync state", syncState)
                            localCollection.lastSyncState = syncState

                            // request next bunch of changes (if available), or exit loop
                            changes = if (changes.furtherChanges)
                                listRemoteChanges(syncState)
                            else {
                                Logger.log.info("No more changes available on server")
                                null
                            }
                        }

                        if (initialSync) {
                            // initial sync is finished, remove all local resources which have
                            // not been sent by the server
                            Logger.log.info("Deleting local resources which are not on server (anymore)")
                            deleteNotPresentRemotely()

                            // remove initial sync flag
                            syncState!!.initialSync = false
                            Logger.log.log(Level.INFO, "Initial sync completed, saving sync state", syncState)
                            localCollection.lastSyncState = syncState
                        }

                        Logger.log.info("Post-processing")
                        postProcess()
                    }
                }
            else
                Logger.log.info("Remote collection didn't change, no reason to sync")

        }
        // sync was cancelled: re-throw to SyncAdapterService
        catch (e: InterruptedException) { throw e }
        catch (e: InterruptedIOException) { throw e }

        // specific I/O errors
        catch (e: SSLHandshakeException) {
            Logger.log.log(Level.WARNING, "SSL handshake failed", e)

            // when a certificate is rejected by cert4android, the cause will be a CertificateException
            if (!BuildConfig.customCerts || e.cause !is CertificateException)
                notifyException(e)
        }

        // specific HTTP errors
        catch (e: ServiceUnavailableException) {
            Logger.log.log(Level.WARNING, "Got 503 Service unavailable, trying again later", e)
            e.retryAfter?.let { retryAfter ->
                // how many seconds to wait? getTime() returns ms, so divide by 1000
                syncResult.delayUntil = (retryAfter.time - Date().time) / 1000
            }
        }

        // all others
        catch (e: Throwable) { notifyException(e) }
    }


    protected abstract fun prepare(): Boolean

    /**
     * Queries the server for synchronization capabilities like specific report types,
     * data formats etc.
     *
     * Should also query and save the initial sync state (e.g. CTag/sync-token).
     *
     * @return current sync state
     */
    protected abstract fun queryCapabilities(): SyncState?

    /**
     * Queries the remote sync state of the collection.
     *
     * @return sync state (may be null)
     */
    protected abstract fun querySyncState(): SyncState?

    /**
     * Forwards local deletions to the server.
     *
     * @return whether remote resources have been deleted
     */
    protected abstract fun processLocallyDeleted(): Boolean

    /**
     * Uploads locally modified resources to the server.
     *
     * @return whether resources have been uploaded
     */
    protected abstract fun uploadDirty(): Boolean

    /**
     * Determines whether a sync is required because there were changes on the server.
     * For instance, this method can compare the collection's CTag/sync-token with
     * the last known local value.
     *
     * When local changes have been uploaded ([processLocallyDeleted] and/or
     * [uploadDirty] were true), a sync is always required and this method
     * should not be evaluated.
     *
     * @param state remote sync state to compare local sync state with
     *
     * @return whether data has been changed on the server = whether running the
     *   sync algorithm is required
     */
    protected abstract fun syncRequired(state: SyncState?): Boolean

    /**
     * Determines which sync algorithm to use.
     * @return
     *   - [SyncAlgorithm.PROPFIND_REPORT]: list all resources (with plain WebDAV
     *   PROPFIND or specific REPORT requests), then compare and synchronize
     *   - [SyncAlgorithm.COLLECTION_SYNC]: use incremental collection synchronization (RFC 6578)
     */
    protected abstract fun syncAlgorithm(): SyncAlgorithm

    /**
     * Marks all local resources which shall be taken into consideration for this
     * sync as "synchronizing". Purpose of marking is that resources which have been marked
     * and are not present remotely anymore can be deleted.
     *
     * Used together with [deleteNotPresentRemotely].
     */
    protected abstract fun resetPresentRemotely()

    /**
     * Lists all remote resources which should be taken into account for synchronization.
     * Will be used if incremental synchronization is not available.
     *
     * @return map with resource names (like "mycontact.vcf") as keys and the resources
     */
    protected abstract fun listAllRemote(): Map<String, DavResponse>


    /**
     * Compares local resources which are marked for synchronization and remote resources by file name and ETag.
     * Remote resources
     *   + which are not present locally
     *   + whose ETag has changed since the last sync (i.e. remote ETag != locally known last remote ETag)
     * will be saved as "updated" in the result.
     *
     * Must mark all found remote resources as "present remotely", so that a later execution of
     * [deleteNotPresentRemotely] doesn't (locally) delete any currently available remote resources.
     *
     * @param remoteResources map of remote resource names and resources
     *
     * @return List of updated resources on the server. The "deleted" list remains empty. Sync
     *         state will be null.
     */
    protected abstract fun compareLocalRemote(remoteResources: Map<String, DavResponse>): RemoteChanges

    /**
     * Lists remote changes (incremental sync).
     *
     * Must mark all found remote resources as "present remotely", so that a later execution of
     * [deleteNotPresentRemotely] doesn't (locally) delete any currently available remote resources.
     *
     * @return list of of remote changes together with the sync state after those changes
     */
    protected abstract fun listRemoteChanges(state: SyncState?): RemoteChanges

    /**
     * Processes remote changes:
     *   + downloads and locally saves remotely updated resources
     *   + locally deletes remotely deleted resources
     *
     * Should call [abortIfCancelled] from time to time, for instance
     * after downloading a resource.
     *
     * Must mark downloaded resources as present on server.
     *
     * @param changes list of remotely updated and deleted resources
     */
    protected abstract fun processRemoteChanges(changes: RemoteChanges)

    /**
     * Locally deletes entries which are
     *   1. not dirty and
     *   2. not marked as [LocalResource.FLAG_REMOTELY_PRESENT].
     *
     * Used together with [resetPresentRemotely] when a full listing has been received from
     * the server to locally delete resources which are not present remotely (anymore).
     */
    protected abstract fun deleteNotPresentRemotely()

    /**
     * Post-processing of synchronized entries, for instance contact group membership operations.
     */
    protected abstract fun postProcess()


    /**
     * Throws an [InterruptedException] if the current thread has been interrupted,
     * most probably because synchronization was cancelled by the user.
     *
     * @throws InterruptedException (which will be caught by [performSync])
     * */
    protected fun abortIfCancelled() {
        if (Thread.interrupted())
            throw InterruptedException("Sync was cancelled")
    }

    private fun notifyException(e: Throwable) {
        val message: String

        when (e) {
            is IOException,
            is InterruptedIOException -> {
                Logger.log.log(Level.WARNING, "I/O error", e)
                message = context.getString(R.string.sync_error_io, e.localizedMessage)
                syncResult.stats.numIoExceptions++
            }
            is UnauthorizedException -> {
                Logger.log.log(Level.SEVERE, "Not authorized anymore", e)
                message = context.getString(R.string.sync_error_authentication_failed)
                syncResult.stats.numAuthExceptions++
            }
            is HttpException, is DavException -> {
                Logger.log.log(Level.SEVERE, "HTTP/DAV exception", e)
                message = context.getString(R.string.sync_error_http_dav, e.localizedMessage)
                syncResult.stats.numParseExceptions++       // numIoExceptions would indicate a soft error
            }
            is CalendarStorageException, is ContactsStorageException, is RemoteException -> {
                Logger.log.log(Level.SEVERE, "Couldn't access local storage", e)
                message = context.getString(R.string.sync_error_local_storage, e.localizedMessage)
                syncResult.databaseError = true
            }
            else -> {
                Logger.log.log(Level.SEVERE, "Unclassified sync error", e)
                message = e.localizedMessage ?: e::class.java.simpleName
                syncResult.stats.numParseExceptions++
            }
        }

        val contentIntent: Intent
        var viewItemAction: NotificationCompat.Action? = null
        if (e is UnauthorizedException) {
            contentIntent = Intent(context, AccountSettingsActivity::class.java)
            contentIntent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
        } else {
            contentIntent = Intent(context, DebugInfoActivity::class.java)
            contentIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e)
            contentIntent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account)
            contentIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority)

            // use current local/remote resource
            currentLocalResource.firstOrNull()?.let { local ->
                // pass local resource info to debug info
                contentIntent.putExtra(DebugInfoActivity.KEY_LOCAL_RESOURCE, local.toString())

                // generate "view item" action
                viewItemAction = buildViewItemAction(local)
            }
            currentRemoteResource.firstOrNull()?.let { remote ->
                contentIntent.putExtra(DebugInfoActivity.KEY_REMOTE_RESOURCE, remote.toString())
            }
        }

        // to make the PendingIntent unique
        contentIntent.data = Uri.parse("davdroid:exception/${e.hashCode()}")

        val channel: String
        val priority: Int
        if (e is IOException) {
            channel = NotificationUtils.CHANNEL_SYNC_IO_ERRORS
            priority = NotificationCompat.PRIORITY_MIN
        } else {
            channel = NotificationUtils.CHANNEL_SYNC_ERRORS
            priority = NotificationCompat.PRIORITY_DEFAULT
        }

        val builder = NotificationUtils.newBuilder(context, channel)
        builder .setSmallIcon(R.drawable.ic_sync_error_notification)
                .setContentTitle(localCollection.title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle(builder).bigText(message))
                .setSubText(mainAccount.name)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setPriority(priority)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
        viewItemAction?.let { builder.addAction(it) }
        builder.addAction(buildRetryAction())

        notificationManager.notify(notificationTag, NotificationUtils.NOTIFY_SYNC_ERROR, builder.build())
    }

    private fun buildRetryAction(): NotificationCompat.Action {
        val retryIntent = Intent(context, DavService::class.java)
        retryIntent.action = DavService.ACTION_FORCE_SYNC

        val syncAuthority: String
        val syncAccount: Account
        if (authority == ContactsContract.AUTHORITY) {
            // if this is a contacts sync, retry syncing all address books of the main account
            syncAuthority = context.getString(R.string.address_books_authority)
            syncAccount = mainAccount
        } else {
            syncAuthority = authority
            syncAccount = account
        }

        retryIntent.data = Uri.parse("sync://").buildUpon()
                .authority(syncAuthority)
                .appendPath(syncAccount.type)
                .appendPath(syncAccount.name)
                .build()

        return NotificationCompat.Action(
                android.R.drawable.ic_menu_rotate, context.getString(R.string.sync_error_retry),
                PendingIntent.getService(context, 0, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun buildViewItemAction(local: LocalResource<*>): NotificationCompat.Action? {
        Logger.log.log(Level.FINE, "Adding view action for local resource", local)
        val intent = local.id?.let { id ->
            when (local) {
                is LocalContact ->
                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, id))
                is LocalEvent ->
                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
                is LocalTask ->
                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority), id))
                else ->
                    null
            }
        }
        return if (intent != null && context.packageManager.resolveActivity(intent, 0) != null)
            NotificationCompat.Action(android.R.drawable.ic_menu_view, context.getString(R.string.sync_error_view_item),
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
        else
            null
    }


    enum class SyncAlgorithm {
        PROPFIND_REPORT,
        COLLECTION_SYNC
    }

    class RemoteChanges(
        val state: SyncState?,
        val furtherChanges: Boolean
    ) {
        val deleted = LinkedList<String>()
        val updated = LinkedList<DavResponse>()

        override fun toString() = MiscUtils.reflectionToString(this)
    }

}