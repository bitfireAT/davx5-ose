/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.accounts.toAccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalDavTaskList
import at.bitfire.davdroid.resource.LocalDavTaskListStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.logging.Level

/**
 * Sync logic for tasks in CalDAV collections (`VTODO`), storing tasks in the DAVx⁵-hosted tasks
 * provider. Structurally mirrors [TaskSyncer] (DMFS backend) — simpler, because the DAVx⁵-hosted
 * provider is always present, doesn't need a minimum-version check, and doesn't need
 * `setAccountVisibility` (it's part of this app, so it can already see all of this app's
 * accounts).
 */
class DavTaskSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted resync: ResyncType?,
    @Assisted syncResult: SyncResult,
    @Assisted settings: SyncSettings,
    localDavTaskListStore: LocalDavTaskListStore,
    private val davTasksSyncManagerFactory: DavTasksSyncManager.Factory,
) : Syncer<LocalDavTaskListStore, LocalDavTaskList>(account, resync, syncResult, settings) {

    @AssistedFactory
    interface Factory {
        fun create(
            account: Account,
            resyncType: ResyncType?,
            syncResult: SyncResult,
            settings: SyncSettings
        ): DavTaskSyncer
    }

    override val dataStore = localDavTaskListStore

    override val serviceType: String
        get() = Service.TYPE_CALDAV


    override fun prepare(provider: ContentProviderClient): Boolean = true

    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncTaskLists(serviceId)

    override suspend fun syncCollection(
        provider: ContentProviderClient,
        localCollection: LocalDavTaskList,
        remoteCollection: Collection
    ) {
        logger.log(
            Level.INFO,
            "Synchronizing task list {0} with database collection ID: {1}",
            arrayOf(localCollection.davTaskList.id, localCollection.dbCollectionId)
        )

        val syncManager = davTasksSyncManagerFactory.davTasksSyncManager(
            account.toAccountId(),
            httpClient,
            syncResult,
            localCollection,
            remoteCollection,
            resync,
            settings
        )
        syncManager.performSync()
    }

}
