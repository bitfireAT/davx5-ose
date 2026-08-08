/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.QuotedStringUtils
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
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.SyncMultigetSemaphore
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.resource.remote.MemberState
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.resource.remote.filterMembers
import at.bitfire.davdroid.resource.remote.filterNotCollections
import at.bitfire.davdroid.resource.remote.member
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.util.batchMap
import at.bitfire.synctools.storage.LocalStorageException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform
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
 *
 * @param accountId         [AccountId] of the account to synchronize
 * @param httpClient        HTTP client to use for network requests, already authenticated with credentials from the account
 * @param dataType          data type to synchronize
 * @param syncResult        receiver for result of the synchronization (will be updated by [performSync])
 * @param collectionInfo    remote collection info in the database
 * @param resync            whether re-synchronization is requested
 * @param settings          snapshot of the account settings relevant for this sync run
 */
abstract class SyncManager<LocalType : LocalResource>(
    val accountId: AccountId,
    val httpClient: HttpClient,
    val dataType: SyncDataType,
    val syncResult: SyncResult,
    val collectionInfo: Collection,
    val resync: ResyncType?,
    val settings: SyncSettings
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
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var readOnlyPolicy: ReadOnlyPolicy

    @Inject
    lateinit var syncStatsRepository: DavSyncStatsRepository

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    @SyncMultigetSemaphore
    lateinit var syncMultigetSemaphore: Semaphore

    @Inject
    lateinit var syncNotificationManagerFactory: SyncNotificationManager.Factory


    /** local collection to synchronize (interface to content provider) */
    protected abstract val localCollection: LocalCollection<LocalType>

    protected abstract val remoteCollection: WebDavCollection

    private val syncNotificationManager by lazy {
        syncNotificationManagerFactory.create(accountId)
    }

    /**
     * Push-Dont-Notify header, added to PUT and DELETE requests if subscription exists.
     */
    private val pushDontNotifyHeader by lazy {
        collectionInfo.pushSubscription?.let { pushSubscription ->
            mapOf("Push-Dont-Notify" to QuotedStringUtils.asQuotedString(pushSubscription))
        } ?: emptyMap()
    }

    suspend fun performSync() = withContext(ioDispatcher) {
        // keep generic ioDispatcher until all LocalStorage calls are suspending or wrapped in withContext(ioDispatcher)

        // dismiss previous error notifications
        syncNotificationManager.dismissCollectionError(localCollectionTag = localCollection.tag)

        try {
            logger.info("Preparing synchronization")
            if (!prepare()) {
                logger.info("No reason to synchronize, aborting")
                return@withContext
            }
            syncStatsRepository.logSyncTime(collectionInfo.id, dataType)

            logger.info("Querying server capabilities")
            val (initialSyncState, capabilities) = collectionInfo.url.withExceptionContext {
                remoteCollection.queryCapabilities()
            }
            logger.info("Sync state = $initialSyncState, capabilities = $capabilities")
            var remoteSyncState = initialSyncState

            logger.info("Processing local deletes/updates")
            val modificationsPresent =
                // bitwise OR guarantees that both expressions are evaluated
                processLocallyDeleted() or uploadDirty(capabilities)

            if (resync == ResyncType.RESYNC_ENTRIES) {
                logger.info("Forcing re-synchronization of all entries")

                // forget sync state of collection (→ initial sync in case of SyncAlgorithm.COLLECTION_SYNC)
                localCollection.lastSyncState = null
                remoteSyncState = null

                // forget sync state of members (→ download all members again and update them locally)
                localCollection.forgetETags()
            }

            if (modificationsPresent || syncRequired(remoteSyncState))
                when (syncAlgorithm(capabilities)) {
                    SyncAlgorithm.PROPFIND_REPORT ->
                        syncPropfindReport(modificationsPresent, remoteSyncState, capabilities)

                    SyncAlgorithm.COLLECTION_SYNC ->
                        syncCollectionSync(capabilities)
                }
            else
                logger.info("Remote collection didn't change, no reason to sync")

        } catch (potentiallyWrappedException: Throwable) {
            val ctx = potentiallyWrappedException.unwrapContext()

            when (val e = ctx.cause) {
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
                        handleException(e, ctx.localResource, ctx.remoteResource)
                }

                // specific HTTP errors
                is ServiceUnavailableException -> {
                    logger.log(Level.WARNING, "Got 503 Service unavailable, trying again later", e)
                    // determine when to retry
                    syncResult.delayUntil = e.getDelayUntil().epochSecond
                    syncResult.softError = true
                }

                // all others
                else ->
                    handleException(e, ctx.localResource, ctx.remoteResource)
            }
        }
    }


    /**
     * Prepares synchronization.
     *
     * @return whether synchronization shall be performed
     */
    protected open suspend fun prepare(): Boolean = true

    //region Processing of locally dirty/deleted items

    /**
     * Processes locally deleted entries. This can mean:
     *
     * - forwarding them to the server (HTTP `DELETE`)
     * - resetting their local state so that they will be downloaded again because they're read-only
     *
     * @return whether local resources have been processed so that a synchronization is always necessary
     */
    protected suspend fun processLocallyDeleted(): Boolean {
        if (localCollection.readOnly)
            return readOnlyPolicy.resetDeleted(localCollection)

        var numDeleted = 0

        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        localCollection.findDeleted().collect { local ->
            local.withExceptionContext {
                val fileName = local.fileName
                if (fileName != null) {
                    val lastScheduleTag = local.scheduleTag
                    val lastETag = if (lastScheduleTag == null) local.eTag else null
                    logger.info("$fileName has been deleted locally -> deleting from server (ETag $lastETag / schedule-tag $lastScheduleTag)")

                    collectionInfo.url.member(fileName).withExceptionContext {
                        try {
                            remoteCollection.deleteMember(
                                fileName = fileName,
                                ifETag = lastETag,
                                ifScheduleTag = lastScheduleTag,
                                additionalHeaders = pushDontNotifyHeader,
                            )
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
    protected open suspend fun uploadDirty(capabilities: WebDavCollection.Capabilities): Boolean {
        if (localCollection.readOnly)
            return readOnlyPolicy.resetDirty(localCollection)

        var numUploaded = 0

        localCollection.findDirty().collect { local ->
            local.withExceptionContext {
                uploadDirty(local, capabilities)
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
     * @param capabilities  current capabilities of the remote collection
     * @param forceAsNew    whether the ETag (and Schedule-Tag) of [local] are ignored and the resource
     *                      is created as a new resource on the server
     */
    protected suspend fun uploadDirty(
        local: LocalType,
        capabilities: WebDavCollection.Capabilities,
        forceAsNew: Boolean = false
    ) {
        val existingFileName = local.fileName

        val upload = generateUpload(local, capabilities)

        val fileName = existingFileName ?: upload.suggestedFileName
        val uploadUrl = collectionInfo.url.member(fileName)

        try {
            uploadUrl.withExceptionContext {
                if (existingFileName == null || forceAsNew) {
                    // create new resource on server
                    logger.log(Level.INFO, "Uploading new resource {0} -> {1}", arrayOf<Any?>(local.id, fileName))

                    val result = remoteCollection.createMember(
                        fileName = fileName,
                        content = upload.content,
                        additionalHeaders = pushDontNotifyHeader
                    )

                    // success (no exception thrown)
                    onSuccessfulUpload(
                        local = local,
                        newFileName = fileName,
                        eTag = result.eTag,
                        scheduleTag = result.scheduleTag,
                        context = upload.onSuccessContext
                    )

                } else {
                    // update resource on server
                    val ifScheduleTag = local.scheduleTag
                    val ifETag = if (ifScheduleTag == null) local.eTag else null

                    logger.log(
                        Level.INFO,
                        "Uploading modified resource {0} -> {1} (if ETag={2} / Schedule-Tag={3})",
                        arrayOf<Any?>(local.id, fileName, ifETag, ifScheduleTag)
                    )

                    val result = remoteCollection.updateMember(
                        fileName = fileName,
                        content = upload.content,
                        ifETag = ifETag,
                        ifScheduleTag = ifScheduleTag,
                        additionalHeaders = pushDontNotifyHeader
                    )

                    // success (no exception thrown)
                    onSuccessfulUpload(
                        local = local,
                        newFileName = fileName,
                        eTag = result.eTag,
                        scheduleTag = result.scheduleTag,
                        context = upload.onSuccessContext
                    )
                }
            }

        } catch (e: Throwable) {
            when (val ex = e.unwrapContext().cause) {
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
                        uploadDirty(local, capabilities, forceAsNew = true)
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
     * @param resource      local resource to generate the body from
     * @param capabilities  current capabilities of the remote collection
     *
     * @return iCalendar or vCard (content + Content-Type) that can be uploaded to the server
     */
    @VisibleForTesting
    internal abstract fun generateUpload(
        resource: LocalType,
        capabilities: WebDavCollection.Capabilities
    ): GeneratedResource

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

    //endregion


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
    protected fun syncRequired(state: SyncState?): Boolean {
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
    protected abstract fun syncAlgorithm(capabilities: WebDavCollection.Capabilities): SyncAlgorithm

    /**
     * Marks all local resources which shall be taken into consideration for this
     * sync as "synchronizing". Purpose of marking is that resources which have been marked
     * and are not present remotely anymore can be deleted.
     *
     * Used together with [deleteNotPresentRemotely].
     */
    protected fun resetPresentRemotely() {
        val number = localCollection.markNotDirty(0)
        logger.info("Number of local non-dirty entries: $number")
    }

    /**
     * Processes a downloaded resource (retrieved via [WebDavCollection.multiget]): maps it to a
     * local format and stores it into local storage.
     */
    protected abstract suspend fun processDownload(result: WebDavCollection.MultiGetItem)

    /**
     * Locally deletes entries which are
     *   1. not dirty and
     *   2. not marked as [LocalResource.FLAG_REMOTELY_PRESENT].
     *
     * Used together with [resetPresentRemotely] when a full listing has been received from
     * the server to locally delete resources which are not present remotely (anymore).
     */
    protected suspend fun deleteNotPresentRemotely() {
        val removed = localCollection.removeNotDirtyMarked(0)
        logger.info("Removed $removed local resources which are not present on the server anymore")
    }

    /**
     * Post-processing of synchronized entries, for instance contact group membership operations.
     */
    protected abstract suspend fun postProcess()


    //region Sync algorithm-specific: PROPFIND/REPORT

    private suspend fun syncPropfindReport(
        modificationsPresent: Boolean,
        remoteSyncState: SyncState?,
        capabilities: WebDavCollection.Capabilities
    ) {
        logger.info("Sync algorithm: full listing as one result (PROPFIND/REPORT)")
        resetPresentRemotely()

        // get current sync state
        var currentSyncState = remoteSyncState
        if (modificationsPresent)
            currentSyncState = remoteCollection.querySyncState()

        // list and process all entries at current sync state (which may be the same as or newer than remoteSyncState)
        logger.info("Processing remote entries")
        collectionInfo.url.withExceptionContext {
            processListing(remoteCollection.listFilteredMembers(), capabilities)
        }

        logger.info("Deleting entries which are not present remotely anymore")
        deleteNotPresentRemotely()

        logger.info("Post-processing")
        postProcess()

        logger.info("Saving sync state: $currentSyncState")
        localCollection.lastSyncState = currentSyncState
    }

    /**
     * Processes a full listing of remote members (PROPFIND/REPORT sync algorithm) with
     * `Depth: 1`.
     *
     * Only new or changed members are queued for download. (Members that have vanished
     * are cleaned up separately by [deleteNotPresentRemotely] once the whole listing has
     * been processed).
     *
     * @param filteredMembers   filtered members to process, as listed by [WebDavCollection.listFilteredMembers]
     * @param capabilities      current capabilities of the remote collection
     */
    private suspend fun processListing(
        filteredMembers: Flow<MemberState>,
        capabilities: WebDavCollection.Capabilities
    ) {
        filteredMembers
            // filter the items we want to download
            .mapNotNull { member ->
                // marks remotely present members as side effect
                decideDownload(member.fileName, member.href, member.eTag)
            }
            // download items in batches concurrently
            .batchMap(MULTIGET_BATCH_SIZE) { batch -> downloadMembers(batch, capabilities) }
            // process and store downloaded items
            .collect { item -> processDownload(item) }
    }

    //endregion


    //region Sync algorithm-specific: collection-sync

    private suspend fun syncCollectionSync(capabilities: WebDavCollection.Capabilities) {
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

        do {
            logger.info("Listing changes since $syncState")
            var (changesFlow, changesResult) = listRemoteChanges(syncState)
            try {
                processChanges(changesFlow, capabilities)
            } catch (e: HttpException) {
                if (e.errors.contains(Error(WebDAV.ValidSyncToken))) {
                    logger.info("Sync token invalid, performing initial sync")
                    initialSync = true
                    resetPresentRemotely()

                    val (retryFlow, retryResult) = listRemoteChanges(null)
                    changesFlow = retryFlow
                    changesResult = retryResult
                    processChanges(changesFlow, capabilities)
                } else
                    throw e
            }

            val syncToken = changesResult.syncToken
                ?: throw DavException("Received sync-collection response without sync-token")
            syncState = SyncState.fromSyncToken(syncToken, initialSync)

            logger.info("Saving sync state: $syncState")
            localCollection.lastSyncState = syncState

            logger.info("Server has further changes = ${changesResult.furtherResults}")
        } while (changesResult.furtherResults)

        if (initialSync) {
            // initial sync is finished, remove all local resources which have not been listed by server
            logger.info("Deleting local resources which are not on server (anymore)")
            deleteNotPresentRemotely()

            // remove initial sync flag
            syncState.initialSync = false
            logger.info("Initial sync completed, saving sync state: $syncState")
            localCollection.lastSyncState = syncState
        }

        logger.info("Post-processing")
        postProcess()
    }

    /**
     * Holds the sync-token / further-results reported by a `sync-collection` REPORT.
     */
    protected class SyncCollectionResult {
        var syncToken: SyncToken? = null
        var furtherResults: Boolean = false
    }

    /**
     * Builds a [Flow] of the member responses of a `sync-collection` REPORT (RFC 6578), together
     * with a [SyncCollectionResult].
     *
     * @param since sync state to list the changes since; `null` for an initial sync
     */
    private fun listRemoteChanges(since: SyncState?): Pair<Flow<MultiStatusItem>, SyncCollectionResult> {
        val result = SyncCollectionResult()
        val flow = remoteCollection.davCollection.reportChanges(
            syncToken = since?.takeIf { since.type == SyncState.Type.SYNC_TOKEN }?.value,
            infiniteDepth = false,
            limit = null,
            WebDAV.GetETag,     // we need the ETag for every item
            WebDAV.ResourceType // we want to ignore sub-collections, so we need to know which items are collections
        ).transform { item ->
            when (item) {
                is MultiStatusItem.Response -> when (item.relation) {
                    Response.HrefRelation.SELF -> {
                        // incoming self response, update result
                        result.furtherResults = item.response.status == HttpStatusCode.InsufficientStorage
                    }

                    Response.HrefRelation.MEMBER -> {
                        // incoming (changed/deleted) member response, emit to flow
                        emit(item)
                    }

                    else ->
                        logger.warning("Unexpected sync-collection response: ${item.response}")
                }

                is MultiStatusItem.ExtraProperty -> {
                    // incoming sync-token, update result
                    (item.property as? SyncToken)?.let { result.syncToken = it }
                }
            }
        }
        return flow to result
    }

    /**
     * Processes changes reported by a `sync-collection` REPORT (collection-sync algorithm)
     * with `Depth: 1`. Each member response either represents
     *
     * - a new/changed member → queue for download, or
     * - a 404 response signaling that the member has been deleted on the server → delete locally.
     *
     * @param remoteItems   Multi-Status items to process
     * @param capabilities  current capabilities of the remote collection
     */
    private suspend fun processChanges(
        remoteItems: Flow<MultiStatusItem>,
        capabilities: WebDavCollection.Capabilities
    ) {
        remoteItems.responsesWithRelation()
            // filter member resources
            .filterMembers()
            // we requested Depth: 1, but may still receive collections which are direct members
            .filterNotCollections()
            // filter the items we want to download
            .mapNotNull { item ->
                val response = item.response
                when {
                    // 2xx means "new/changed member"
                    response.isSuccess() -> {
                        // marks remotely present members as side effect
                        decideDownload(response.hrefName(), response.href, response[GetETag::class.java]?.eTag)
                    }

                    // 404 means "removed member"
                    response.status == HttpStatusCode.NotFound -> {
                        // locally deletes remotely removed members as side effect
                        deleteRemovedMember(response.hrefName())
                        null
                    }

                    else -> {
                        logger.warning("Ignoring response for ${response.href} (${response.status})")
                        null
                    }
                }
            }
            // download items in batches concurrently
            .batchMap(MULTIGET_BATCH_SIZE) { batch -> downloadMembers(batch, capabilities) }
            // process and store downloaded items
            .collect { item -> processDownload(item) }
    }

    /**
     * Locally deletes a member after the server reported it as deleted remotely.
     */
    private suspend fun deleteRemovedMember(name: String) {
        localCollection.findByName(name)?.let { local ->
            local.withExceptionContext {
                logger.info("$name has been deleted on server, deleting locally")
                local.deleteLocal()
            }
        }
    }

    //endregion


    //region Processing shared by sync algorithms

    /**
     * Decides whether a listed member represents a new or changed resource that needs to be
     * (re)downloaded, and marks it as remotely present so it won't be deleted by
     * [deleteNotPresentRemotely].
     *
     * @param name          file name of the member
     * @param href          URL of the member
     * @param remoteETag    current ETag of the member, if provided by the server
     *
     * @return [href] if the member needs to be (re)downloaded, `null` otherwise
     */
    private suspend fun decideDownload(name: String, href: Url, remoteETag: String?): Url? {
        logger.fine("Found remote resource: $name")

        val local = localCollection.findByName(name)
        return local.withExceptionContext {
            if (local == null) {
                logger.info("$name has been added remotely, queueing download")
                href
            } else {
                val eTag = remoteETag ?: throw DavException("Server didn't provide ETag")

                // mark as remotely present, so that this resource won't be deleted at the end
                local.updateFlags(LocalResource.FLAG_REMOTELY_PRESENT)

                if (local.eTag == eTag) {
                    logger.info("$name has not been changed on server (ETag still $eTag)")
                    null
                } else {
                    logger.info("$name has been changed on server (current ETag=$eTag, last known ETag=${local.eTag})")
                    href
                }
            }
        }
    }

    /**
     * Downloads one batch of members via [WebDavCollection.multiget], bounded by
     * [syncMultigetSemaphore] and tagged with [collectionInfo]'s URL for error context.
     */
    private fun downloadMembers(
        batch: List<Url>,
        capabilities: WebDavCollection.Capabilities
    ): Flow<WebDavCollection.MultiGetItem> = flow {
        syncMultigetSemaphore.withPermit {
            logger.info("Downloading ${batch.size} resources: $batch")
            collectionInfo.url.withExceptionContext {
                emitAll(remoteCollection.multiget(batch, capabilities))
            }
        }
    }

    //endregion


    // sync helpers

    /**
     * Logs the exception, updates sync result and shows a notification to the user.
     */
    private suspend fun handleException(e: Throwable, local: LocalResource?, remote: Url?) {
        var message: String
        when (e) {
            is IOException -> {
                logger.log(Level.WARNING, "I/O error", e)
                syncResult.softError = true
                message = context.getString(R.string.sync_error_io, e.localizedMessage)
            }

            is UnauthorizedException -> {
                logger.log(Level.SEVERE, "Not authorized anymore", e)
                syncResult.hardError = true
                message = context.getString(R.string.sync_error_authentication_failed)
            }

            is HttpException, is DavException -> {
                logger.log(Level.SEVERE, "HTTP/DAV exception", e)
                syncResult.hardError = true
                message = context.getString(R.string.sync_error_http_dav, e.localizedMessage)
            }

            is LocalStorageException, is RemoteException -> {
                logger.log(Level.SEVERE, "Couldn't access local storage", e)
                syncResult.hardError = true
                message = context.getString(R.string.sync_error_local_storage, e.localizedMessage)
            }

            else -> {
                logger.log(Level.SEVERE, "Unclassified sync error", e)
                syncResult.hardError = true
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

    protected suspend fun notifyInvalidResource(e: Throwable, fileName: String) =
        syncNotificationManager.notifyInvalidResource(
            dataType,
            localCollection.tag,
            collectionInfo,
            e,
            fileName,
            notifyInvalidResourceTitle()
        )

    protected abstract fun notifyInvalidResourceTitle(): String


    companion object {
        const val MULTIGET_BATCH_SIZE = 10
    }

}
