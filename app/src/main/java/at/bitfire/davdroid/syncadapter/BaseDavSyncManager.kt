/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.dav4android.DavCollection
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.exception.ConflictException
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.dav4android.exception.PreconditionFailedException
import at.bitfire.dav4android.property.GetCTag
import at.bitfire.dav4android.property.GetETag
import at.bitfire.dav4android.property.SyncToken
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.SyncState
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.ISettings
import okhttp3.HttpUrl
import okhttp3.RequestBody
import java.util.logging.Level

abstract class BaseDavSyncManager<ResourceType: LocalResource, out CollectionType: LocalCollection<ResourceType>, RemoteType: DavCollection>(
        context: Context,
        settings: ISettings,
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        localCollection: CollectionType
): SyncManager<ResourceType, CollectionType>(context, settings, account, accountSettings, extras, authority, syncResult, localCollection), AutoCloseable {

    protected val httpClient = HttpClient.Builder(context, settings, accountSettings).build()

    protected lateinit var collectionURL: HttpUrl
    protected lateinit var davCollection: RemoteType

    override fun close() {
        httpClient.close()
    }

    override fun prepare(): Boolean {
        // always re-sync on manual syncs
        if (extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL))
            localCollection.lastSyncState = null

        return true
    }

    /**
     * Process locally deleted entries (DELETE them on the server as well).
     * Checks for thread interruption before each request to allow quick sync cancellation.
     */
    override fun processLocallyDeleted(): Boolean {
        var numDeleted = 0

        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        val localList = localCollection.findDeleted()
        for (local in localList)
            useLocal(local, {
                abortIfCancelled()

                val fileName = local.fileName
                if (fileName != null) {
                    Logger.log.info("$fileName has been deleted locally -> deleting from server")

                    useRemote(DavResource(httpClient.okHttpClient, collectionURL.newBuilder().addPathSegment(fileName).build()), { remote ->
                        try {
                            remote.delete(local.eTag)
                            numDeleted++
                        } catch (e: HttpException) {
                            Logger.log.warning("Couldn't delete $fileName from server; ignoring (may be downloaded again)")
                        }
                    })
                } else
                    Logger.log.info("Removing local record #${local.id} which has been deleted locally and was never uploaded")
                local.delete()
                syncResult.stats.numDeletes++
            })
        Logger.log.info("Removed $numDeleted record(s) from server")
        return numDeleted > 0
    }

    protected abstract fun prepareUpload(resource: ResourceType): RequestBody

    /**
     * Uploads dirty records to the server, using a PUT request for each record.
     * Checks for thread interruption before each request to allow quick sync cancellation.
     */
    override fun uploadDirty(): Boolean {
        var numUploaded = 0

        // upload dirty contacts
        for (local in localCollection.findDirty())
            useLocal(local, {
                abortIfCancelled()

                if (local.fileName == null) {
                    Logger.log.fine("Generating file name/UID for local record #${local.id}")
                    local.assignNameAndUID()
                }

                val fileName = local.fileName!!
                useRemote(DavResource(httpClient.okHttpClient, collectionURL.newBuilder().addPathSegment(fileName).build()), { remote ->
                    // generate entity to upload (VCard, iCal, whatever)
                    val body = prepareUpload(local)

                    try {
                        if (local.eTag == null) {
                            Logger.log.info("Uploading new record $fileName")
                            remote.put(body, null, true)
                        } else {
                            Logger.log.info("Uploading locally modified record $fileName")
                            remote.put(body, local.eTag, false)
                        }
                        numUploaded++
                    } catch(e: ConflictException) {
                        // we can't interact with the user to resolve the conflict, so we treat 409 like 412
                        Logger.log.log(Level.INFO, "Edit conflict, ignoring", e)
                    } catch(e: PreconditionFailedException) {
                        Logger.log.log(Level.INFO, "Resource has been modified on the server before upload, ignoring", e)
                    }

                    val newETag = remote.properties[GetETag::class.java]
                    val eTag: String?
                    if (newETag != null) {
                        eTag = newETag.eTag
                        Logger.log.fine("Received new ETag=$eTag after uploading")
                    } else {
                        Logger.log.fine("Didn't receive new ETag after uploading, setting to null")
                        eTag = null
                    }

                    local.clearDirty(eTag)
                })
            })
        Logger.log.info("Sent $numUploaded record(s) to server")
        return numUploaded > 0
    }

    override fun syncRequired(): Boolean {
        val localState = localCollection.lastSyncState
        val remoteState = syncState(true)
        Logger.log.info("Local sync state = $localState, remote sync state = $remoteState")
        return when {
            remoteState?.type == SyncState.Type.SYNC_TOKEN -> {
                val lastKnownToken = localState?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }?.value
                lastKnownToken != remoteState.value
            }
            remoteState?.type == SyncState.Type.CTAG -> {
                val lastKnownCTag = localState?.takeIf { it.type == SyncState.Type.CTAG }?.value
                lastKnownCTag != remoteState.value
            }
            else ->
                true
        }
    }

    override fun syncState(forceRefresh: Boolean) = useRemoteCollection { remote ->
        if (forceRefresh)
            remote.propfind(0, GetCTag.NAME, SyncToken.NAME)

        remote.properties[SyncToken::class.java]?.token?.let {
            SyncState(SyncState.Type.SYNC_TOKEN, it)
        } ?:
        remote.properties[GetCTag::class.java]?.cTag?.let {
            SyncState(SyncState.Type.CTAG, it)
        }
    }

    override fun resetPresentRemotely() {
        val number = localCollection.markNotDirty(0)
        Logger.log.info("Number of local non-dirty entries: $number")
    }

    override fun compareLocalRemote(syncState: SyncState?, remoteResources: Map<String, DavResource>): RemoteChanges {
        /* check which resources are
           1. updated remotely -> update
           2. added remotely -> update
           3. not present remotely anymore -> ignore (because they will be deleted by deleteObsolete()
         */

        val changes = RemoteChanges(syncState, false)

        for ((name, remote) in remoteResources)
            useLocal(localCollection.findByName(name), { local ->
                if (local == null) {
                    Logger.log.info("$name has been added remotely")
                    changes.updated += remote
                } else {
                    val localETag = local.eTag
                    val remoteETag = remote.properties[GetETag::class.java]?.eTag ?: throw DavException("Server didn't provide ETag")
                    if (localETag == remoteETag)
                        Logger.log.fine("$name has not been changed on server (ETag still $remoteETag)")
                    else {
                        Logger.log.info("$name has been changed on server (current ETag=$remoteETag, last known ETag=$localETag)")
                        changes.updated += remote
                    }

                    // mark as remotely present, so that this resource won't be deleted at the end
                    local.updateFlags(LocalResource.FLAG_REMOTELY_PRESENT)
                }
            })

        return changes
    }

    override fun listRemoteChanges(state: SyncState?): RemoteChanges {
        throw UnsupportedOperationException("Collection sync not implemented yet")

        /* TODO
        try {
            davCollection.reportChanges(
                    state?.takeIf { state.type == SyncState.Type.SYNC_TOKEN }?.value,
                    false, COLLECTION_SYNC_PAGE_SIZE,
                    GetETag.NAME)
        } catch(e: HttpException) {
            if (e.status in arrayOf(500,507))
                // some servers don't like the limit, try again without
                davCollection.reportChanges(
                        state?.takeIf { state.type == SyncState.Type.SYNC_TOKEN }?.value,
                        false, null,
                        GetETag.NAME, GetContentType.NAME)
        }

        var syncToken: String? = null
        davCollection.properties[SyncToken::class.java]?.let {
            syncToken = it.token
        }

        val changes = RemoteChanges(syncToken?.let { SyncState(SyncState.Type.SYNC_TOKEN, it) }, davCollection.furtherResults)
        for (member in davCollection.members)
            changes.updated += member

        for (member in davCollection.removedMembers)
            changes.deleted += member.fileName()

        Logger.log.log(Level.INFO, "Received list of changed/removed resources", changes)
        return changes
        */
    }

    override fun deleteNotPresentRemotely() {
        val removed = localCollection.removeNotDirtyMarked(0)
        Logger.log.info("Removed $removed local resources which are not present on the server anymore")
    }

    override fun postProcess() {
    }


    protected fun<T: LocalResource?, R> useLocal(local: T, body: (T) -> R): R {
        local?.let { currentLocalResource.push(it) }
        val result = body(local)
        local?.let { currentLocalResource.pop() }
        return result
    }

    protected fun<T: DavResource, R> useRemote(remote: T, body: (T) -> R): R {
        currentRemoteResource.push(remote)
        val result = body(remote)
        currentRemoteResource.pop()
        return result
    }

    protected fun<R> useRemoteCollection(body: (RemoteType) -> R) =
            useRemote(davCollection, body)

}