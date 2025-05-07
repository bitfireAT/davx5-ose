/*
 * Copyright 2023 All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.ConflictException
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.ForbiddenException
import at.bitfire.dav4jvm.exception.GoneException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.NotFoundException
import at.bitfire.dav4jvm.exception.PreconditionFailedException
import at.bitfire.dav4jvm.exception.ServiceUnavailableException
import at.bitfire.dav4jvm.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.sync.SyncManager.Companion.DELAY_UNTIL_DEFAULT
import at.bitfire.davdroid.sync.SyncManager.Companion.DELAY_UNTIL_MAX
import at.bitfire.davdroid.sync.SyncManager.Companion.DELAY_UNTIL_MIN
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.Ical4Android
import at.bitfire.vcard4android.ContactsStorageException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.RequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.security.cert.CertificateException
import java.time.Instant
import java.util.LinkedList
import java.util.concurrent.LinkedBlockingQueue
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException

/**
 * Synchronizes a local collection with a remote collection.
 *
 * @param ResourceType      type of local resources
 * @param CollectionType    type of local collection
 * @param RemoteType        type of remote collection
 *
 * @param account           account to synchronize
 * @param httpClient        HTTP client to use for network requests, already authenticated with credentials from [account]
 * @param extras            additional sync parameters
 * @param authority         authority of the content provider the collection shall be synchronized with
 * @param syncResult        receiver for result of the synchronization (will be updated by [performSync])
 * @param localCollection   local collection to synchronize (interface to content provider)
 * @param collection        collection info in the database
 */
abstract class SyncManager<ResourceType: LocalResource<*>, out CollectionType: LocalCollection<ResourceType>, RemoteType: DavCollection>(
    val account: Account,
    val httpClient: HttpClient,
    val extras: Array<String>,
    val authority: String,
    val syncResult: SyncResult,
    val localCollection: CollectionType,
    val collection: Collection
) {

    enum class SyncAlgorithm {
        PROPFIND_REPORT,
        COLLECTION_SYNC
    }

    companion object {

        /** Maximum number of resources that are requested with one multiget request. */
        const val MAX_MULTIGET_RESOURCES = 10

        const val DELAY_UNTIL_DEFAULT = 15*60L      // 15 min
        const val DELAY_UNTIL_MIN =      1*60L      // 1 min
        const val DELAY_UNTIL_MAX =     2*60*60L    // 2 hours

        /**
         * Returns appropriate sync retry delay in seconds, considering the servers suggestion
         * ([DELAY_UNTIL_DEFAULT] if no server suggestion).
         *
         * Takes current time into account to calculate intervals. Interval
         * will be restricted to values between [DELAY_UNTIL_MIN] and [DELAY_UNTIL_MAX].
         *
         * @param retryAfter   optional server suggestion on how long to wait before retrying
         * @return until when to wait before sync can be retried
         */
        fun getDelayUntil(retryAfter: Instant?): Instant {
            val now = Instant.now()

            if (retryAfter == null)
                return now.plusSeconds(DELAY_UNTIL_DEFAULT)

            // take server suggestion, but restricted to plausible min/max values
            val min = now.plusSeconds(DELAY_UNTIL_MIN)
            val max = now.plusSeconds(DELAY_UNTIL_MAX)
            return when {
                min > retryAfter -> min
                max < retryAfter -> max
                else -> retryAfter
            }
        }

    }


    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var syncStatsRepository: DavSyncStatsRepository

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var syncNotificationManagerFactory: SyncNotificationManager.Factory


    init {
        // required for ServiceLoader -> ical4j -> ical4android
        Ical4Android.checkThreadContextClassLoader()
    }

    protected lateinit var davCollection: RemoteType

    protected var hasCollectionSync = false

    private val syncNotificationManager by lazy {
        syncNotificationManagerFactory.create(account)
    }

    /**
     * Push-Dont-Notify header for PROPFIND requests.
     */
    private val pushDontNotifyHeader by lazy {
        collection.pushSubscription?.takeIf {
            collection.subscriptionActive()
        }?.let { activePushSubscription ->
            mapOf("Push-Dont-Notify" to activePushSubscription)
        }
    }

    fun performSync() {
        // dismiss previous error notifications
        syncNotificationManager.dismissInvalidResource(localCollectionTag = localCollection.tag)


        try {
            logger.info("Preparing synchronization")
            if (!prepare()) {
                logger.info("No reason to synchronize, aborting")
                return
            }
            syncStatsRepository.logSyncTimeBlocking(collection.id, authority)

            logger.info("Querying server capabilities")
            var remoteSyncState = queryCapabilities()

            logger.info("Processing local deletes/updates")
            val modificationsPresent =
                processLocallyDeleted() or uploadDirty()     // bitwise OR guarantees that both expressions are evaluated

            if (extras.contains(Syncer.SYNC_EXTRAS_FULL_RESYNC)) {
                logger.info("Forcing re-synchronization of all entries")

                // forget sync state of collection (→ initial sync in case of SyncAlgorithm.COLLECTION_SYNC)
                localCollection.lastSyncState = null
                remoteSyncState = null

                // forget sync state of members (→ download all members again and update them locally)
                localCollection.forgetETags()
            }

            if (modificationsPresent || syncRequired(remoteSyncState))
                when (syncAlgorithm()) {
                    SyncAlgorithm.PROPFIND_REPORT -> {
                        logger.info("Sync algorithm: full listing as one result (PROPFIND/REPORT)")
                        resetPresentRemotely()

                        // get current sync state
                        if (modificationsPresent)
                            remoteSyncState = querySyncState()

                        // list and process all entries at current sync state (which may be the same as or newer than remoteSyncState)
                        logger.info("Processing remote entries")
                        syncRemote { callback ->
                            listAllRemote(callback)
                        }

                        logger.info("Deleting entries which are not present remotely anymore")
                        deleteNotPresentRemotely()

                        logger.info("Post-processing")
                        postProcess()

                        logger.log(Level.INFO, "Saving sync state", remoteSyncState)
                        localCollection.lastSyncState = remoteSyncState
                    }

                    SyncAlgorithm.COLLECTION_SYNC -> {
                        var syncState = localCollection.lastSyncState?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }

                        var initialSync = false
                        if (syncState == null) {
                            logger.info("Starting initial sync")
                            initialSync = true
                            resetPresentRemotely()
                        } else if (syncState.initialSync == true) {
                            logger.info("Continuing initial sync")
                            initialSync = true
                        }

                        var furtherChanges = false
                        do {
                            logger.info("Listing changes since $syncState")
                            syncRemote { callback ->
                                try {
                                    val result = listRemoteChanges(syncState, callback)
                                    syncState = SyncState.fromSyncToken(result.first, initialSync)
                                    furtherChanges = result.second
                                } catch (e: HttpException) {
                                    if (e.errors.contains(Error.VALID_SYNC_TOKEN)) {
                                        logger.info("Sync token invalid, performing initial sync")
                                        initialSync = true
                                        resetPresentRemotely()

                                        val result = listRemoteChanges(null, callback)
                                        syncState = SyncState.fromSyncToken(result.first, initialSync)
                                        furtherChanges = result.second
                                    } else
                                        throw e
                                }
                            }

                            logger.log(Level.INFO, "Saving sync state", syncState)
                            localCollection.lastSyncState = syncState

                            logger.info("Server has further changes: $furtherChanges")
                        } while (furtherChanges)

                        if (initialSync) {
                            // initial sync is finished, remove all local resources which have not been listed by server
                            logger.info("Deleting local resources which are not on server (anymore)")
                            deleteNotPresentRemotely()

                            // remove initial sync flag
                            syncState!!.initialSync = false
                            logger.log(Level.INFO, "Initial sync completed, saving sync state", syncState)
                            localCollection.lastSyncState = syncState
                        }

                        logger.info("Post-processing")
                        postProcess()
                    }
                }
            else
                logger.info("Remote collection didn't change, no reason to sync")

        } catch (potentiallyWrappedException: Throwable) {
            var local: LocalResource<*>? = null
            var remote: HttpUrl? = null

            val e = SyncException.unwrap(potentiallyWrappedException) {
                local = it.localResource
                remote = it.remoteResource
            }

            when (e) {
                // DeadObjectException (may occur when syncing takes too long and process is demoted to cached):
                // re-throw to base Syncer → will cause soft error and restart the sync process
                is DeadObjectException ->
                    throw e

                // sync was cancelled or account has been removed: re-throw to BaseSyncer
                is InterruptedException,
                is InterruptedIOException,
                is InvalidAccountException ->
                    throw e

                // specific I/O errors
                is SSLHandshakeException -> {
                    logger.log(Level.WARNING, "SSL handshake failed", e)

                    // when a certificate is rejected by cert4android, the cause will be a CertificateException
                    if (e.cause !is CertificateException)
                        handleException(e, local, remote)
                }

                // specific HTTP errors
                is ServiceUnavailableException -> {
                    logger.log(Level.WARNING, "Got 503 Service unavailable, trying again later", e)
                    // determine when to retry
                    syncResult.delayUntil = getDelayUntil(e.retryAfter).epochSecond
                    syncResult.numServiceUnavailableExceptions++ // Indicate a soft error occurred
                }

                // all others
                else ->
                    handleException(e, local, remote)
            }
        }
    }


    /**
     * Prepares synchronization. Sets the lateinit property [davCollection].
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
            SyncException.wrapWithLocalResource(local) {
                val fileName = local.fileName
                if (fileName != null) {
                    val lastScheduleTag = local.scheduleTag
                    val lastETag = if (lastScheduleTag == null) local.eTag else null
                    logger.info("$fileName has been deleted locally -> deleting from server (ETag $lastETag / schedule-tag $lastScheduleTag)")

                    val url = collection.url.newBuilder().addPathSegment(fileName).build()
                    val remote = DavResource(httpClient.okHttpClient, url)
                    SyncException.wrapWithRemoteResource(url) {
                        try {
                            remote.delete(
                                ifETag = lastETag,
                                ifScheduleTag = lastScheduleTag,
                                headers = pushDontNotifyHeader ?: emptyMap(),
                            ) {}
                            numDeleted++
                        } catch (_: HttpException) {
                            logger.warning("Couldn't delete $fileName from server; ignoring (may be downloaded again)")
                        }
                    }
                } else
                    logger.info("Removing local record #${local.id} which has been deleted locally and was never uploaded")
                local.delete()
            }
        }
        logger.info("Removed $numDeleted record(s) from server")
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
        runBlocking {
            for (local in localCollection.findDirty())
                launch {
                    SyncException.wrapWithLocalResource(local) {
                        uploadDirty(local)
                        numUploaded++
                    }
                }
        }
        logger.info("Sent $numUploaded record(s) to server")
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

                val uploadUrl = collection.url.newBuilder().addPathSegment(newFileName).build()
                val remote = DavResource(httpClient.okHttpClient, uploadUrl)
                SyncException.wrapWithRemoteResource(uploadUrl) {
                    logger.info("Uploading new record ${local.id} -> $newFileName")
                    remote.put(
                        generateUpload(local),
                        ifNoneMatch = true,
                        callback = readTagsFromResponse,
                        headers = pushDontNotifyHeader ?: emptyMap()
                    )
                }

            } else /* existingFileName != null */ {     // updated resource
                local.prepareForUpload()

                val uploadUrl = collection.url.newBuilder().addPathSegment(existingFileName).build()
                val remote = DavResource(httpClient.okHttpClient, uploadUrl)
                SyncException.wrapWithRemoteResource(uploadUrl) {
                    val lastScheduleTag = local.scheduleTag
                    val lastETag = if (lastScheduleTag == null) local.eTag else null
                    logger.info("Uploading modified record ${local.id} -> $existingFileName (ETag=$lastETag, Schedule-Tag=$lastScheduleTag)")
                    remote.put(
                        generateUpload(local),
                        ifETag = lastETag,
                        ifScheduleTag = lastScheduleTag,
                        callback = readTagsFromResponse,
                        headers = pushDontNotifyHeader ?: emptyMap()
                    )
                }
            }
        } catch (e: SyncException) {
            when (val ex = e.cause) {
                is ForbiddenException -> {
                    // HTTP 403 Forbidden
                    // If and only if the upload failed because of missing permissions, treat it like 412.
                    if (ex.errors.contains(Error.NEED_PRIVILEGES))
                        logger.log(Level.INFO, "Couldn't upload because of missing permissions, ignoring", ex)
                    else
                        throw e
                }
                is NotFoundException, is GoneException -> {
                    // HTTP 404 Not Found (i.e. either original resource or the whole collection is not there anymore)
                    if (local.scheduleTag != null || local.eTag != null) {      // this was an update of a previously existing resource
                        logger.info("Original version of locally modified resource is not there (anymore), trying as fresh upload")
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
                    logger.info("Edit conflict, ignoring")
                }
                is PreconditionFailedException -> {
                    // HTTP 412 Precondition failed: Resource has been modified on the server in the meanwhile.
                    // Ignore this condition so that the resource can be downloaded and reset again.
                    logger.info("Resource has been modified on the server before upload, ignoring")
                }
                else -> throw e
            }
        }

        if (eTag != null)
            logger.fine("Received new ETag=$eTag after uploading")
        else
            logger.fine("Didn't receive new ETag after uploading, setting to null")

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
     * Will return _true_ if [Syncer.SYNC_EXTRAS_RESYNC] and/or
     * [Syncer.SYNC_EXTRAS_FULL_RESYNC] is set in [extras].
     *
     * @param state remote sync state to compare local sync state with
     *
     * @return whether data has been changed on the server, i.e. whether running the
     * sync algorithm is required
     */
    protected open fun syncRequired(state: SyncState?): Boolean {
        if (extras.contains(Syncer.SYNC_EXTRAS_RESYNC) ||
            extras.contains(Syncer.SYNC_EXTRAS_FULL_RESYNC))
            return true

        val localState = localCollection.lastSyncState
        logger.info("Local sync state = $localState, remote sync state = $state")
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
        logger.info("Number of local non-dirty entries: $number")
    }

    /**
     * Calls a callback to list remote resources. All resources from the returned
     * list are downloaded and processed.
     *
     * @param listRemote function to list remote resources (for instance, all since a certain sync-token)
     */
    protected open fun syncRemote(listRemote: (MultiResponseCallback) -> Unit) {
        runBlocking {
            // download queue
            val toDownload = LinkedBlockingQueue<HttpUrl>()
            fun download(url: HttpUrl?) {
                if (url != null)
                    toDownload += url

                if (toDownload.size >= MAX_MULTIGET_RESOURCES || url == null) {
                    while (toDownload.isNotEmpty()) {
                        val bunch = LinkedList<HttpUrl>()
                        toDownload.drainTo(bunch, MAX_MULTIGET_RESOURCES)
                        launch {
                            downloadRemote(bunch)
                        }
                    }
                }
            }

            coroutineScope {    // structured concurrency: blocks until all inner coroutines are finished
                listRemote { response, relation ->
                    // ignore non-members
                    if (relation != Response.HrefRelation.MEMBER)
                        return@listRemote

                    // ignore collections
                    if (response[at.bitfire.dav4jvm.property.webdav.ResourceType::class.java]?.types?.contains(at.bitfire.dav4jvm.property.webdav.ResourceType.COLLECTION) == true)
                        return@listRemote

                    val name = response.hrefName()

                    if (response.isSuccess()) {
                        logger.fine("Found remote resource: $name")

                        launch {
                            val local = localCollection.findByName(name)
                            SyncException.wrapWithLocalResource(local) {
                                if (local == null) {
                                    logger.info("$name has been added remotely, queueing download")
                                    download(response.href)
                                } else {
                                    val localETag = local.eTag
                                    val remoteETag = response[GetETag::class.java]?.eTag
                                        ?: throw DavException("Server didn't provide ETag")
                                    if (localETag == remoteETag) {
                                        logger.info("$name has not been changed on server (ETag still $remoteETag)")
                                    } else {
                                        logger.info("$name has been changed on server (current ETag=$remoteETag, last known ETag=$localETag)")
                                        download(response.href)
                                    }

                                    // mark as remotely present, so that this resource won't be deleted at the end
                                    local.updateFlags(LocalResource.FLAG_REMOTELY_PRESENT)
                                }
                            }
                        }

                    } else if (response.status?.code == HttpURLConnection.HTTP_NOT_FOUND) {
                        // collection sync: resource has been deleted on remote server
                        launch {
                            localCollection.findByName(name)?.let { local ->
                                SyncException.wrapWithLocalResource(local) {
                                    logger.info("$name has been deleted on server, deleting locally")
                                    local.delete()
                                }
                            }
                        }
                    }
                }
            }

            // download remaining resources
            download(null)
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
                    logger.fine("Unexpected sync-collection response: $response")
            }
        }

        var syncToken: SyncToken? = null
        report.filterIsInstance<SyncToken>().firstOrNull()?.let {
            syncToken = it
        }
        if (syncToken == null)
            throw DavException("Received sync-collection response without sync-token")

        return Pair(syncToken, furtherResults)
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
        logger.info("Removed $removed local resources which are not present on the server anymore")
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

    /**
     * Logs the exception, updates sync result and shows a notification to the user.
     */
    private fun handleException(e: Throwable, local: LocalResource<*>?, remote: HttpUrl?) {
        var message: String
        when (e) {
            is IOException -> {
                logger.log(Level.WARNING, "I/O error", e)
                syncResult.numIoExceptions++
                message = context.getString(R.string.sync_error_io, e.localizedMessage)
            }

            is UnauthorizedException -> {
                logger.log(Level.SEVERE, "Not authorized anymore", e)
                syncResult.numAuthExceptions++
                message = context.getString(R.string.sync_error_authentication_failed)
            }

            is HttpException, is DavException -> {
                logger.log(Level.SEVERE, "HTTP/DAV exception", e)
                syncResult.numHttpExceptions++
                message = context.getString(R.string.sync_error_http_dav, e.localizedMessage)
            }

            is CalendarStorageException, is ContactsStorageException, is RemoteException -> {
                logger.log(Level.SEVERE, "Couldn't access local storage", e)
                syncResult.localStorageError = true
                message = context.getString(R.string.sync_error_local_storage, e.localizedMessage)
            }

            else -> {
                logger.log(Level.SEVERE, "Unclassified sync error", e)
                syncResult.numUnclassifiedErrors++
                message = e.localizedMessage ?: e::class.java.simpleName
            }
        }

        syncNotificationManager.notifyException(
            authority,
            localCollection.tag,
            message,
            localCollection,
            e,
            local,
            remote
        )
    }

    protected fun notifyInvalidResource(e: Throwable, fileName: String) =
        syncNotificationManager.notifyInvalidResource(
            authority,
            localCollection.tag,
            collection,
            e,
            fileName,
            notifyInvalidResourceTitle()
        )

    protected abstract fun notifyInvalidResourceTitle(): String

}