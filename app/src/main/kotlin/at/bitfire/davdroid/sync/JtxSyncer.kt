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
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxCollectionStore
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Sync logic for jtx board
 */
class JtxSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted syncResult: SyncResult,
    localJtxCollectionStore: LocalJtxCollectionStore,
    private val jtxSyncManagerFactory: JtxSyncManager.Factory,
    private val tasksAppManager: dagger.Lazy<TasksAppManager>
): Syncer<LocalJtxCollectionStore, LocalJtxCollection>(account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, syncResult: SyncResult): JtxSyncer
    }

    override val dataStore = localJtxCollectionStore

    override val serviceType: String
        get() = Service.TYPE_CALDAV
    override val authority: String
        get() = TaskProvider.ProviderName.JtxBoard.authority


    override fun prepare(provider: ContentProviderClient): Boolean {
        // check whether jtx Board is new enough
        try {
            TaskProvider.checkVersion(context, TaskProvider.ProviderName.JtxBoard)
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
            if (am.getAccountVisibility(account, TaskProvider.ProviderName.JtxBoard.packageName) != AccountManager.VISIBILITY_VISIBLE)
                am.setAccountVisibility(account, TaskProvider.ProviderName.JtxBoard.packageName, AccountManager.VISIBILITY_VISIBLE)
        }
        return true
    }

    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncJtxCollections(serviceId)

    override fun syncCollection(provider: ContentProviderClient, localCollection: LocalJtxCollection, remoteCollection: Collection) {
        logger.info("Synchronizing jtx collection $localCollection")

        val syncManager = jtxSyncManagerFactory.jtxSyncManager(
            account,
            accountSettings,
            extras,
            httpClient.value,
            authority,
            syncResult,
            localCollection,
            remoteCollection
        )
        syncManager.performSync()
    }

}