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
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.exception.*
import at.bitfire.dav4android.property.GetCTag
import at.bitfire.dav4android.property.GetETag
import at.bitfire.davdroid.*
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.AccountSettingsActivity
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException
import okhttp3.HttpUrl
import okhttp3.RequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.security.cert.CertificateException
import java.util.*
import java.util.logging.Level
import javax.net.ssl.SSLHandshakeException

abstract class SyncManager(
        val context: Context,
        val settings: ISettings,
        val account: Account,
        val accountSettings: AccountSettings,
        val extras: Bundle,
        val authority: String,
        val syncResult: SyncResult,
        val uniqueCollectionId: String
): AutoCloseable {

    companion object {

        val SYNC_PHASE_PREPARE = 0
        val SYNC_PHASE_QUERY_CAPABILITIES = 1
        val SYNC_PHASE_PROCESS_LOCALLY_DELETED = 2
        val SYNC_PHASE_PREPARE_DIRTY = 3
        val SYNC_PHASE_UPLOAD_DIRTY = 4
        val SYNC_PHASE_CHECK_SYNC_STATE = 5
        val SYNC_PHASE_LIST_LOCAL = 6
        val SYNC_PHASE_LIST_REMOTE = 7
        val SYNC_PHASE_COMPARE_LOCAL_REMOTE = 8
        val SYNC_PHASE_DOWNLOAD_REMOTE = 9
        val SYNC_PHASE_POST_PROCESSING = 10
        val SYNC_PHASE_SAVE_SYNC_STATE = 11

        infix fun <T> Set<T>.disjunct(other: Set<T>) = (this - other) union (other - this)

    }

    protected val notificationManager = NotificationUtils.createChannels(context)

    protected lateinit var localCollection: LocalCollection<*>

    protected val httpClient = HttpClient.Builder(context, settings, accountSettings).build()
    protected lateinit var collectionURL: HttpUrl
    protected lateinit var davCollection: DavResource


    /** current sync phase */
    private var syncPhase: Int = SYNC_PHASE_PREPARE

    /** state information for debug info (local resource) */
    protected var currentLocalResource: LocalResource? = null

    /** state information for debug info (remote resource) */
    protected var currentDavResource: DavResource? = null


    /** remote CTag at the time of {@link #listRemote()} */
    protected var remoteCTag: String? = null

    /** sync-able resources in the local collection, as enumerated by {@link #listLocal()} */
    protected lateinit var localResources: MutableMap<String, LocalResource>

    /** sync-able resources in the remote collection, as enumerated by {@link #listRemote()} */
    protected lateinit var remoteResources: MutableMap<String, DavResource>

    /** resources which have changed on the server, as determined by {@link #compareLocalRemote()} */
    protected val toDownload = mutableSetOf<DavResource>()


    protected abstract fun notificationId(): Int
    protected abstract fun getSyncErrorTitle(): String

    @Suppress("UNUSED_VALUE")
    fun performSync() {
        // dismiss previous error notifications
        notificationManager.cancel(uniqueCollectionId, notificationId())

        try {
            Logger.log.info("Preparing synchronization")
            if (!prepare()) {
                Logger.log.info("No reason to synchronize, aborting")
                return
            }

            abortIfCancelled()
            syncPhase = SYNC_PHASE_QUERY_CAPABILITIES
            Logger.log.info("Querying capabilities")
            queryCapabilities()

            syncPhase = SYNC_PHASE_PROCESS_LOCALLY_DELETED
            Logger.log.info("Processing locally deleted entries")
            processLocallyDeleted()

            abortIfCancelled()
            syncPhase = SYNC_PHASE_PREPARE_DIRTY
            Logger.log.info("Locally preparing dirty entries")
            prepareDirty()

            syncPhase = SYNC_PHASE_UPLOAD_DIRTY
            Logger.log.info("Uploading dirty entries")
            uploadDirty()

            syncPhase = SYNC_PHASE_CHECK_SYNC_STATE
            Logger.log.info("Checking sync state")
            if (checkSyncState()) {
                syncPhase = SYNC_PHASE_LIST_LOCAL
                Logger.log.info("Listing local resources")
                listLocal()

                abortIfCancelled()
                syncPhase = SYNC_PHASE_LIST_REMOTE
                Logger.log.info("Listing remote resources")
                listRemote()

                abortIfCancelled()
                syncPhase = SYNC_PHASE_COMPARE_LOCAL_REMOTE
                Logger.log.info("Comparing local/remote entries")
                compareLocalRemote()

                syncPhase = SYNC_PHASE_DOWNLOAD_REMOTE
                Logger.log.info("Downloading remote entries")
                downloadRemote()

                syncPhase = SYNC_PHASE_POST_PROCESSING
                Logger.log.info("Post-processing")
                postProcess()

                syncPhase = SYNC_PHASE_SAVE_SYNC_STATE
                Logger.log.info("Saving sync state")
                saveSyncState()
            } else
                Logger.log.info("Remote collection didn't change, skipping remote sync")

        } catch (e: InterruptedException) {
            // re-throw to SyncAdapterService
            throw e
        } catch (e: InterruptedIOException) {
            throw e

        } catch (e: SSLHandshakeException) {
            Logger.log.log(Level.WARNING, "SSL handshake failed", e)

            // when a certificate is rejected by cert4android, the cause will be a CertificateException
            if (!BuildConfig.customCerts || e.cause !is CertificateException)
                notifyException(e)
        } catch (e: IOException) {
            Logger.log.log(Level.WARNING, "I/O exception during sync, trying again later", e)
            syncResult.stats.numIoExceptions++
        } catch (e: ServiceUnavailableException) {
            Logger.log.log(Level.WARNING, "Got 503 Service unavailable, trying again later", e)
            syncResult.stats.numIoExceptions++
            e.retryAfter?.let { retryAfter ->
                // how many seconds to wait? getTime() returns ms, so divide by 1000
                syncResult.delayUntil = (retryAfter.time - Date().time) / 1000
            }
        } catch (e: Throwable) {
            notifyException(e)
        }
    }

    private fun notifyException(e: Throwable) {
        val messageString: Int

        when (e) {
            is UnauthorizedException -> {
                Logger.log.log(Level.SEVERE, "Not authorized anymore", e)
                messageString = R.string.sync_error_unauthorized
                syncResult.stats.numAuthExceptions++
            }
            is HttpException, is DavException -> {
                Logger.log.log(Level.SEVERE, "HTTP/DAV Exception during sync", e)
                messageString = R.string.sync_error_http_dav
                syncResult.stats.numParseExceptions++
            }
            is CalendarStorageException, is ContactsStorageException -> {
                Logger.log.log(Level.SEVERE, "Couldn't access local storage", e)
                messageString = R.string.sync_error_local_storage
                syncResult.databaseError = true
            }
            else -> {
                Logger.log.log(Level.SEVERE, "Unknown sync error", e)
                messageString = R.string.sync_error
                syncResult.stats.numParseExceptions++
            }
        }

        val detailsIntent: Intent
        if (e is UnauthorizedException) {
            detailsIntent = Intent(context, AccountSettingsActivity::class.java)
            detailsIntent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
        } else {
            detailsIntent = Intent(context, DebugInfoActivity::class.java)
            detailsIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e)
            detailsIntent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account)
            detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority)
            detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase)
            currentLocalResource?.let { detailsIntent.putExtra(DebugInfoActivity.KEY_LOCAL_RESOURCE, it.toString()) }
            currentDavResource?.let { detailsIntent.putExtra(DebugInfoActivity.KEY_REMOTE_RESOURCE, it.toString()) }
        }

        // to make the PendingIntent unique
        detailsIntent.data = Uri.parse("uri://${javaClass.name}/$uniqueCollectionId")

        val builder = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_SYNC_PROBLEMS)
        builder .setSmallIcon(R.drawable.ic_sync_error_notification)
                .setLargeIcon(App.getLauncherBitmap(context))
                .setContentTitle(getSyncErrorTitle())
                .setContentIntent(PendingIntent.getActivity(context, 0, detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                .setCategory(NotificationCompat.CATEGORY_ERROR)

        try {
            val phases = context.resources.getStringArray(R.array.sync_error_phases)
            val message = context.getString(messageString, phases[syncPhase])
            builder.setContentText(message)
        } catch (ex: IndexOutOfBoundsException) {
            // should never happen
        }

        notificationManager.notify(uniqueCollectionId, notificationId(), builder.build())
    }

    /**
     * Throws an [InterruptedException] if the current thread has been interrupted,
     * most probably because synchronization was cancelled by the user.
     * @throws InterruptedException (which will be catched by [performSync])
     * */
    protected fun abortIfCancelled() {
        if (Thread.interrupted())
            throw InterruptedException("Sync was cancelled")
    }

    override fun close() {
        httpClient.close()
    }


    /** Prepares synchronization (for instance, allocates necessary resources).
     * @return whether actual synchronization is required / can be made. true = synchronization
     *         shall be continued, false = synchronization can be skipped */
    abstract protected fun prepare(): Boolean

    abstract protected fun queryCapabilities()

    /**
     * Process locally deleted entries (DELETE them on the server as well).
     * Checks for thread interruption before each request to allow quick sync cancellation.
     */
    protected open fun processLocallyDeleted() {
        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        val localList = localCollection.getDeleted()
        for (local in localList) {
            abortIfCancelled()

            currentLocalResource = local

            val fileName = local.fileName
            if (fileName != null) {
                Logger.log.info("$fileName has been deleted locally -> deleting from server")

                val remote = DavResource(httpClient.okHttpClient, collectionURL.newBuilder().addPathSegment(fileName).build())
                currentDavResource = remote
                try {
                    remote.delete(local.eTag)
                } catch (e: HttpException) {
                    Logger.log.warning("Couldn't delete $fileName from server; ignoring (may be downloaded again)")
                }
            } else
                Logger.log.info("Removing local record #${local.id} which has been deleted locally and was never uploaded")
            local.delete()
            syncResult.stats.numDeletes++

            currentLocalResource = null
            currentDavResource = null
        }
    }

    protected open fun prepareDirty() {
        // assign file names and UIDs to new contacts so that we can use the file name as an index
        Logger.log.info("Looking for contacts/groups without file name")
        for (local in localCollection.getWithoutFileName()) {
            currentLocalResource = local

            Logger.log.fine("Found local record #${local.id} without file name; generating file name/UID if necessary")
            local.prepareForUpload()

            currentLocalResource = null
        }
    }

    abstract protected fun prepareUpload(resource: LocalResource): RequestBody

    /**
     * Uploads dirty records to the server, using a PUT request for each record.
     * Checks for thread interruption before each request to allow quick sync cancellation.
     */
    protected open fun uploadDirty() {
        // upload dirty contacts
        for (local in localCollection.getDirty()) {
            abortIfCancelled()

            currentLocalResource = local
            val fileName = local.fileName

            val remote = DavResource(httpClient.okHttpClient, collectionURL.newBuilder().addPathSegment(fileName).build())
            currentDavResource = remote

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

            currentLocalResource = null
            currentDavResource = null
        }
    }

    /**
     * Checks the current sync state (e.g. CTag) and whether synchronization from remote is required.
     * @return <ul>
     *      <li><code>true</code>   if the remote collection has changed, i.e. synchronization from remote is required</li>
     *      <li><code>false</code>  if the remote collection hasn't changed</li>
     * </ul>
     */
    protected open fun checkSyncState(): Boolean {
        // check CTag (ignore on manual sync)
        davCollection.properties[GetCTag::class.java]?.let { remoteCTag = it.cTag }

        val localCTag = if (extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL)) {
            Logger.log.info("Manual sync, ignoring CTag")
            null
        } else
            localCollection.getCTag()

        return if (remoteCTag != null && remoteCTag == localCTag) {
            Logger.log.info("Remote collection didn't change (CTag=$remoteCTag), no need to query children")
            false
        } else
            true
    }

    /**
     * Lists all local resources which should be taken into account for synchronization into {@link #localResources}.
     */
    protected open fun listLocal() {
        // fetch list of local contacts and build hash table to index file name
        val localList = localCollection.getAll()
        val resources = HashMap<String, LocalResource>(localList.size)
        for (resource in localList) {
            Logger.log.fine("Found local resource: ${resource.fileName}")
            resource.fileName?.let { resources[it] = resource }
        }
        localResources = resources
        Logger.log.info("Found ${localResources.size} local resources")
    }

    /**
     * Lists all members of the remote collection which should be taken into account for synchronization into {@link #remoteResources}.
     */
    abstract protected fun listRemote()

    /**
     * Compares {@link #localResources} and {@link #remoteResources} by file name and ETag:
     * <ul>
     *     <li>Local resources which are not available in the remote collection (anymore) will be removed.</li>
     *     <li>Resources whose remote ETag has changed will be added into {@link #toDownload}</li>
     * </ul>
     */
    protected open fun compareLocalRemote() {
        /* check which contacts
           1. are not present anymore remotely -> delete immediately on local side
           2. updated remotely -> add to downloadNames
           3. added remotely  -> add to downloadNames
         */
        toDownload.clear()
        for ((name,local) in localResources) {
            val remote = remoteResources[name]
            currentDavResource = remote

            if (remote == null) {
                Logger.log.info("$name is not on server anymore, deleting")
                currentLocalResource = local
                local.delete()
                syncResult.stats.numDeletes++
            } else {
                // contact is still on server, check whether it has been updated remotely
                val localETag = local.eTag
                val getETag = remote.properties[GetETag::class.java]
                val remoteETag = getETag?.eTag ?: throw DavException("Server didn't provide ETag")
                if (remoteETag == localETag) {
                    Logger.log.fine("$name has not been changed on server (ETag still $remoteETag)")
                    syncResult.stats.numSkippedEntries++
                } else {
                    Logger.log.info("$name has been changed on server (current ETag=$remoteETag, last known ETag=$localETag)")
                    toDownload.add(remote)
                }

                // remote entry has been seen, remove from list
                remoteResources.remove(name)

                currentDavResource = null
                currentLocalResource = null
            }
        }

        // add all unseen (= remotely added) remote contacts
        if (remoteResources.isNotEmpty()) {
            Logger.log.info("New resources have been found on the server: ${remoteResources.keys.joinToString(", ")}")
            toDownload.addAll(remoteResources.values)
        }
    }

    /**
     * Downloads the remote resources in {@link #toDownload} and stores them locally.
     * Must check for thread interruption periodically to allow quick sync cancellation.
     */
    abstract protected fun downloadRemote()

    /**
     * For post-processing of entries, for instance assigning groups.
     */
    protected open fun postProcess() {}

    protected open fun saveSyncState() {
        /* Save sync state (CTag). It doesn't matter if it has changed during the sync process
           (for instance, because another client has uploaded changes), because this will simply
           cause all remote entries to be listed at the next sync. */
        Logger.log.info("Saving CTag=$remoteCTag")
        localCollection.setCTag(remoteCTag)
    }

}
