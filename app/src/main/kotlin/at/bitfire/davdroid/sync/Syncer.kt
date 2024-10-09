/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.DeadObjectException
import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.settings.AccountSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Base class for sync code.
 *
 * Contains generic sync code, equal for all sync authorities.
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
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    abstract val authority: String
    abstract val serviceType: String

    val accountSettings by lazy { accountSettingsFactory.create(account) }
    val httpClient = lazy { HttpClient.Builder(context, accountSettings).build() }

    /**
     * Creates, updates and/or deletes local collections (calendars, address books, etc) according to
     * remote collection information. Then syncs the actual entries (events, tasks, contacts, etc)
     * of the remaining, now up-to-date, collections.
     */
    @VisibleForTesting
    internal fun sync(provider: ContentProviderClient) {
        // Collection type specific preparations
        if (!prepare(provider)) {
            logger.log(Level.WARNING, "Failed to prepare sync. Won't run sync.")
            return
        }

        // Find collections in database and provider which should be synced (are sync-enabled)
        val dbCollections = getSyncEnabledCollections()
        val localCollections = getLocalCollections(provider).toMutableList()

        // Update/delete local collections and determine new (unknown) remote collections
        val newDbCollections = updateCollections(localCollections, dbCollections)

        // Create new local collections for newly found remote collections
        val newProviderCollections = createLocalCollections(provider, newDbCollections)
        localCollections.addAll(newProviderCollections) // Add the newly created collections

        // Sync local collection contents (events, contacts, tasks)
        syncCollectionContents(provider, localCollections, dbCollections)
    }

    /**
     * Finds sync enabled collections in database. They contain collection info which might have
     * been updated by collection refresh [at.bitfire.davdroid.servicedetection.DavResourceFinder].
     *
     * @return The sync enabled collections as hash map identified by their URL
     */
    @VisibleForTesting
    internal fun getSyncEnabledCollections(): Map<HttpUrl, Collection> {
        val dbCollections = mutableMapOf<HttpUrl, Collection>()
        serviceRepository.getByAccountAndType(account.name, serviceType)?.let { service ->
            for (dbCollection in getDbSyncCollections(service.id))
                dbCollections[dbCollection.url] = dbCollection
        }
        return dbCollections
    }

    /**
     * Updates and deletes local collections. Specifically:
     *
     * - Deletes local collections if corresponding database collections are missing. If
     * a local collection is removed because it's not in [dbCollections], **it becomes also removed from [localCollections]!**
     *
     * - Updates local collections with possibly new info from corresponding database collections.
     * - Determines new database collections to be also created as local collections.
     *
     * @param localCollections The local collections to be updated or deleted. May be modified by this method.
     * @param dbCollections The database collections possibly containing new information
     *
     * @return New found database collections to be created in provider
     */
    @VisibleForTesting
    internal fun updateCollections(
        localCollections: MutableList<CollectionType>,
        dbCollections: Map<HttpUrl, Collection>
    ): HashMap<HttpUrl, Collection> {
        val newDbCollections = HashMap(dbCollections)   // create a copy
        for (localCollection in localCollections) {
            val dbCollection = dbCollections[localCollection.collectionUrl?.toHttpUrlOrNull()]
            if (dbCollection == null) {
                // Collection not available in db = on server (anymore), delete obsolete local collection
                logger.info("Deleting local collection ${localCollection.title}")
                localCollection.deleteCollection()
                localCollections -= localCollection
            } else {
                // Collection exists locally, update local collection and remove it from "to be created" map
                update(localCollection, dbCollection)
                newDbCollections -= dbCollection.url
            }
        }
        return newDbCollections
    }

    /**
     * Creates new local collections from database collections.
     *
     * @param provider Content provider client to access local collections.
     * @param dbCollections Database collections to be created as local collections.
     * @return Newly created local collections.
     */
    @VisibleForTesting
    internal fun createLocalCollections(
        provider: ContentProviderClient,
        dbCollections: Map<HttpUrl, Collection>
    ): List<CollectionType> =
        dbCollections.map { (_, collection) -> create(provider, collection) }


    /**
     * Synchronize the actual collection contents.
     *
     * @param provider Content provider client to access local collections
     * @param localCollections Collections to be synchronized
     * @param dbCollections Remote collection information
     */
    @VisibleForTesting
    internal fun syncCollectionContents(
        provider: ContentProviderClient,
        localCollections: List<CollectionType>,
        dbCollections: Map<HttpUrl, Collection>
    ) = localCollections.forEach { localCollection ->
        dbCollections[localCollection.collectionUrl?.toHttpUrl()]?.let { dbCollection ->
            syncCollection(provider, localCollection, dbCollection)
        }
    }

    /**
     * For collection specific sync preparations.
     *
     * @param provider Content provider for syncer specific authority
     * @return *true* to run the sync; *false* to abort
     */
    open fun prepare(provider: ContentProviderClient): Boolean = true

    /**
     * Gets all local collections (not from the database, but from the content provider).
     *
     * [Syncer] will remove collections which are returned by this method, but not by
     * [getDbSyncCollections], and add collections which are returned by [getDbSyncCollections], but not by this method.
     *
     * @param provider Content provider to access local collections
     * @return Local collections to be updated
     */
    abstract fun getLocalCollections(provider: ContentProviderClient): List<CollectionType>

    /**
     * Get the local database collections which are sync-enabled (should by synchronized).
     *
     * [Syncer] will remove collections which are returned by [getLocalCollections], but not by
     * this method, and add collections which are returned by this method, but not by [getLocalCollections].
     *
     * @param serviceId The CalDAV or CardDAV service (account) to be synchronized
     * @return Database collections to be synchronized
     */
    abstract fun getDbSyncCollections(serviceId: Long): List<Collection>

    /**
     * Updates an existing local collection (in the content provider) with remote collection information (from the DB).
     *
     * @param localCollection The local collection to be updated
     * @param remoteCollection The new remote collection information
     */
    abstract fun update(localCollection: CollectionType, remoteCollection: Collection)

    /**
     * Creates a new local collection (in the content provider) from remote collection information (from the DB).
     *
     * @param provider The content provider client to create the local collection
     * @param remoteCollection The remote collection to be created locally
     */
    abstract fun create(provider: ContentProviderClient, remoteCollection: Collection): CollectionType

    /**
     * Synchronizes local with remote collection contents.
     *
     * @param provider The content provider client to access the local collection to be updated
     * @param localCollection The local collection to be synchronized
     * @param remoteCollection The database collection representing the remote collection. Contains
     * remote address of the collection to be synchronized.
     */
    abstract fun syncCollection(provider: ContentProviderClient, localCollection: CollectionType, remoteCollection: Collection)

    /**
     * Prepares the sync:
     *
     * - acquire content provider
     * - handle occurring sync errors
     */
    operator fun invoke() {
        logger.log(Level.INFO, "$authority sync of $account initiated", extras.joinToString(", "))

        // Acquire ContentProviderClient
        try {
            context.contentResolver.acquireContentProviderClient(authority)
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "Missing permissions for authority $authority", e)
            null
        }.use { provider ->
            if (provider == null) {
                /* Can happen if
                 - we're not allowed to access the content provider, or
                 - the content provider is not available at all, for instance because the respective
                   system app, like "calendar storage" is disabled */
                logger.warning("Couldn't connect to content provider of authority $authority")
                syncResult.stats.numParseExceptions++ // hard sync error

                return // Don't continue without provider
            }

            // run sync
            try {
                val runSync = /* ose */ true
                if (runSync)
                    sync(provider)
                Unit
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

}