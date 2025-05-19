/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.os.Build
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.resource.LocalTaskListStore
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking

/**
 * Sync logic for tasks in CalDAV collections ({@code VTODO}).
 */
class TaskSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted val providerName: TaskProvider.ProviderName,
    @Assisted resync: ResyncType,
    @Assisted syncResult: SyncResult,
    localTaskListStoreFactory: LocalTaskListStore.Factory,
    private val tasksAppManager: dagger.Lazy<TasksAppManager>,
    private val tasksSyncManagerFactory: TasksSyncManager.Factory,
): Syncer<LocalTaskListStore, LocalTaskList>(account, resync, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, providerName: TaskProvider.ProviderName, resyncType: ResyncType?, syncResult: SyncResult): TaskSyncer
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
            syncResult.contentProviderError = true
            return false // Don't sync
        }

        // make sure account can be seen by task provider
        if (Build.VERSION.SDK_INT >= 26) {
            /* Warning: If setAccountVisibility is called, Android 12 broadcasts the
               AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION Intent. This cancels running syncs
               and starts them again! So make sure setAccountVisibility is only called when necessary. */
            val am = AccountManager.get(context)
            if (am.getAccountVisibility(account, providerName.packageName) != AccountManager.VISIBILITY_VISIBLE)
                am.setAccountVisibility(account, providerName.packageName, AccountManager.VISIBILITY_VISIBLE)
        }
        return true
    }

    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncTaskLists(serviceId)

    override fun syncCollection(provider: ContentProviderClient, localCollection: LocalTaskList, remoteCollection: Collection) {
        logger.info("Synchronizing task list ${localCollection.id} with database collection ID: ${localCollection.dbCollectionId}")

        val syncManager = tasksSyncManagerFactory.tasksSyncManager(
            account,
            httpClient.value,
            dataStore.authority,
            syncResult,
            localCollection,
            remoteCollection,
            resync
        )
        runBlocking {
            syncManager.performSync()
        }
    }

}