/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentUris
import android.os.Build
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.PrincipalRepository
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.TaskProvider
import at.techbee.jtx.JtxContract
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.logging.Level

/**
 * Sync logic for jtx board
 */
class JtxSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted syncResult: SyncResult,
    private val jtxSyncManagerFactory: JtxSyncManager.Factory,
    private val principalRepository: PrincipalRepository,
    private val tasksAppManager: dagger.Lazy<TasksAppManager>
): Syncer<LocalJtxCollection>(account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, syncResult: SyncResult): JtxSyncer
    }

    override val serviceType: String
        get() = Service.TYPE_CALDAV
    override val authority: String
        get() = TaskProvider.ProviderName.JtxBoard.authority


    override fun getLocalCollections(provider: ContentProviderClient): List<LocalJtxCollection>
        = JtxCollection.find(account, provider, context, LocalJtxCollection.Factory, null, null)

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

    override fun update(localCollection: LocalJtxCollection, remoteCollection: Collection) {
        logger.log(Level.FINE, "Updating local jtx collection ${remoteCollection.url}", remoteCollection)
        val owner = remoteCollection.ownerId?.let { principalRepository.get(it) }
        localCollection.updateCollection(remoteCollection, owner, accountSettings.getManageCalendarColors())
    }

    override fun create(provider: ContentProviderClient, remoteCollection: Collection): LocalJtxCollection {
        logger.log(Level.INFO, "Adding local jtx collection", remoteCollection)
        val owner = remoteCollection.ownerId?.let { principalRepository.get(it) }
        val uri = LocalJtxCollection.create(account, provider, remoteCollection, owner)
        return JtxCollection.find(
            account,
            provider,
            context,
            LocalJtxCollection.Factory,
            "${JtxContract.JtxCollection.ID} = ?",
            arrayOf("${ContentUris.parseId(uri)}")
        ).first()
    }

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