/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentProviderClient
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.resource.LocalTaskListStore
import at.bitfire.davdroid.resource.remote.CalDavCollection
import at.bitfire.davdroid.resource.remote.CalendarQueryFilter
import at.bitfire.synctools.storage.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.logging.Level

/**
 * Sync logic for tasks in CalDAV collections ({@code VTODO}).
 */
class TaskSyncer @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted val providerName: TaskProvider.ProviderName,
    @Assisted resync: ResyncType?,
    @Assisted syncResult: SyncResult,
    @Assisted settings: SyncSettings,
    localTaskListStoreFactory: LocalTaskListStore.Factory,
    private val tasksAppManager: dagger.Lazy<TasksAppManager>,
    private val tasksSyncManagerFactory: TasksSyncManager.Factory,
) : Syncer<LocalTaskListStore, LocalTaskList>(accountId, resync, syncResult, settings) {

    @AssistedFactory
    interface Factory {
        fun create(
            accountId: AccountId,
            providerName: TaskProvider.ProviderName,
            resyncType: ResyncType?,
            syncResult: SyncResult,
            settings: SyncSettings
        ): TaskSyncer
    }

    override val dataStore = localTaskListStoreFactory.create(providerName)

    override val serviceType: String
        get() = Service.TYPE_CALDAV


    override fun prepare(provider: ContentProviderClient): Boolean {
        // Don't sync if task provider is too old
        try {
            TaskProvider.checkVersion(context, providerName)
        } catch (e: TaskProvider.ProviderTooOldException) {
            tasksAppManager.get().notifyProviderTooOld(e)
            syncResult.hardError = true
            return false // Don't sync
        }

        // make sure account can be seen by task provider
        androidAccountManager.ensureAccountVisibility(accountId, providerName.packageName)

        return true
    }

    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncTaskLists(serviceId)

    override suspend fun syncCollection(
        provider: ContentProviderClient,
        localCollection: LocalTaskList,
        remoteCollectionInfo: Collection
    ) {
        logger.log(
            Level.INFO,
            "Synchronizing task list {0} with database collection ID: {1}",
            arrayOf(localCollection.dmfsTaskList.id, localCollection.dbCollectionId)
        )

        val syncManager = tasksSyncManagerFactory.tasksSyncManager(
            accountId = accountId,
            httpClient = httpClient,
            syncResult = syncResult,
            localCollection = localCollection,
            collectionInfo = remoteCollectionInfo,
            remoteCollection = CalDavCollection(
                httpClient = httpClient,
                url = remoteCollectionInfo.url,
                filter = CalendarQueryFilter(components = listOf("VTODO"))
            ),
            resync = resync,
            settings = settings
        )
        syncManager.performSync()
    }

}