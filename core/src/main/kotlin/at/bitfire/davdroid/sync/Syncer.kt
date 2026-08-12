/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentProviderClient
import android.content.Context
import android.os.DeadObjectException
import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.accounts.toAndroidAccount
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.ServiceType
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalDataStore
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.util.causedBy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.WillCloseWhenClosed
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * Base class for sync code.
 *
 * Contains generic sync code, equal for all sync authorities.
 *
 * @param accountId     [AccountId] of the account to synchronize
 * @param resync        whether re-synchronization is requested (`null` for normal sync)
 * @param syncResult    synchronization result, to be modified during sync
 * @param settings      snapshot of the account settings relevant for this sync run
 */
abstract class Syncer<StoreType: LocalDataStore<CollectionType>, CollectionType: LocalCollection<*>>(
    protected val accountId: AccountId,
    protected val resync: ResyncType?,
    protected val syncResult: SyncResult,
    protected val settings: SyncSettings
): Closeable {

    abstract val dataStore: StoreType

    @Inject
    lateinit var androidAccountManager: AndroidAccountManager

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var syncNotificationManagerFactory: SyncNotificationManager.Factory

    @Inject
    lateinit var syncValidator: Optional<SyncValidator>

    @ServiceType
    abstract val serviceType: String

    val syncNotificationManager by lazy {
        syncNotificationManagerFactory.create(accountId)
    }

    private val lazyHttpClient = lazy {
        httpClientBuilder.fromAccount(accountId).build()
    }

    /** authenticated HTTP client to be used by subclasses */
    @WillCloseWhenClosed
    val httpClient by lazyHttpClient

    /**
     * Creates, updates and/or deletes local collections (calendars, address books, etc) according to
     * remote collection information. Then syncs the actual entries (events, tasks, contacts, etc)
     * of the remaining, now up-to-date, collections.
     */
    @VisibleForTesting
    internal suspend fun sync(provider: ContentProviderClient) {
        // Collection type specific preparations
        if (!prepare(provider)) {
            logger.warning("Failed to prepare sync. Won't run sync.")
            return
        }

        // Find collections in database and provider which should be synced (are sync-enabled)
        val dbCollections = getSyncEnabledCollections()
        val localCollections = dataStore.getAll(accountId, provider)

        // Create/update/delete local collections according to DB
        val updatedLocalCollections = updateCollections(provider, localCollections, dbCollections)

        // Sync local collection contents (events, contacts, tasks)
        syncCollectionContents(provider, updatedLocalCollections, dbCollections)
    }

    /**
     * Finds sync enabled collections in database. They contain collection info which might have
     * been updated by collection refresh [at.bitfire.davdroid.servicedetection.DavResourceFinder].
     *
     * @return The sync enabled database collections as hash map identified by their ID
     */
    @VisibleForTesting
    internal suspend fun getSyncEnabledCollections(): Map<Long, Collection> {
        val dbCollections = mutableMapOf<Long, Collection>()
        serviceRepository.getByAccountIdAndType(accountId, serviceType)?.let { service ->
            for (dbCollection in getDbSyncCollections(service.id))
                dbCollections[dbCollection.id] = dbCollection
        }

        return dbCollections
    }

    /**
     * Updates and deletes local collections.
     *
     * - Updates local collections with possibly new info from corresponding database collections.
     * - Deletes local collections without a corresponding database collection.
     * - Creates local collections for database collections without local match.
     *
     * @param provider Content provider client, used to create local collections
     * @param localCollections The current local collections
     * @param dbCollections The current database collections, possibly containing new information
     *
     * @return Updated list of local collections (obsolete collections removed, new collections added)
     */
    @VisibleForTesting
    internal suspend fun updateCollections(
        provider: ContentProviderClient,
        localCollections: List<CollectionType>,
        dbCollections: Map<Long, Collection>
    ): List<CollectionType> {
        // create mutable copies of input
        val updatedLocalCollections = localCollections.toMutableList()
        val newDbCollections = dbCollections.toMutableMap()

        for (localCollection in localCollections) {
            val dbCollection = dbCollections.getOrDefault(localCollection.dbCollectionId, null)
            if (dbCollection == null) {
                // Collection not available in db = on server (anymore), delete and remove from the updated list
                logger.log(
                    Level.INFO,
                    "Deleting local collection {0} without matching remote collection",
                    arrayOf(localCollection.dbCollectionId)
                )
                dataStore.delete(localCollection)
                updatedLocalCollections -= localCollection
            } else {
                // Collection exists locally, update local collection and remove it from "to be created" map
                logger.log(
                    Level.FINE,
                    "Updating local collection {0} with {1}",
                    arrayOf(localCollection.dbCollectionId, dbCollection)
                )
                dataStore.update(provider, localCollection, dbCollection)
                newDbCollections -= dbCollection.id
            }
        }

        // Create local collections which are in DB, but don't exist locally yet
        if (newDbCollections.isNotEmpty()) {
            val toBeCreated = newDbCollections.values.toList()
            logger.log(Level.INFO, "Creating new local collections: {0}", arrayOf(toBeCreated.joinToString()))
            val newLocalCollections = createLocalCollections(provider, toBeCreated)
            // Add the newly created collections to the updated list
            updatedLocalCollections.addAll(newLocalCollections)
        }

        return updatedLocalCollections
    }

    /**
     * Creates new local collections from database collections.
     *
     * @param provider Content provider client to access local collections
     * @param dbCollections Database collections to be created as local collections
     *
     * @return Newly created local collections
     */
    @VisibleForTesting
    internal suspend fun createLocalCollections(
        provider: ContentProviderClient,
        dbCollections: List<Collection>
    ): List<CollectionType> =
        dbCollections.map { collection ->
            dataStore.create(provider, collection)
                ?: throw IllegalStateException("Couldn't create local collection for $collection")
        }

    /**
     * Synchronize the actual collection contents.
     *
     * @param provider Content provider client to access local collections
     * @param localCollections Collections to be synchronized
     * @param dbCollections Remote collection information
     */
    @VisibleForTesting
    internal suspend fun syncCollectionContents(
        provider: ContentProviderClient,
        localCollections: List<CollectionType>,
        dbCollections: Map<Long, Collection>
    ) {
        for (localCollection in localCollections) {
            val dbCollection = dbCollections[localCollection.dbCollectionId] ?: continue
            syncCollection(provider, localCollection, dbCollection)
        }
    }

    override fun close() {
        if (lazyHttpClient.isInitialized())
            httpClient.close()
    }

    /**
     * For collection specific sync preparations.
     *
     * @param provider Content provider for data store
     *
     * @return *true* to run the sync; *false* to abort
     */
    open fun prepare(provider: ContentProviderClient): Boolean = true

    /**
     * Get the local database collections which are sync-enabled (should by synchronized).
     *
     * @param serviceId The CalDAV or CardDAV service (account) to be synchronized
     *
     * @return Database collections to be synchronized
     */
    abstract fun getDbSyncCollections(serviceId: Long): List<Collection>

    /**
     * Synchronizes local with remote collection contents.
     *
     * @param provider The content provider client to access the local collection to be updated
     * @param localCollection The local collection to be synchronized
     * @param remoteCollectionInfo The database collection representing the remote collection. Contains
     * remote address of the collection to be synchronized.
     */
    abstract suspend fun syncCollection(
        provider: ContentProviderClient,
        localCollection: CollectionType,
        remoteCollectionInfo: Collection
    )

    /**
     * Acquires the content provider, runs the sync, and handles exceptions that are not handled by the SyncManager itself.
     */
    suspend operator fun invoke() {
        logger.info("${dataStore.authority} sync of $accountId initiated (resync=$resync)")

        try {
            dataStore.acquireContentProvider(throwOnMissingPermissions = true)
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "Missing permissions for content provider authority ${dataStore.authority}", e)
            /* Don't show a notification here without possibility to permanently dismiss it!
            Some users intentionally don't grant all permissions for what is syncable. */
            return
        }.use { provider ->
            if (provider == null) {
                /* Content provider is not available at all.
                I.E. system app (like "calendar storage") is missing or disabled */
                logger.warning("Couldn't connect to content provider of authority ${dataStore.authority}")
                syncNotificationManager.notifyProviderError(dataStore.authority)
                syncResult.hardError = true
                return // Don't continue without provider
            }

            // Dismiss previous content provider error notification
            syncNotificationManager.dismissProviderError(dataStore.authority)

            // run sync
            try {
                val runSync = syncValidator.getOrNull()?.let { instance ->
                    logger.info("Registered sync validator: ${instance::class.java.name}")
                    instance.beforeSync(accountId.toAndroidAccount())
                } ?: /* no sync validator, in OSE for example */ true

                if (runSync)
                    sync(provider)

            } catch (e: Exception) {
                /* Handle sync exceptions. Note that most exceptions that occur during synchronization of a specific
                collection are already handled in SyncManager. The exceptions here usually
                - have occurred during Syncer operation (for instance when creating/deleting local collections) —
                  content provider access here can also throw a DeadObjectException wrapped by the storage layer,
                - or have been re-thrown from SyncManager (like the unwrapped DeadObjectException). */
                if (e.causedBy<DeadObjectException>() != null) {
                    // content provider process died, or (Android 14+) was killed for being frozen/cached; see SyncExceptionHandler
                    logger.log(Level.WARNING, "Received DeadObjectException, treating as soft error", e)
                    syncResult.softError = true
                } else when (e) {
                    is InvalidAccountException ->
                        logger.log(Level.WARNING, "Account was removed during synchronization", e)

                    else -> {
                        logger.log(Level.SEVERE, "Couldn't sync ${dataStore.authority}", e)
                        syncResult.hardError = true
                    }
                }

            } finally {
                logger.info("${dataStore.authority} sync of $accountId finished")
            }
        }
    }

}