/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.exception.ConflictException
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.ForbiddenException
import at.bitfire.dav4jvm.ktor.exception.GoneException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.ktor.exception.NotFoundException
import at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException
import at.bitfire.dav4jvm.ktor.exception.ServiceUnavailableException
import at.bitfire.dav4jvm.ktor.exception.UnauthorizedException
import at.bitfire.dav4jvm.ktor.responsesWithRelation
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.storage.LocalStorageException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headers
import io.ktor.util.appendAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.cert.CertificateException
import java.util.Optional
import java.util.concurrent.CancellationException
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException

/**
 * Synchronizes a local collection with a remote collection.
 *
 * @param LocalType         type of local resources
 * @param CollectionType    type of local collection
 * @param RemoteType        type of remote collection
 *
 * @param account           account to synchronize
 * @param httpClient        HTTP client to use for network requests, already authenticated with credentials from [account]
 * @param dataType          data type to synchronize
 * @param syncResult        receiver for result of the synchronization (will be updated by [performSync])
 * @param localCollection   local collection to synchronize (interface to content provider)
 * @param collection        collection info in the database
 * @param resync            whether re-synchronization is requested
 */
abstract class SyncManager<LocalType : LocalResource, out CollectionType : LocalCollection<LocalType>, RemoteType : DavCollection>(
    val account: Account,
    val httpClient: HttpClient,
    val dataType: SyncDataType,
    val syncResult: SyncResult,
    val localCollection: CollectionType,
    val collection: Collection,
    val resync: ResyncType?,
    val ioDispatcher: CoroutineDispatcher,
    val syncTransferSemaphore: Semaphore
) {

    enum class SyncAlgorithm {
        PROPFIND_REPORT,
        COLLECTION_SYNC
    }


    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var readOnlyPolicy: ReadOnlyPolicy

    @Inject
    lateinit var syncStatsRepository: DavSyncStatsRepository

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var syncNotificationManagerFactory: SyncNotificationManager.Factory


    protected lateinit var davCollection: RemoteType

    protected var hasCollectionSync = false

    private val syncNotificationManager by lazy {
        syncNotificationManagerFactory.create(account)
    }

    /**
     * Push-Dont-Notify header, added to PUT and DELETE requests if subscription exists.
     */
    private val pushDontNotifyHeader by lazy {
        collection.pushSubscription?.let { pushSubscription ->
            mapOf("Push-Dont-Notify" to QuotedStringUtils.asQuotedString(pushSubscription))
        } ?: emptyMap()
    }

    suspend fun performSync() = withContext(ioDispatcher) {
        // dismiss previous error notifications
        syncNotificationManager.dismissCollectionError(localCollectionTag = localCollection.tag)

        try {
            logger.info("Preparing synchronization")
            if (!prepare()) {
                logger.info("No reason to synchronize, aborting")
                return@withContext
            }
            syncStatsRepository.logSyncTime(collection.id, dataType)

            logger.info("Querying server capabilities")
            var remoteSyncState = queryCapabilities()

            logger.info("Processing local deletes/updates")
            val modificationsPresent =
                processLocallyDeleted() or uploadDirty()     // bitwise OR guarantees that both expressions are evaluated

            if (resync == ResyncType.RESYNC_ENTRIES) {
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
                        syncRemote { listAllRemote() }

                        logger.info("Deleting entries which are not present remotely anymore")
                        deleteNotPresentRemotely()

                        logger.info("Post-processing")
                        postProcess()

                        logger.info("Saving sync state: $remoteSyncState")
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
                            try {
                                val result = listRemoteChangesAndSync(syncState)
                                syncState = SyncState.fromSyncToken(result.first, initialSync)
                                furtherChanges = result.second
                            } catch (e: HttpException) {
                                if (e.errors.contains(Error(WebDAV.ValidSyncToken))) {
                                    logger.info("Sync token invalid, performing initial sync")
                                    initialSync = true
                                    resetPresentRemotely()

                                    val result = listRemoteChangesAndSync(null)
                                    syncState = SyncState.fromSyncToken(result.first, initialSync)
                                    furtherChanges = result.second
                                } else
                                    throw e
                            }

                            logger.info("Saving sync state: $syncState")
                            localCollection.lastSyncState = syncState

                            logger.info("Server has further changes: $furtherChanges")
                        } while (furtherChanges)

                        if (initialSync) {
                            // initial sync is finished, remove all local resources which have not been listed by server
                            logger.info("Deleting local resources which are not on server (anymore)")
                            deleteNotPresentRemotely()

                            // remove initial sync flag
                            syncState!!.initialSync = false
                            logger.info("Initial sync completed, saving sync state: $syncState")
                            localCollection.lastSyncState = syncState
                        }

                        logger.info("Post-processing")
                        postProcess()
                    }
                }
            else
                logger.info("Remote collection didn't change, no reason to sync")

        } catch (potentiallyWrappedException: Throwable) {
            var local: LocalResource? = null
            var remote: Url? = null

            val e = SyncException.unwrap(potentiallyWrappedException) {
                local = it.localResource
                remote = it.remoteResource
            }

            when (e) {
                /* LocalStorageException with cause DeadObjectException may occur when syncing takes too long
                and process is demoted to cached. In this case, we re-throw to the base Syncer which will
                treat it as a soft error and re-schedule the sync process. */
                is LocalStorageException if e.cause is DeadObjectException ->
                    throw e

                // sync was cancelled or account has been removed: re-throw to Syncer
                is CancellationException,
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
                    syncResult.delayUntil = e.getDelayUntil().epochSecond
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
    protected abstract suspend fun prepare(): Boolean

    /**
     * Queries the server for synchronization capabilities like specific report types,
     * data formats etc.
     *
     * Should also query and save the initial sync state (e.g. CTag/sync-token).
     *
     * @return current sync state
     */
    protected abstract suspend fun queryCapabilities(): SyncState?

    /**
     * Processes locally deleted entries. This can mean:
     *
     * - forwarding them to the server (HTTP `DELETE`)
     * - resetting their local state so that they will be downloaded again because they're read-only
     *
     * @return whether local resources have been processed so that a synchronization is always necessary
     */
    protected open suspend fun processLocallyDeleted(): Boolean {
        if (localCollection.readOnly)
            return readOnlyPolicy.resetDeleted(localCollection)

        var numDeleted = 0

        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        localCollection.findDeleted().collect { local ->
            SyncException.wrapWithLocalResource(local) {
                val fileName = local.fileName
                if (fileName != null) {
                    val lastScheduleTag = local.scheduleTag
                    val lastETag = if (lastScheduleTag == null) local.eTag else null
                    logger.info("$fileName has been deleted locally -> deleting from server (ETag $lastETag / schedule-tag $lastScheduleTag)")

                    val url = URLBuilder(collection.url).appendPathSegments(fileName, encodeSlash = true).build()
                    val remote = DavResource(httpClient, url)
                    SyncException.wrapWithRemoteResource(url) {
                        try {
                            remote.delete(
                                ifETag = lastETag,
                                ifScheduleTag = lastScheduleTag,
                                headers = pushDontNotifyHeader,
                            ) {}
                            numDeleted++
                        } catch (_: HttpException) {
                            logger.warning("Couldn't delete $fileName from server; ignoring (may be downloaded again)")
                        }
                    }
                } else
                    logger.log(
                        Level.INFO,
                        "Removing local record #{0} which has been deleted locally and was never uploaded",
                        arrayOf(local.id)
                    )
                local.deleteLocal()
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
    protected open suspend fun uploadDirty(): Boolean {
        if (localCollection.readOnly)
            return readOnlyPolicy.resetDirty(localCollection)

        var numUploaded = 0

        localCollection.findDirty().collect { local ->
            SyncException.wrapWithLocalResource(local) {
                uploadDirty(local)
                numUploaded++
            }
        }

        logger.info("Sent $numUploaded record(s) to server")
        return numUploaded > 0
    }

    /**
     * Uploads a dirty local resource.
     *
     * @param local         resource to upload
     * @param forceAsNew    whether the ETag (and Schedule-Tag) of [local] are ignored and the resource
     *                      is created as a new resource on the server
     */
    protected open suspend fun uploadDirty(local: LocalType, forceAsNew: Boolean = false) {
        val existingFileName = local.fileName

        val upload = generateUpload(local)

        val fileName = existingFileName ?: upload.suggestedFileName
        val uploadUrl = URLBuilder(collection.url).appendPathSegments(fileName, encodeSlash = true).build()
        val remote = DavResource(httpClient, uploadUrl)

        try {
            SyncException.wrapWithRemoteResource(uploadUrl) {
                if (existingFileName == null || forceAsNew) {
                    // create new resource on server
                    logger.log(Level.INFO, "Uploading new resource {0} -> {1}", arrayOf<Any?>(local.id, fileName))

                    var newETag: String? = null
                    var newScheduleTag: String? = null
                    remote.put(
                        upload.content,
                        ifNoneMatch = true,     // fails if there's already a resource with that name
                        callback = { response ->
                            newETag = GetETag.fromHttpResponse(response)?.eTag
                            newScheduleTag = ScheduleTag.fromHttpResponse(response)?.scheduleTag
                        },
                        headers = pushDontNotifyHeader
                    )

                    // success (no exception thrown)
                    onSuccessfulUpload(local, fileName, newETag, newScheduleTag, upload.onSuccessContext)

                } else {
                    // update resource on server
                    val ifScheduleTag = local.scheduleTag
                    val ifETag = if (ifScheduleTag == null) local.eTag else null

                    logger.log(
                        Level.INFO,
                        "Uploading modified resource {0} -> {1} (if ETag={2} / Schedule-Tag={3})",
                        arrayOf<Any?>(local.id, fileName, ifETag, ifScheduleTag)
                    )

                    var updatedETag: String? = null
                    var updatedScheduleTag: String? = null
                    remote.put(
                        upload.content,
                        ifETag = ifETag,
                        ifScheduleTag = ifScheduleTag,
                        callback = { response ->
                            updatedETag = GetETag.fromHttpResponse(response)?.eTag
                            updatedScheduleTag = ScheduleTag.fromHttpResponse(response)?.scheduleTag
                        },
                        headers = pushDontNotifyHeader
                    )

                    // success (no exception thrown)
                    onSuccessfulUpload(local, fileName, updatedETag, updatedScheduleTag, upload.onSuccessContext)
                }
            }

        } catch (e: SyncException) {
            when (val ex = e.cause) {
                is ForbiddenException -> {
                    // HTTP 403 Forbidden
                    // If and only if the upload failed because of missing permissions, treat it like 412.
                    if (ex.errors.contains(Error(WebDAV.NeedPrivileges)))
                        logger.log(Level.INFO, "Couldn't upload because of missing permissions, ignoring", ex)
                    else
                        throw e
                }
                is NotFoundException, is GoneException -> {
                    // HTTP 404 Not Found (i.e. either original resource or the whole collection is not there anymore)
                    if (!forceAsNew) {      // first try; if this fails with 404, too, the collection is gone
                        logger.info("Original version of locally modified resource is not there (anymore), trying as fresh upload")
                        uploadDirty(local, forceAsNew = true)
                        return
                    } else {
                        // we tried with forceAsNew, collection probably gone
                        throw e
                    }
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
    }

    /**
     * Generates the request body (iCalendar or vCard) from a local resource.
     *
     * @param resource local resource to generate the body from
     *
     * @return iCalendar or vCard (content + Content-Type) that can be uploaded to the server
     */
    @VisibleForTesting
    internal abstract fun generateUpload(resource: LocalType): GeneratedResource

    /**
     * Called after a successful upload (either of a new or an updated resource) so that the local
     * _dirty_ state can be reset. Also updates some other local properties.
     *
     * @param local         local resource that has been uploaded successfully
     * @param newFileName   file name that has been used for uploading
     * @param eTag          resulting `ETag` of the upload (from the server)
     * @param scheduleTag   resulting `Schedule-Tag` of the upload (from the server)
     * @param context       properties that have been generated before the upload and that shall be persisted by this method
     */
    private fun onSuccessfulUpload(
        local: LocalType,
        newFileName: String,
        eTag: String?,
        scheduleTag: String?,
        context: GeneratedResource.OnSuccessContext?
    ) {
        logger.fine("Upload successful: file=$newFileName, ETag=$eTag, Schedule-Tag=$scheduleTag, context=$context")

        // update SEQUENCE, if necessary
        if (context?.sequence != null)
            local.updateSequence(context.sequence)

        // clear dirty flag and update ETag/Schedule-Tag
        local.clearDirty(Optional.of(newFileName), eTag, scheduleTag)
    }


    /**
     * Determines whether a sync is required because there were changes on the server.
     * For instance, this method can compare the collection's `CTag`/`sync-token` with
     * the last known local value.
     *
     * When local changes have been uploaded ([processLocallyDeleted] and/or
     * [uploadDirty] were true), a sync is always required and this method
     * should *not* be evaluated.
     *
     * Will return _true_ if [resync] is non-null and thus indicates re-synchronization.
     *
     * @param state remote sync state to compare local sync state with
     *
     * @return whether data has been changed on the server, i.e. whether running the
     * sync algorithm is required
     */
    protected open fun syncRequired(state: SyncState?): Boolean {
        if (resync != null)
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
     * Calls a function to list remote resources. All resources from the returned
     * flow are downloaded and processed.
     *
     * @param listRemote function to list remote resources (for instance, all since a certain sync-token)
     */
    protected open suspend fun syncRemote(listRemote: () -> Flow<MultiStatusItem>) =
        coroutineScope {    // structured concurrency
            val batchDownloader = BatchDownloader { batch ->
                launch {
                    syncTransferSemaphore.withPermit {
                        downloadRemote(batch)
                    }
                }
            }

            coroutineScope {    // structured concurrency
                listRemote().responsesWithRelation().collect { (response, relation) ->
                    // ignore non-members
                    if (relation != Response.HrefRelation.MEMBER)
                        return@collect

                    // ignore collections
                    if (response[ResourceType::class.java]?.types?.contains(WebDAV.Collection) == true)
                        return@collect

                    val name = response.hrefName()

                    if (response.isSuccess()) {
                        logger.fine("Found remote resource: $name")

                        launch {
                            val local = localCollection.findByName(name)
                            SyncException.wrapWithLocalResource(local) {
                                if (local == null) {
                                    logger.info("$name has been added remotely, queueing download")
                                    batchDownloader.enqueue(response.href)
                                } else {
                                    val localETag = local.eTag
                                    val remoteETag = response[GetETag::class.java]?.eTag
                                        ?: throw DavException("Server didn't provide ETag")
                                    if (localETag == remoteETag) {
                                        logger.info("$name has not been changed on server (ETag still $remoteETag)")
                                    } else {
                                        logger.info("$name has been changed on server (current ETag=$remoteETag, last known ETag=$localETag)")
                                        batchDownloader.enqueue(response.href)
                                    }

                                    // mark as remotely present, so that this resource won't be deleted at the end
                                    local.updateFlags(LocalResource.FLAG_REMOTELY_PRESENT)
                                }
                            }
                        }

                    } else if (response.status == HttpStatusCode.NotFound) {
                        // collection sync: resource has been deleted on remote server
                        launch {
                            localCollection.findByName(name)?.let { local ->
                                SyncException.wrapWithLocalResource(local) {
                                    logger.info("$name has been deleted on server, deleting locally")
                                    local.deleteLocal()
                                }
                            }
                        }
                    }
                }
            }

            // download remaining resources
            batchDownloader.flush()
        }

    protected abstract fun listAllRemote(): Flow<MultiStatusItem>

    protected open suspend fun listRemoteChangesAndSync(syncState: SyncState?): Pair<SyncToken, Boolean> {
        var furtherResults = false
        var syncToken: SyncToken? = null

        syncRemote {
            davCollection.reportChanges(
                syncState?.takeIf { syncState.type == SyncState.Type.SYNC_TOKEN }?.value,
                false, null,
                WebDAV.GetETag
            ).onEach { item ->
                when (item) {
                    is MultiStatusItem.Response ->
                        when (item.relation) {
                            Response.HrefRelation.SELF ->
                                furtherResults = item.response.status == HttpStatusCode.InsufficientStorage
                            Response.HrefRelation.MEMBER -> {}   // handled by syncRemote itself
                            else ->
                                logger.fine("Unexpected sync-collection response: ${item.response}")
                        }
                    is MultiStatusItem.ExtraProperty ->
                        (item.property as? SyncToken)?.let { syncToken = it }
                }
            }
        }

        val token = syncToken ?: throw DavException("Received sync-collection response without sync-token")
        return Pair(token, furtherResults)
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
    protected abstract suspend fun downloadRemote(bunch: List<Url>)

    /**
     * Locally deletes entries which are
     *   1. not dirty and
     *   2. not marked as [LocalResource.FLAG_REMOTELY_PRESENT].
     *
     * Used together with [resetPresentRemotely] when a full listing has been received from
     * the server to locally delete resources which are not present remotely (anymore).
     */
    protected open suspend fun deleteNotPresentRemotely() {
        val removed = localCollection.removeNotDirtyMarked(0)
        logger.info("Removed $removed local resources which are not present on the server anymore")
    }

    /**
     * Post-processing of synchronized entries, for instance contact group membership operations.
     */
    protected abstract suspend fun postProcess()


    // sync helpers

    protected fun syncState(dav: Response) =
        dav[SyncToken::class.java]?.token?.let {
            SyncState(SyncState.Type.SYNC_TOKEN, it)
        } ?: dav[GetCTag::class.java]?.cTag?.let {
            SyncState(SyncState.Type.CTAG, it)
        }

    private suspend fun querySyncState(): SyncState? =
        davCollection.propfind(0, CalDAV.GetCTag, WebDAV.SyncToken).selfResponse()?.let { syncState(it) }

    /**
     * Logs the exception, updates sync result and shows a notification to the user.
     */
    private fun handleException(e: Throwable, local: LocalResource?, remote: Url?) {
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

            is LocalStorageException, is RemoteException -> {
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
            dataType,
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
            dataType,
            localCollection.tag,
            collection,
            e,
            fileName,
            notifyInvalidResourceTitle()
        )

    protected abstract fun notifyInvalidResourceTitle(): String

    /**
     * A wrapper for making `PUT` requests with conditional headers.
     * @param content The content to send in the PUT request.
     * @param ifETag If one is given, the `If-Match` header will have this value.
     * @param ifScheduleTag If one is given, the `If-Schedule-Tag-Match` header will have this value.
     * @param ifNoneMatch If `true`, the `If-None-Match` header will be set to `*`.
     * @param headers Any other headers to append to the request.
     * @param callback Will be called with the request's response.
     */
    private suspend fun DavResource.put(
        content: OutgoingContent,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        ifNoneMatch: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        callback: suspend (HttpResponse) -> Unit
    ) {
        put(
            content,
            additionalHeaders = headers {
                if (ifETag != null)
                // only overwrite specific version
                    append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                if (ifScheduleTag != null)
                // only overwrite specific version
                    append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
                if (ifNoneMatch)
                // don't overwrite anything existing
                    append(HttpHeaders.IfNoneMatch, "*")

                // Append all custom headers
                appendAll(headers)
            },
            callback = callback
        )
    }

    /**
     * A wrapper for making `DELETE` requests with conditional headers.
     * @param ifETag If one is given, the `If-Match` header will have this value.
     * @param ifScheduleTag If one is given, the `If-Schedule-Tag-Match` header will have this value.
     * @param headers Any other headers to append to the request.
     * @param callback Will be called with the request's response.
     */
    private suspend fun DavResource.delete(
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        headers: Map<String, String> = emptyMap(),
        callback: suspend (HttpResponse) -> Unit
    ) {
        delete(
            additionalHeaders = headers {
                if (ifETag != null)
                    append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                if (ifScheduleTag != null)
                    append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))

                // Append all custom headers
                appendAll(headers)
            },
            callback = callback
        )
    }

}