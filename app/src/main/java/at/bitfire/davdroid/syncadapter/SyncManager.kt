/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

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
import at.bitfire.dav4jvm.property.ScheduleTag
import at.bitfire.dav4jvm.property.SyncToken
import at.bitfire.davdroid.*
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.db.SyncStats
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.*
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.account.SettingsActivity
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.UsesThreadContextClassLoader
import at.bitfire.vcard4android.ContactsStorageException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.RequestBody
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ContextedException
import org.dmfs.tasks.contract.TaskContract
import java.io.IOException
import java.io.InterruptedIOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.security.cert.CertificateException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import javax.net.ssl.SSLHandshakeException

@UsesThreadContextClassLoader
abstract class SyncManager<ResourceType: LocalResource<*>, out CollectionType: LocalCollection<ResourceType>, RemoteType: DavCollection>(
    val context: Context,
    val account: Account,
    val accountSettings: AccountSettings,
    val httpClient: HttpClient,
    val extras: Bundle,
    val authority: String,
    val syncResult: SyncResult,
    val localCollection: CollectionType
) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncManagerEntryPoint {
        fun appDatabase(): AppDatabase
    }

    enum class SyncAlgorithm {
        PROPFIND_REPORT,
        COLLECTION_SYNC
    }

    companion object {
        const val DEBUG_INFO_MAX_RESOURCE_DUMP_SIZE = 100*FileUtils.ONE_KB.toInt()
        const val MAX_MULTIGET_RESOURCES = 10

        var _workDispatcher: WeakReference<CoroutineDispatcher>? = null
        /**
         * We use our own dispatcher to
         *
         *   - make sure that all threads have [Thread.getContextClassLoader] set, which is required for dav4jvm and ical4j (because they rely on [ServiceLoader]),
         *   - control the global number of sync worker threads.
         *
         * Threads created by a service automatically have a contextClassLoader.
         */
        fun getWorkDispatcher(): CoroutineDispatcher {
            val cached = _workDispatcher?.get()
            if (cached != null)
                return cached

            val newDispatcher = ThreadPoolExecutor(
                0, Integer.min(Runtime.getRuntime().availableProcessors(), 4),
                10, TimeUnit.SECONDS, LinkedBlockingQueue()
            ).asCoroutineDispatcher()
            return newDispatcher
        }
    }

    init {
        // required for ServiceLoader -> ical4j -> ical4android
        Ical4Android.checkThreadContextClassLoader()
    }

    protected val mainAccount = if (localCollection is LocalAddressBook)
        localCollection.mainAccount
    else
        account

    protected val notificationManager = NotificationManagerCompat.from(context)
    protected val notificationTag = localCollection.tag

    protected lateinit var collectionURL: HttpUrl
    protected lateinit var davCollection: RemoteType

    protected var hasCollectionSync = false

    val workDispatcher = getWorkDispatcher()


    fun performSync() {
        // dismiss previous error notifications
        notificationManager.cancel(notificationTag, NotificationUtils.NOTIFY_SYNC_ERROR)

        unwrapExceptions({
            Logger.log.info("Preparing synchronization")
            if (!prepare()) {
                Logger.log.info("No reason to synchronize, aborting")
                return@unwrapExceptions
            }

            // log sync time
            val db = EntryPointAccessors.fromApplication(context, SyncManagerEntryPoint::class.java).appDatabase()
            db.runInTransaction {
                db.collectionDao().getByUrl(collectionURL.toString())?.let { collection ->
                    db.syncStatsDao().insertOrReplace(
                        SyncStats(0, collection.id, authority, System.currentTimeMillis())
                    )
                }
            }

            Logger.log.info("Querying server capabilities")
            var remoteSyncState = queryCapabilities()

            Logger.log.info("Processing local deletes/updates")
            val modificationsPresent = processLocallyDeleted() || uploadDirty()

            if (extras.containsKey(SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC)) {
                Logger.log.info("Forcing re-synchronization of all entries")

                // forget sync state of collection (→ initial sync in case of SyncAlgorithm.COLLECTION_SYNC)
                localCollection.lastSyncState = null
                remoteSyncState = null

                // forget sync state of members (→ download all members again and update them locally)
                localCollection.forgetETags()
            }

            if (modificationsPresent || syncRequired(remoteSyncState))
                when (syncAlgorithm()) {
                    SyncAlgorithm.PROPFIND_REPORT -> {
                        Logger.log.info("Sync algorithm: full listing as one result (PROPFIND/REPORT)")
                        resetPresentRemotely()

                        // get current sync state
                        if (modificationsPresent)
                            remoteSyncState = querySyncState()

                        // list and process all entries at current sync state (which may be the same as or newer than remoteSyncState)
                        Logger.log.info("Processing remote entries")
                        syncRemote { callback ->
                            listAllRemote(callback)
                        }

                        Logger.log.info("Deleting entries which are not present remotely anymore")
                        deleteNotPresentRemotely()

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
                            }

                            Logger.log.log(Level.INFO, "Saving sync state", syncState)
                            localCollection.lastSyncState = syncState

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
                // sync was cancelled or account has been removed: re-throw to SyncAdapterService
                is InterruptedException,
                is InterruptedIOException,
                is InvalidAccountException ->
                    throw e

                // specific I/O errors
                is SSLHandshakeException -> {
                    Logger.log.log(Level.WARNING, "SSL handshake failed", e)

                    // when a certificate is rejected by cert4android, the cause will be a CertificateException
                    if (e.cause !is CertificateException)
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


    /**
     * Prepares synchronization. Sets the lateinit properties [collectionURL] and [davCollection].
     *
     * @return whether synchronization shall be performed
     */
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
     * Processes locally deleted entries. This can mean:
     *
     * - forwarding them to the server (HTTP `DELETE`)
     * - resetting their local state so that they will be downloaded again because they're read-only
     *
     * @return whether local resources have been processed so that a synchronization is always necessary
     */
    protected open fun processLocallyDeleted(): Boolean {
        var numDeleted = 0

        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        val localList = localCollection.findDeleted()
        for (local in localList) {
            localExceptionContext(local) {
                val fileName = local.fileName
                if (fileName != null) {
                    val lastScheduleTag = local.scheduleTag
                    val lastETag = if (lastScheduleTag == null) local.eTag else null
                    Logger.log.info("$fileName has been deleted locally -> deleting from server (ETag $lastETag / schedule-tag $lastScheduleTag)")

                    remoteExceptionContext(DavResource(httpClient.okHttpClient, collectionURL.newBuilder().addPathSegment(fileName).build())) { remote ->
                        try {
                            remote.delete(ifETag = lastETag, ifScheduleTag = lastScheduleTag) {}
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
     * Processes locally modified resources to the server. This can mean:
     *
     * - uploading them to the server (HTTP `PUT`)
     * - resetting their local state so that they will be downloaded again because they're read-only
     *
     * @return whether local resources have been processed so that a synchronization is always necessary
     */
    protected open fun uploadDirty(): Boolean {
        var numUploaded = 0

        // upload dirty resources (parallelized)
        runBlocking(workDispatcher) {
            for (local in localCollection.findDirty())
                launch {
                    localExceptionContext(local) {
                        uploadDirty(local)
                        numUploaded++
                    }
                }
        }
        syncResult.stats.numEntries += numUploaded
        Logger.log.info("Sent $numUploaded record(s) to server")
        return numUploaded > 0
    }

    protected fun uploadDirty(local: ResourceType) {
        val existingFileName = local.fileName

        var newFileName: String? = null
        var eTag: String? = null
        var scheduleTag: String? = null
        val readTagsFromResponse: (okhttp3.Response) -> Unit = { response ->
            eTag = GetETag.fromResponse(response)?.eTag
            scheduleTag = ScheduleTag.fromResponse(response)?.scheduleTag
        }

        try {
            if (existingFileName == null) {             // new resource
                newFileName = local.prepareForUpload()

                val uploadUrl = collectionURL.newBuilder().addPathSegment(newFileName).build()
                remoteExceptionContext(DavResource(httpClient.okHttpClient, uploadUrl)) { remote ->
                    Logger.log.info("Uploading new record ${local.id} -> $newFileName")
                    remote.put(generateUpload(local), ifNoneMatch = true, callback = readTagsFromResponse)
                }

            } else /* existingFileName != null */ {     // updated resource
                local.prepareForUpload()

                val uploadUrl = collectionURL.newBuilder().addPathSegment(existingFileName).build()
                remoteExceptionContext(DavResource(httpClient.okHttpClient, uploadUrl)) { remote ->
                    val lastScheduleTag = local.scheduleTag
                    val lastETag = if (lastScheduleTag == null) local.eTag else null
                    Logger.log.info("Uploading modified record ${local.id} -> $existingFileName (ETag=$lastETag, Schedule-Tag=$lastScheduleTag)")
                    remote.put(generateUpload(local), ifETag = lastETag, ifScheduleTag = lastScheduleTag, callback = readTagsFromResponse)
                }
            }
        } catch (e: ContextedException) {
            when (val ex = e.cause) {
                is ForbiddenException -> {
                    // HTTP 403 Forbidden
                    // If and only if the upload failed because of missing permissions, treat it like 412.
                    if (ex.errors.contains(Error.NEED_PRIVILEGES))
                        Logger.log.log(Level.INFO, "Couldn't upload because of missing permissions, ignoring", ex)
                    else
                        throw e
                }
                is NotFoundException, is GoneException -> {
                    // HTTP 404 Not Found (i.e. either original resource or the whole collection is not there anymore)
                    if (local.scheduleTag != null || local.eTag != null) {      // this was an update of a previously existing resource
                        Logger.log.info("Original version of locally modified resource is not there (anymore), trying as fresh upload")
                        if (local.scheduleTag != null)  // contacts don't support scheduleTag, don't try to set it without check
                            local.scheduleTag = null
                        local.eTag = null
                        uploadDirty(local)      // if this fails with 404, too, the collection is gone
                        return
                    } else
                        throw e                 // the collection is probably gone
                }
                is ConflictException -> {
                    // HTTP 409 Conflict
                    // We can't interact with the user to resolve the conflict, so we treat 409 like 412.
                    Logger.log.info("Edit conflict, ignoring")
                }
                is PreconditionFailedException -> {
                    // HTTP 412 Precondition failed: Resource has been modified on the server in the meanwhile.
                    // Ignore this condition so that the resource can be downloaded and reset again.
                    Logger.log.info("Resource has been modified on the server before upload, ignoring")
                }
                else -> throw e
            }
        }

        if (eTag != null)
            Logger.log.fine("Received new ETag=$eTag after uploading")
        else
            Logger.log.fine("Didn't receive new ETag after uploading, setting to null")

        local.clearDirty(newFileName, eTag, scheduleTag)
    }

    /**
     * Generates the request body (iCalendar or vCard) from a local resource.
     *
     * @param resource local resource to generate the body from
     *
     * @return iCalendar or vCard (content + Content-Type) that can be uploaded to the server
     */
    protected abstract fun generateUpload(resource: ResourceType): RequestBody


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
        return when (state?.type) {
            SyncState.Type.SYNC_TOKEN -> {
                val lastKnownToken = localState?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }?.value
                lastKnownToken != state.value
            }
            SyncState.Type.CTAG -> {
                val lastKnownCTag = localState?.takeIf { it.type == SyncState.Type.CTAG }?.value
                lastKnownCTag != state.value
            }
            else -> true
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

    /**
     * Calls a callback to list remote resources. All resources from the returned
     * list are downloaded and processed.
     *
     * @param listRemote function to list remote resources (for instance, all since a certain sync-token)
     */
    protected open fun syncRemote(listRemote: (MultiResponseCallback) -> Unit) {
        // thread-safe sync stats
        val nInserted = AtomicInteger()
        val nUpdated = AtomicInteger()
        val nDeleted = AtomicInteger()
        val nSkipped = AtomicInteger()

        runBlocking {
            // download queue
            val toDownload = LinkedBlockingQueue<HttpUrl>()
            fun download(url: HttpUrl?) {
                if (url != null)
                    toDownload += url

                if (toDownload.size >= MAX_MULTIGET_RESOURCES || url == null) {
                    while (toDownload.size > 0) {
                        val bunch = LinkedList<HttpUrl>()
                        toDownload.drainTo(bunch, MAX_MULTIGET_RESOURCES)
                        launch {
                            downloadRemote(bunch)
                        }
                    }
                }
            }

            withContext(workDispatcher) {    // structured concurrency: blocks until all inner coroutines are finished
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

                        launch {
                            localExceptionContext(localCollection.findByName(name)) { local ->
                                if (local == null) {
                                    Logger.log.info("$name has been added remotely, queueing download")
                                    download(response.href)
                                    nInserted.incrementAndGet()
                                } else {
                                    val localETag = local.eTag
                                    val remoteETag = response[GetETag::class.java]?.eTag
                                            ?: throw DavException("Server didn't provide ETag")
                                    if (localETag == remoteETag) {
                                        Logger.log.info("$name has not been changed on server (ETag still $remoteETag)")
                                        nSkipped.incrementAndGet()
                                    } else {
                                        Logger.log.info("$name has been changed on server (current ETag=$remoteETag, last known ETag=$localETag)")
                                        download(response.href)
                                        nUpdated.incrementAndGet()
                                    }

                                    // mark as remotely present, so that this resource won't be deleted at the end
                                    local.updateFlags(LocalResource.FLAG_REMOTELY_PRESENT)
                                }
                            }
                        }

                    } else if (response.status?.code == HttpURLConnection.HTTP_NOT_FOUND) {
                        // collection sync: resource has been deleted on remote server
                        launch {
                            localExceptionContext(localCollection.findByName(name)) { local ->
                                Logger.log.info("$name has been deleted on server, deleting locally")
                                local?.delete()
                                nDeleted.incrementAndGet()
                            }
                        }
                    }
                }
            }

            // download remaining resources
            download(null)
        }

        // update sync stats
        with(syncResult.stats) {
            numInserts += nInserted.get()
            numUpdates += nUpdated.get()
            numDeletes += nDeleted.get()
            numSkippedEntries += nSkipped.get()
        }
    }

    protected abstract fun listAllRemote(callback: MultiResponseCallback)

    protected open fun listRemoteChanges(syncState: SyncState?, callback: MultiResponseCallback): Pair<SyncToken, Boolean> {
        var furtherResults = false

        val report = davCollection.reportChanges(
                syncState?.takeIf { syncState.type == SyncState.Type.SYNC_TOKEN }?.value,
                false, null,
                GetETag.NAME) { response, relation ->
            when (relation) {
                Response.HrefRelation.SELF ->
                    furtherResults = response.status?.code == 507

                Response.HrefRelation.MEMBER ->
                    callback.onResponse(response, relation)

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

    /**
     * Downloads and processes resources, given as a list of URLs. Will be called with a list
     * of changed/new remote resources.
     *
     * Implementations should not use GET to fetch single resources, but always multi-get, even
     * for single resources for these reasons:
     *
     *   1. GET can only be used without HTTP compression, because it may change the ETag.
     *      multi-get sends the ETag in the XML body, so there's no problem with compression.
     *   2. Some servers are wrongly configured to suppress the ETag header in the response.
     *      With multi-get, the ETag is in the XML body, so it won't be affected by that.
     *   3. If there are two methods to download resources (GET and multi-get), both methods
     *      have to be implemented, tested and maintained. Given that multi-get is required
     *      in any case, it's better to have only one method.
     *   4. For users, it's strange behavior when DAVx5 can download multiple remote changes,
     *      but not a single one (or vice versa). So only one method is more user-friendly.
     *   5. March 2020: iCloud now crashes with HTTP 500 upon CardDAV GET requests.
     */
    protected abstract fun downloadRemote(bunch: List<HttpUrl>)

    /**
     * Locally deletes entries which are
     *   1. not dirty and
     *   2. not marked as [LocalResource.FLAG_REMOTELY_PRESENT].
     *
     * Used together with [resetPresentRemotely] when a full listing has been received from
     * the server to locally delete resources which are not present remotely (anymore).
     */
    protected open fun deleteNotPresentRemotely() {
        val removed = localCollection.removeNotDirtyMarked(0)
        Logger.log.info("Removed $removed local resources which are not present on the server anymore")
        syncResult.stats.numDeletes += removed
    }

    /**
     * Post-processing of synchronized entries, for instance contact group membership operations.
     */
    protected abstract fun postProcess()


    // sync helpers

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
                .setContentIntent(PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setPriority(priority)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
        viewItemAction?.let { builder.addAction(it) }
        builder.addAction(buildRetryAction())

        notificationManager.notify(notificationTag, NotificationUtils.NOTIFY_SYNC_ERROR, builder.build())
    }

    private fun buildDebugInfoIntent(e: Throwable, local: ResourceType?, remote: HttpUrl?) =
        DebugInfoActivity.IntentBuilder(context)
            .withAccount(account)
            .withAuthority(authority)
            .withCause(e)
            .withLocalResource(
                try {
                    local.toString()
                } catch (e: OutOfMemoryError) {
                    // for instance because of a huge contact photo; maybe we're lucky and can fetch it
                    null
                }
            )
            .withRemoteResource(remote)
            .build()

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
                PendingIntent.getService(context, 0, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
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
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else
            null
    }

    protected fun notifyInvalidResource(e: Throwable, fileName: String) {
        val intent = buildDebugInfoIntent(e, null, collectionURL.resolve(fileName))

        val builder = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_WARNINGS)
        builder .setSmallIcon(R.drawable.ic_warning_notify)
                .setContentTitle(notifyInvalidResourceTitle())
                .setContentText(context.getString(R.string.sync_invalid_resources_ignoring))
                .setSubText(mainAccount.name)
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .priority = NotificationCompat.PRIORITY_LOW
        notificationManager.notify(notificationTag, NotificationUtils.NOTIFY_INVALID_RESOURCE, builder.build())
    }

    protected abstract fun notifyInvalidResourceTitle(): String

    protected fun<T: ResourceType?, R> localExceptionContext(local: T, body: (T) -> R): R {
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

    protected fun<T: DavResource, R> remoteExceptionContext(remote: T, body: (T) -> R): R {
        try {
            return body(remote)
        } catch (e: ContextedException) {
            e.addContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.location)
            throw e
        } catch(e: Throwable) {
            throw ContextedException(e).setContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.location)
        }
    }

    protected fun<T> responseExceptionContext(remote: Response, body: (Response) -> T): T {
        try {
            return body(remote)
        } catch (e: ContextedException) {
            e.addContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.href)
            throw e
        } catch (e: Throwable) {
            throw ContextedException(e).setContextValue(Constants.EXCEPTION_CONTEXT_REMOTE_RESOURCE, remote.href)
        }
    }

    protected fun<R> remoteExceptionContext(body: (RemoteType) -> R) =
            remoteExceptionContext(davCollection, body)

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