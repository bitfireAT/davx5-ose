/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.dmfs.tasks.contract.TaskContract.TaskLists
import java.util.logging.Level

/**
 * Sync logic for tasks in CalDAV collections ({@code VTODO}).
 */
class TaskSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted override val authority: String,
    @Assisted extras: Array<String>,
    @Assisted syncResult: SyncResult,
    private val tasksAppManager: dagger.Lazy<TasksAppManager>,
    private val tasksSyncManagerFactory: TasksSyncManager.Factory,
): Syncer<LocalTaskList>(account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, authority: String, extras: Array<String>, syncResult: SyncResult): TaskSyncer
    }

    private lateinit var taskProvider: TaskProvider

    override val serviceType: String
        get() = Service.TYPE_CALDAV


    override fun localSyncCollections(provider: ContentProviderClient): List<LocalTaskList>
        = DmfsTaskList.find(account, taskProvider, LocalTaskList.Factory, "${TaskLists.SYNC_ENABLED}!=0", null)

    override fun prepare(provider: ContentProviderClient): Boolean {
        // Acquire task provider
        val providerName = TaskProvider.ProviderName.fromAuthority(authority)
        taskProvider = try {
            TaskProvider.fromProviderClient(context, providerName, provider)
        } catch (e: TaskProvider.ProviderTooOldException) {
            tasksAppManager.get().notifyProviderTooOld(e)
            syncResult.databaseError = true
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

    override fun getSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncTaskLists(serviceId)

    override fun update(localCollection: LocalTaskList, remoteCollection: Collection) {
        logger.log(Level.FINE, "Updating local task list ${remoteCollection.url}", remoteCollection)
        localCollection.update(remoteCollection, accountSettings.getManageCalendarColors())
    }

    override fun create(provider: ContentProviderClient, remoteCollection: Collection): LocalTaskList {
        logger.log(Level.INFO, "Adding local task list", remoteCollection)
        val uri = LocalTaskList.create(account, taskProvider, remoteCollection)
        return DmfsTaskList.findByID(account, taskProvider, LocalTaskList.Factory, ContentUris.parseId(uri))
    }

    override fun syncCollection(provider: ContentProviderClient, localCollection: LocalTaskList, remoteCollection: Collection) {
        logger.info("Synchronizing task list #${localCollection.id} [${localCollection.syncId}]")

        val syncManager = tasksSyncManagerFactory.tasksSyncManager(
            account,
            accountSettings,
            httpClient.value,
            extras,
            authority,
            syncResult,
            localCollection,
            remoteCollection
        )
        syncManager.performSync()
    }

}