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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.*
import at.bitfire.dav4jvm.exception.*
import at.bitfire.dav4jvm.property.GetCTag
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.SyncToken
import at.bitfire.davdroid.*
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.SyncState
import at.bitfire.davdroid.resource.*
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.account.SettingsActivity
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.ContactsStorageException
import okhttp3.HttpUrl
import okhttp3.RequestBody
import org.apache.commons.lang3.exception.ContextedException
import org.dmfs.tasks.contract.TaskContract
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.security.cert.CertificateException
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import javax.net.ssl.SSLHandshakeException
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
abstract class SyncManager<ResourceType: LocalResource<*>, out CollectionType: LocalCollection<ResourceType>, RemoteType: DavCollection>(
        val context: Context,
        val account: Account,
        val accountSettings: AccountSettings,
        val extras: Bundle,
        val authority: String,
        val syncResult: SyncResult,
        val localCollection: CollectionType
): AutoCloseable {

    enum class SyncAlgorithm {
        PROPFIND_REPORT,
        COLLECTION_SYNC
    }

    companion object {

        val MAX_PROCESSING_THREADS =    // nCPU/2 (rounded up for case of 1 CPU), but max. 4
                min((Runtime.getRuntime().availableProcessors()+1)/2, 4)
        val MAX_DOWNLOAD_THREADS =      // one (if one CPU), 2 otherwise
                min(Runtime.getRuntime().availableProcessors(), 2)
        const val MAX_MULTIGET_RESOURCES = 10

        fun cancelNotifications(manager: NotificationManagerCompat, authority: String, account: Account) =
                manager.cancel(notificationTag(authority, account), NotificationUtils.NOTIFY_SYNC_ERROR)

        private fun notificationTag(authority: String, account: Account) =
                "$authority-${account.name}".hashCode().toString()

    }

    init {
        Logger.log.info("SyncManager: using up to $MAX_PROCESSING_THREADS processing threads and $MAX_DOWNLOAD_THREADS download threads")
    }

    private val mainAccount = if (localCollection is LocalAddressBook)
        localCollection.mainAccount
    else
        account

    protected val notificationManager = NotificationManagerCompat.from(context)
    protected val notificationTag = notificationTag(authority, mainAccount)

    protected val httpClient = HttpClient.Builder(context, accountSettings).build()

    protected lateinit var collectionURL: HttpUrl
    protected lateinit var davCollection: RemoteType

    protected var hasCollectionSync = false

    override fun close() {
        httpClient.close()
    }


    fun performSync() {
        // dismiss previous error notifications
        notificationManager.cancel(notificationTag, NotificationUtils.NOTIFY_SYNC_ERROR)

        unwrapExceptions({
            Logger.log.info("Preparing synchronization")
            if (!prepare()) {
                Logger.log.info("No reason to synchronize, aborting")
                return@unwrapExceptions
            }
            abortIfCancelled()

            Logger.log.info("Querying server capabilities")
            var remoteSyncState = queryCapabilities()
            abortIfCancelled()

            Logger.log.info("Sending local deletes/updates to server")
            val modificationsSent = processLocallyDeleted() ||
                    uploadDirty()
            abortIfCancelled()

            if (extras.containsKey(SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC)) {
                Logger.log.info("Forcing re-synchronization of all entries")

                // forget sync state of collection (→ initial sync in case of SyncAlgorithm.COLLECTION_SYNC)
                localCollection.lastSyncState = null
                remoteSyncState = null

                // forget sync state of members (→ download all members again and update them locally)
                localCollection.forgetETags()
            }

            if (modificationsSent || syncRequired(remoteSyncState))
                when (syncAlgorithm()) {
                    SyncAlgorithm.PROPFIND_REPORT -> {
                        Logger.log.info("Sync algorithm: full listing as one result (PROPFIND/REPORT)")
                        resetPresentRemotely()

                        // get current sync state
                        if (modificationsSent)
                            remoteSyncState = querySyncState()

                        // list and process all entries at current sync state (which may be the same as or newer than remoteSyncState)
                        Logger.log.info("Processing remote entries")
                        syncRemote { callback ->
                            listAllRemote(callback)
                        }

                        Logger.log.info("Deleting entries which are not present remotely anymore")
                        syncResult.stats.numDeletes += deleteNotPresentRemotely()

                        Logger.log.info("Post-processing")
                        postProcess()

                        Logger.log.log(Level.INFO, "Saving sync state", remoteSyncState)
                        localCollection.lastSyncState = remoteSyncState
                    }
                    SyncAlgorithm.COLLECTION_SYNC -> {
                        var syncState = localCollection.lastSyncState?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }

                        var initialSync = false
                        if (syncState == null) {
                            Logger.log.info("Starting initial sync")
                            initialSync = true
                            resetPresentRemotely()
                        } else if (syncState.initialSync == true) {
                            Logger.log.info("Continuing initial sync")
                            initialSync = true
                        }

                        var furtherChanges = false
                        do {
                            Logger.log.info("Listing changes since $syncState")
                            syncRemote { callback ->
                                try {
                                    val result = listRemoteChanges(syncState, callback)
                                    syncState = SyncState.fromSyncToken(result.first, initialSync)
                                    furtherChanges = result.second
                                } catch(e: HttpException) {
                                    if (e.errors.contains(Error.VALID_SYNC_TOKEN)) {
                                        Logger.log.info("Sync token invalid, performing initial sync")
                                        initialSync = true
                                        resetPresentRemotely()

                                        val result = listRemoteChanges(null, callback)
                                        syncState = SyncState.fromSyncToken(result.first, initialSync)
                                        furtherChanges = result.second
                                    } else
                                        throw e
                                }

                                Logger.log.log(Level.INFO, "Saving sync state", syncState)
                                localCollection.lastSyncState = syncState
                            }

                            Logger.log.info("Server has further changes: $furtherChanges")
                        } while(furtherChanges)

                        if (initialSync) {
                            // initial sync is finished, remove all local resources which have not been listed by server
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

        }, { e, local, remote ->
            when (e) {
                // sync was cancelled: re-throw to SyncAdapterService
                is InterruptedException,
                is InterruptedIOException ->
                    throw e

                // specific I/O errors
                is SSLHandshakeException -> {
                    Logger.log.log(Level.WARNING, "SSL handshake failed", e)

                    // when a certificate is rejected by cert4android, the cause will be a CertificateException
                    if (!BuildConfig.customCerts || e.cause !is CertificateException)
                        notifyException(e, local, remote)
                }

                // specific HTTP errors
                is ServiceUnavailableException -> {
                    Logger.log.log(Level.WARNING, "Got 503 Service unavailable, trying again later", e)
                    e.retryAfter?.let { retryAfter ->
                        // how many seconds to wait? getTime() returns ms, so divide by 1000
                        syncResult.delayUntil = (retryAfter.time - Date().time) / 1000
                    }
                }

                // all others
                else ->
                    notifyException(e, local, remote)
            }
        })
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
     * Processes locally deleted entries and forwards them to the server (HTTP `DELETE`).
     *
     * @return whether resources have been deleted from the server
     */
    protected open fun processLocallyDeleted(): Boolean {
        var numDeleted = 0

        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        val localList = localCollection.findDeleted()
        for (local in localList) {
            abortIfCancelled()
            useLocal(local) {
                val fileName = local.fileName
                if (fileName != null) {
                    Logger.log.info("$fileName has been deleted locally -> deleting from server (ETag ${local.eTag})")

                    useRemote(DavResource(httpClient.okHttpClient, collectionURL.newBuilder().addPathSegment(fileName).build())) { remote ->
                        try {
                            remote.delete(local.eTag) {}
                            numDeleted++
                        } catch (e: HttpException) {
                            Logger.log.warning("Couldn't delete $fileName from server; ignoring (may be downloaded again)")
                        }
                    }
                } else
                    Logger.log.info("Removing local record #${local.id} which has been deleted locally and was never uploaded")
                local.delete()
                syncResult.stats.numDeletes++
            }
        }
        Logger.log.info("Removed $numDeleted record(s) from server")
        return numDeleted > 0
    }

    /**
     * Uploads locally modified resources to the server (HTTP `PUT`).
     *
     * @return whether resources have been uploaded
     */
    protected open fun uploadDirty(): Boolean {
        var numUploaded = 0

        // make sure all resources have file name and UID before uploading them
        for (local in localCollection.findDirtyWithoutNameOrUid())
            useLocal(local) {
                Logger.log.fine("Generating file name/UID for local resource #${local.id}")
                local.assignNameAndUID()
            }

        // upload dirty resources
        for (local in localCollection.findDirty())
            useLocal(local) {
                abortIfCancelled()

                val fileName = local.fileName!!
                useRemote(DavResource(httpClient.okHttpClient, collectionURL.newBuilder().addPathSegment(fileName).build())) { remote ->
                    // generate entity to upload (VCard, iCal, whatever)
                    val body = prepareUpload(local)

                    var eTag: String? = null
                    val processETag: (response: okhttp3.Response) -> Unit = { response ->
                        response.header("ETag")?.let { getETag ->
                            eTag = GetETag(getETag).eTag
                        }
                    }
                    try {
                        if (local.eTag == null) {
                            Logger.log.info("Uploading new record $fileName")
                            remote.put(body, null, true, processETag)
                        } else {
                            Logger.log.info("Uploading locally modified record $fileName")
                            remote.put(body, local.eTag, false, processETag)
                        }
                        numUploaded++
                    } catch(e: ForbiddenException) {
                        // HTTP 403 Forbidden
                        // If and only if the upload failed because of missing permissions, treat it like 412.
                        if (e.errors.contains(Error.NEED_PRIVILEGES))
                            Logger.log.log(Level.INFO, "Couldn't upload because of missing permissions, ignoring", e)
                        else
                            throw e
                    } catch(e: ConflictException) {
                        // HTTP 409 Conflict
                        // We can't interact with the user to resolve the conflict, so we treat 409 like 412.
                        Logger.log.log(Level.INFO, "Edit conflict, ignoring", e)
                    } catch(e: PreconditionFailedException) {
                        // HTTP 412 Precondition failed: Resource has been modified on the server in the meanwhile.
                        // Ignore this condition so that the resource can be downloaded and reset again.
                        Logger.log.log(Level.INFO, "Resource has been modified on the server before upload, ignoring", e)
                    }

                    if (eTag != null)
                        Logger.log.fine("Received new ETag=$eTag after uploading")
                    else
                        Logger.log.fine("Didn't receive new ETag after uploading, setting to null")

                    local.clearDirty(eTag)
                }
            }
        Logger.log.info("Sent $numUploaded record(s) to server")
        return numUploaded > 0
    }

    protected abstract fun prepareUpload(resource: ResourceType): RequestBody

    /**
     * Determines whether a sync is required because there were changes on the server.
     * For instance, this method can compare the collection's `CTag`/`sync-token` with
     * the last known local value.
     *
     * When local changes have been uploaded ([processLocallyDeleted] and/or
     * [uploadDirty] were true), a sync is always required and this method
     * should *not* be evaluated.
     *
     * Will return _true_ if [SyncAdapterService.SYNC_EXTRAS_RESYNC] and/or
     * [SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC] is set in [extras].
     *
     * @param state remote sync state to compare local sync state with
     *
     * @return whether data has been changed on the server, i.e. whether running the
     * sync algorithm is required
     */
    protected open fun syncRequired(state: SyncState?): Boolean {
        if (extras.containsKey(SyncAdapterService.SYNC_EXTRAS_RESYNC) ||
            extras.containsKey(SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC))
            return true

        val localState = localCollection.lastSyncState
        Logger.log.info("Local sync state = $localState, remote sync state = $state")
        return when {
            state?.type == SyncState.Type.SYNC_TOKEN -> {
                val lastKnownToken = localState?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }?.value
                lastKnownToken != state.value
            }
            state?.type == SyncState.Type.CTAG -> {
                val lastKnownCTag = localState?.takeIf { it.type == SyncState.Type.CTAG }?.value
                lastKnownCTag != state.value
            }
            else ->
                true
        }
    }

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
    protected open fun resetPresentRemotely() {
        val number = localCollection.markNotDirty(0)
        Logger.log.info("Number of local non-dirty entries: $number")
    }

    protected open fun syncRemote(listRemote: (DavResponseCallback) -> Unit) {
        // results must be processed in main thread because exceptions must be thrown in main
        // thread, so that they can be catched by SyncManager
        val results = ConcurrentLinkedQueue<Future<*>>()

        // thread-safe sync stats
        val nInserted = AtomicInteger()
        val nUpdated = AtomicInteger()
        val nDeleted = AtomicInteger()
        val nSkipped = AtomicInteger()

        // download queue
        val toDownload = LinkedBlockingQueue<HttpUrl>()

        // tasks from this executor create the download tasks (if necessary)
        val processor = ThreadPoolExecutor(1, MAX_PROCESSING_THREADS,
                10, TimeUnit.SECONDS,
                LinkedBlockingQueue(MAX_PROCESSING_THREADS),  // accept up to MAX_PROCESSING_THREADS processing tasks
                ThreadPoolExecutor.CallerRunsPolicy()         // if the queue is full, run task in submitting thread
        )

        // this executor runs the actual download tasks
        val downloader = ThreadPoolExecutor(0, MAX_DOWNLOAD_THREADS,
                10, TimeUnit.SECONDS,
                LinkedBlockingQueue(MAX_DOWNLOAD_THREADS),  // accept up to MAX_DOWNLOAD_THREADS download tasks
                ThreadPoolExecutor.CallerRunsPolicy()       // if the queue is full, run task in submitting thread
        )
        fun downloadBunch() {
            val bunch = LinkedList<HttpUrl>()
            toDownload.drainTo(bunch, MAX_MULTIGET_RESOURCES)
            results += downloader.submit {
                downloadRemote(bunch)
            }
        }

        listRemote { response, relation ->
            // ignore non-members
            if (relation != Response.HrefRelation.MEMBER)
                return@listRemote

            // ignore collections
            if (response[at.bitfire.dav4jvm.property.ResourceType::class.java]?.types?.contains(at.bitfire.dav4jvm.property.ResourceType.COLLECTION) == true)
                return@listRemote

            val name = response.hrefName()

            if (response.isSuccess()) {
                Logger.log.fine("Found remote resource: $name")

                results += processor.submit {
                    useLocal(localCollection.findByName(name)) { local ->
                        if (local == null) {
                            Logger.log.info("$name has been added remotely")
                            toDownload += response.href
                            nInserted.incrementAndGet()
                        } else {
                            val localETag = local.eTag
                            val remoteETag = response[GetETag::class.java]?.eTag ?: throw DavException("Server didn't provide ETag")
                            if (localETag == remoteETag) {
                                Logger.log.info("$name has not been changed on server (ETag still $remoteETag)")
                                nSkipped.incrementAndGet()
                            } else {
                                Logger.log.info("$name has been changed on server (current ETag=$remoteETag, last known ETag=$localETag)")
                                toDownload += response.href
                                nUpdated.incrementAndGet()
                            }

                            // mark as remotely present, so that this resource won't be deleted at the end
                            local.updateFlags(LocalResource.FLAG_REMOTELY_PRESENT)
                        }
                    }

                    synchronized(processor) {
                        if (toDownload.size >= MAX_MULTIGET_RESOURCES)
                            // download another bunch of MAX_MULTIGET_RESOURCES resources
                            downloadBunch()
                    }
                }

            } else if (response.status?.code == HttpURLConnection.HTTP_NOT_FOUND) {
                // collection sync: resource has been deleted on remote server
                results += processor.submit {
                    useLocal(localCollection.findByName(name)) { local ->
                        Logger.log.info("$name has been deleted on server, deleting locally")
                        local?.delete()
                        nDeleted.incrementAndGet()
                    }
                }
            }

            // check already available results for exceptions so that they don't become too many
            checkResults(results)
        }

        // process remaining responses
        processor.shutdown()
        processor.awaitTermination(5, TimeUnit.MINUTES)

        // download remaining resources
        if (toDownload.isNotEmpty())
            downloadBunch()

        // signal end of queue and wait for download thread
        downloader.shutdown()
        downloader.awaitTermination(5, TimeUnit.MINUTES)

        // check remaining results for exceptions
        checkResults(results)

        // update sync stats
        with(syncResult.stats) {
            numInserts += nInserted.get()
            numUpdates += nUpdated.get()
            numDeletes += nDeleted.get()
            numSkippedEntries += nSkipped.get()
        }
    }

    protected abstract fun listAllRemote(callback: DavResponseCallback)

    protected open fun listRemoteChanges(syncState: SyncState?, callback: DavResponseCallback): Pair<SyncToken, Boolean> {
        var furtherResults = false

        val report = davCollection.reportChanges(
                syncState?.takeIf { syncState.type == SyncState.Type.SYNC_TOKEN }?.value,
                false, null,
                GetETag.NAME) { response, relation ->
            when (relation) {
                Response.HrefRelation.SELF ->
                    furtherResults = response.status?.code == 507

                Response.HrefRelation.MEMBER ->
                    callback(response, relation)

                else ->
                    Logger.log.fine("Unexpected sync-collection response: $response")
            }
        }

        var syncToken: SyncToken? = null
        report.filterIsInstance(SyncToken::class.java).firstOrNull()?.let {
            syncToken = it
        }
        if (syncToken == null)
            throw DavException("Received sync-collection response without sync-token")

        return Pair(syncToken!!, furtherResults)
    }

    protected abstract fun downloadRemote(bunch: List<HttpUrl>)

    /**
     * Locally deletes entries which are
     *   1. not dirty and
     *   2. not marked as [LocalResource.FLAG_REMOTELY_PRESENT].
     *
     * Used together with [resetPresentRemotely] when a full listing has been received from
     * the server to locally delete resources which are not present remotely (anymore).
     */
    protected open fun deleteNotPresentRemotely(): Int {
        val removed = localCollection.removeNotDirtyMarked(0)
        Logger.log.info("Removed $removed local resources which are not present on the server anymore")
        return removed
    }

    /**
     * Post-processing of synchronized entries, for instance contact group membership operations.
     */
    protected abstract fun postProcess()


    // sync helpers

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

    protected fun syncState(dav: Response) =
            dav[SyncToken::class.java]?.token?.let {
                SyncState(SyncState.Type.SYNC_TOKEN, it)
            } ?:
            dav[GetCTag::class.java]?.cTag?.let {
                SyncState(SyncState.Type.CTAG, it)
            }

    private fun querySyncState(): SyncState? {
        var state: SyncState? = null
        davCollection.propfind(0, GetCTag.NAME, SyncToken.NAME) { response, relation ->
            if (relation == Response.HrefRelation.SELF)
                state = syncState(response)
        }
        return state
    }


    // exception helpers

    private fun notifyException(e: Throwable, local: ResourceType?, remote: HttpUrl?) {
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
            contentIntent = Intent(context, SettingsActivity::class.java)
            contentIntent.putExtra(SettingsActivity.EXTRA_ACCOUNT,
                    if (authority == ContactsContract.AUTHORITY)
                        mainAccount
                    else
                        account)
        } else {
            contentIntent = buildDebugInfoIntent(e, local, remote)
            if (local != null)
                viewItemAction = buildViewItemAction(local)
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
        builder .setSmallIcon(R.drawable.ic_sync_problem_notify)
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

    private fun buildDebugInfoIntent(e: Throwable, local: ResourceType?, remote: HttpUrl?) =
            Intent(context, DebugInfoActivity::class.java).apply {
                putExtra(DebugInfoActivity.KEY_ACCOUNT, account)
                putExtra(DebugInfoActivity.KEY_AUTHORITY, authority)
                putExtra(DebugInfoActivity.KEY_THROWABLE, e)

                // pass current local/remote resource
                if (local != null)
                    putExtra(DebugInfoActivity.KEY_LOCAL_RESOURCE, local.toString())
                if (remote != null)
                    putExtra(DebugInfoActivity.KEY_REMOTE_RESOURCE, remote.toString())
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

    private fun buildViewItemAction(local: ResourceType): NotificationCompat.Action? {
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


    fun checkResults(results: MutableCollection<Future<*>>) {
        val iter = results.iterator()
        while (iter.hasNext()) {
            val result = iter.next()
            if (result.isDone) {
                try {
                    result.get()
                } catch(e: ExecutionException) {
                    throw e.cause!!
                }
                iter.remove()
            }
        }
    }

    protected fun notifyInvalidResource(e: Throwable, fileName: String) {
        val intent = buildDebugInfoIntent(e, null, collectionURL.resolve(fileName))

        val builder = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_WARNINGS)
        builder .setSmallIcon(R.drawable.ic_warning_notify)
                .setContentTitle(notifyInvalidResourceTitle())
                .setContentText(context.getString(R.string.sync_invalid_resources_ignoring))
                .setSubText(mainAccount.name)
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .priority = NotificationCompat.PRIORITY_LOW
        notificationManager.notify(notificationTag, NotificationUtils.NOTIFY_INVALID_RESOURCE, builder.build())
    }

    protected abstract fun notifyInvalidResourceTitle(): String

    protected fun<T: ResourceType?, R> useLocal(local: T, body: (T) -> R): R {
        try {
            return body(local)
        } catch (e: ContextedException) {
            e.addContextValue(Constants.EXCEPTION_CONTEXT_LOCAL_RESOURCE, local)
            throw e
        } catch (e: Throwable) {
            if (local != null)
                throw ContextedException(e).setContextValue(Constants.EXCEPTION_CONTEXT_LOCAL_RESOURCE, local)
            else
                throw e
        }
    }

    protected fun<T: DavResource, R> useRemote(remote: T, body: (T) -> R): R {
        try {
            return body(remote)
        } catch (e: ContextedException) {
            e.addContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.location)
            throw e
        } catch(e: Throwable) {
            throw ContextedException(e).setContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.location)
        }
    }

    protected fun<T> useRemote(remote: Response, body: (Response) -> T): T {
        try {
            return body(remote)
        } catch (e: ContextedException) {
            e.addContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.href)
            throw e
        } catch (e: Throwable) {
            throw ContextedException(e).setContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.href)
        }
    }

    protected fun<R> useRemoteCollection(body: (RemoteType) -> R) =
            useRemote(davCollection, body)

    private fun unwrapExceptions(body: () -> Unit, handler: (e: Throwable, local: ResourceType?, remote: HttpUrl?) -> Unit) {
        var ex: Throwable? = null
        try {
            body()
        } catch(e: Throwable) {
            ex = e
        }

        var local: ResourceType? = null
        var remote: HttpUrl? = null

        if (ex is ContextedException) {
            @Suppress("UNCHECKED_CAST")
            // we want the innermost context value, which is the first one
            (ex.getFirstContextValue(Constants.EXCEPTION_CONTEXT_LOCAL_RESOURCE) as? ResourceType)?.let {
                if (local == null)
                    local = it
            }
            (ex.getFirstContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE) as? HttpUrl)?.let {
                if (remote == null)
                    remote = it
            }
            ex = ex.cause
        }

        if (ex != null)
            handler(ex, local, remote)
    }


}