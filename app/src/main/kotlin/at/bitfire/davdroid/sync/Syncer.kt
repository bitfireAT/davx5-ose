/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.DeadObjectException
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import okhttp3.HttpUrl
import java.util.logging.Level

/**
 * Base class for sync code.
 *
 * Contains generic sync code, equal for all sync authorities, checks sync conditions and does
 * validation.
 *
 * Also provides useful methods that can be used by derived syncers ie [CalendarSyncer], etc.
 */
abstract class Syncer(
    val context: Context,
    val serviceRepository: DavServiceRepository,
    val collectionRepository: DavCollectionRepository,
    protected val account: Account,
    protected val extras: Array<String>,
    protected val authority: String,
    protected val syncResult: SyncResult
) {

    companion object {

        /**
         * Requests a re-synchronization of all entries. For instance, if this extra is
         * set for a calendar sync, all remote events will be listed and checked for remote
         * changes again.
         *
         * Useful if settings which modify the remote resource list (like the CalDAV setting
         * "sync events n days in the past") have been changed.
         */
        const val SYNC_EXTRAS_RESYNC = "resync"

        /**
         * Requests a full re-synchronization of all entries. For instance, if this extra is
         * set for an address book sync, all contacts will be downloaded again and updated in the
         * local storage.
         *
         * Useful if settings which modify parsing/local behavior have been changed.
         */
        const val SYNC_EXTRAS_FULL_RESYNC = "full_resync"

    }

    val accountSettings by lazy { AccountSettings(context, account) }
    val httpClient = lazy { HttpClient.Builder(context, accountSettings).build() }

    val remoteCollections = mutableMapOf<HttpUrl, Collection>()

    lateinit var provider: ContentProviderClient

    /**
     * Creates, updates and/or deletes local resources (calendars, address books, etc) according to
     * remote collection information. Then syncs the actual entries (events, tasks, contacts, etc)
     * of the remaining up-to-date resources.
     */
    open fun sync() {

        // 0. resource specific preparations
        beforeSync()

        // 1. find resource collections to be synced
        val service = serviceRepository.getByAccountAndType(account.name, getServiceType())
        if (service != null)
            for (collection in collectionRepository.getSyncCollections(service.id, authority))
                remoteCollections[collection.url] = collection

        // 2. update/delete local resources and determine new (unknown) remote collections
        val newFoundCollections = HashMap(remoteCollections)
        for (url in getLocalResourceUrls())
            remoteCollections[url].let { collection ->
                if (collection == null)
                    // Collection got deleted on server, delete obsolete local resource
                    deleteLocalResource(url)
                else {
                    // Collection exists locally, update local resource and don't add it again
                    updateLocalResource(collection)
                    newFoundCollections -= url
                }
            }

        // 3. create new local resources for new found collections
        for ((_, collection) in newFoundCollections)
            createLocalResource(collection)

        // 4. sync local resources
        for (url in getLocalSyncableResourceUrls())
            remoteCollections[url]?.let { collection ->
                syncLocalResource(collection)
            }

        // 5. clean up
        afterSync()

    }

    abstract fun beforeSync()

    abstract fun afterSync()

    abstract fun getServiceType(): String

    abstract fun getLocalResourceUrls(): List<HttpUrl?>

    abstract fun deleteLocalResource(url: HttpUrl?)

    abstract fun updateLocalResource(collection: Collection)

    abstract fun createLocalResource(collection: Collection)

    abstract fun getLocalSyncableResourceUrls(): List<HttpUrl?>

    abstract fun syncLocalResource(collection: Collection)

    fun onPerformSync() {
        Logger.log.log(Level.INFO, "$authority sync of $account initiated", extras.joinToString(", "))

        // Acquire ContentProviderClient
        val tryProvider = try {
            context.contentResolver.acquireContentProviderClient(authority)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Missing permissions for authority $authority", e)
            null
        }

        if (tryProvider == null) {
            /* Can happen if
             - we're not allowed to access the content provider, or
             - the content provider is not available at all, for instance because the respective
               system app, like "calendar storage" is disabled */
            Logger.log.warning("Couldn't connect to content provider of authority $authority")
            syncResult.stats.numParseExceptions++ // hard sync error

            return // Don't continue without provider
        }
        provider = tryProvider

        // run sync
        try {
            val runSync = /* ose */ true
            if (runSync)
                sync()

        } catch (e: DeadObjectException) {
            /* May happen when the remote process dies or (since Android 14) when IPC (for instance with the calendar provider)
            is suddenly forbidden because our sync process was demoted from a "service process" to a "cached process". */
            Logger.log.log(Level.WARNING, "Received DeadObjectException, treating as soft error", e)
            syncResult.stats.numIoExceptions++

        } catch (e: InvalidAccountException) {
            Logger.log.log(Level.WARNING, "Account was removed during synchronization", e)

        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't sync $authority", e)
            syncResult.stats.numParseExceptions++ // Hard sync error

        } finally {
            if (httpClient.isInitialized())
                httpClient.value.close()

            // close content provider client which was acquired above
            provider.close()

            Logger.log.log(
                Level.INFO,
                "$authority sync of $account finished",
                extras.joinToString(", "))
        }
    }

}