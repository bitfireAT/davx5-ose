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
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.settings.AccountSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Base class for sync code.
 *
 * Contains generic sync code, equal for all sync authorities
 */
abstract class Syncer<CollectionType: LocalCollection<*>>(
    protected val account: Account,
    protected val extras: Array<String>,
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

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var logger: Logger

    abstract val serviceType: String

    abstract val authority: String

    /** All local collections of a specific type (calendar, address book, etc) */
    abstract val localCollections: List<CollectionType>

    /** Sync enabled local collections of specific type */
    abstract val localSyncCollections: List<CollectionType>

    val remoteCollections = mutableMapOf<HttpUrl, Collection>()
    val accountSettings by lazy { accountSettingsFactory.forAccount(account) }
    val httpClient = lazy { HttpClient.Builder(context, accountSettings).build() }

    lateinit var provider: ContentProviderClient

    /**
     * Creates, updates and/or deletes local resources (calendars, address books, etc) according to
     * remote collection information. Then syncs the actual entries (events, tasks, contacts, etc)
     * of the remaining up-to-date resources.
     */
    fun sync() {

        // 0. resource specific preparations
        beforeSync()

        // 1. find resource collections to be synced
        val service = serviceRepository.getByAccountAndType(account.name, serviceType)
        if (service != null)
            for (remoteCollection in getSyncCollections(service.id))
                remoteCollections[remoteCollection.url] = remoteCollection

        // 2. update/delete local resources and determine new (unknown) remote collections
        val newRemoteCollections = HashMap(remoteCollections)
        for (localCollection in localCollections) {
            val remoteCollection = remoteCollections[getUrl(localCollection)]
            if (remoteCollection == null)
                // Collection got deleted on server, delete obsolete local resource
                delete(localCollection)
            else {
                // Collection exists locally, update local resource and don't add it again
                update(localCollection, remoteCollection)
                newRemoteCollections -= remoteCollection.url
            }
        }

        // 3. create new local collections for newly found remote collections
        for ((_, collection) in newRemoteCollections)
            create(collection)

        // 4. sync local resources
        for (localCollection in localSyncCollections)
            remoteCollections[getUrl(localCollection)]?.let { remoteCollection ->
                syncCollection(localCollection, remoteCollection)
            }

    }

    abstract fun beforeSync()

    abstract fun getSyncCollections(serviceId: Long): List<Collection>

    abstract fun getUrl(localCollection: CollectionType): HttpUrl?

    abstract fun delete(localCollection: CollectionType)

    abstract fun update(localCollection: CollectionType, remoteCollection: Collection)

    abstract fun create(remoteCollection: Collection)

    abstract fun syncCollection(localCollection: CollectionType, remoteCollection: Collection)

    fun onPerformSync() {
        logger.log(Level.INFO, "$authority sync of $account initiated", extras.joinToString(", "))

        // Acquire ContentProviderClient
        val tryProvider = try {
            context.contentResolver.acquireContentProviderClient(authority)
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "Missing permissions for authority $authority", e)
            null
        }

        if (tryProvider == null) {
            /* Can happen if
             - we're not allowed to access the content provider, or
             - the content provider is not available at all, for instance because the respective
               system app, like "calendar storage" is disabled */
            logger.warning("Couldn't connect to content provider of authority $authority")
            syncResult.stats.numParseExceptions++ // hard sync error

            return // Don't continue without provider
        }
        provider = tryProvider

        // run sync
        try {
            provider.use {
                val runSync = /* ose */ true
                if (runSync)
                    sync()
            }
        } catch (e: DeadObjectException) {
            /* May happen when the remote process dies or (since Android 14) when IPC (for instance with the calendar provider)
            is suddenly forbidden because our sync process was demoted from a "service process" to a "cached process". */
            logger.log(Level.WARNING, "Received DeadObjectException, treating as soft error", e)
            syncResult.stats.numIoExceptions++

        } catch (e: InvalidAccountException) {
            logger.log(Level.WARNING, "Account was removed during synchronization", e)

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Couldn't sync $authority", e)
            syncResult.stats.numParseExceptions++ // Hard sync error

        } finally {
            if (httpClient.isInitialized())
                httpClient.value.close()
            logger.log(
                Level.INFO,
                "$authority sync of $account finished",
                extras.joinToString(", "))
        }
    }

}