/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentProviderClient
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.local.LocalJtxCollection
import at.bitfire.davdroid.resource.local.LocalJtxCollectionStore
import at.bitfire.davdroid.resource.remote.CalDavCollection
import at.bitfire.davdroid.resource.remote.CalendarQueryFilter
import at.bitfire.synctools.storage.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Sync logic for jtx board
 */
class JtxSyncer @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted resync: ResyncType?,
    @Assisted syncResult: SyncResult,
    @Assisted settings: SyncSettings,
    localJtxCollectionStore: LocalJtxCollectionStore,
    private val jtxSyncManagerFactory: JtxSyncManager.Factory,
    private val tasksAppManager: dagger.Lazy<TasksAppManager>
) : Syncer<LocalJtxCollectionStore, LocalJtxCollection>(accountId, resync, syncResult, settings) {

    @AssistedFactory
    interface Factory {
        fun create(
            accountId: AccountId,
            resyncType: ResyncType?,
            syncResult: SyncResult,
            settings: SyncSettings
        ): JtxSyncer
    }

    override val dataStore = localJtxCollectionStore

    override val serviceType: String
        get() = Service.TYPE_CALDAV


    override fun prepare(provider: ContentProviderClient): Boolean {
        // check whether jtx Board is new enough
        try {
            TaskProvider.checkVersion(context, TaskProvider.ProviderName.JtxBoard)
        } catch (e: TaskProvider.ProviderTooOldException) {
            tasksAppManager.get().notifyProviderTooOld(e)
            syncResult.hardError = true
            return false // Don't sync
        }

        // make sure account can be seen by jtx board
        androidAccountManager.ensureAccountVisibility(accountId, TaskProvider.ProviderName.JtxBoard.packageName)

        return true
    }

    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncJtxCollections(serviceId)

    override suspend fun syncCollection(
        provider: ContentProviderClient,
        localCollection: LocalJtxCollection,
        remoteCollectionInfo: Collection
    ) {
        logger.info("Synchronizing jtx collection $localCollection")

        val syncManager = jtxSyncManagerFactory.jtxSyncManager(
            accountId = accountId,
            httpClient = httpClient,
            syncResult = syncResult,
            localCollection = localCollection,
            collectionInfo = remoteCollectionInfo,
            remoteCollection = CalDavCollection(
                httpClient = httpClient,
                url = remoteCollectionInfo.url,
                filter = CalendarQueryFilter(components = buildList {
                    if (localCollection.supportsVTODO) add("VTODO")
                    if (localCollection.supportsVJOURNAL) add("VJOURNAL")
                })
            ),
            resync = resync,
            settings = settings
        )
        syncManager.performSync()
    }

}