/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level
import javax.inject.Inject

/**
 * Sync logic for tasks in CalDAV collections ({@code VTODO}).
 */
class TaskSyncer @Inject constructor(
    @ApplicationContext context: Context,
    db: AppDatabase,
    private val tasksSyncManagerFactory: TasksSyncManager.Factory
): Syncer(context, db) {

    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {

        // 0. preparations

        // Acquire task provider
        val providerName = TaskProvider.ProviderName.fromAuthority(authority)
        val taskProvider = try {
            TaskProvider.fromProviderClient(context, providerName, provider)
        } catch (e: TaskProvider.ProviderTooOldException) {
            TaskUtils.notifyProviderTooOld(context, e)
            syncResult.databaseError = true
            return // Don't sync
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

        val accountSettings = AccountSettings(context, account)

        // 1. find task collections to be synced
        val remoteTaskLists = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)
        if (service != null)
            for (collection in db.collectionDao().getSyncTaskLists(service.id))
                remoteTaskLists[collection.url] = collection
        val updateColors = accountSettings.getManageCalendarColors()

        // 2. delete/update local task lists
        for (list in DmfsTaskList.find(account, taskProvider, LocalTaskList.Factory, null, null))
            list.syncId?.let {
                val url = it.toHttpUrl()
                val info = remoteTaskLists[url]
                if (info == null) {
                    Logger.log.fine("Deleting obsolete local task list $url")
                    list.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.log(Level.FINE, "Updating local task list $url", info)
                    list.update(info, updateColors)
                    // we already have a local task list for this remote collection, don't take into consideration anymore
                    remoteTaskLists -= url
                }
            }

        // 3. create new local task lists
        for ((_,info) in remoteTaskLists) {
            Logger.log.log(Level.INFO, "Adding local task list", info)
            LocalTaskList.create(account, taskProvider, info)
        }

        // 4. sync local task lists
        val localTaskLists = DmfsTaskList
            .find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)
        for (localTaskList in localTaskLists) {
            Logger.log.info("Synchronizing task list #${localTaskList.id} [${localTaskList.syncId}]")

            val url = localTaskList.syncId?.toHttpUrl()
            remoteTaskLists[url]?.let { collection ->
                val syncManager = tasksSyncManagerFactory.tasksSyncManager(
                    account,
                    accountSettings,
                    httpClient.value,
                    extras,
                    authority,
                    syncResult,
                    localTaskList,
                    collection
                )
                syncManager.performSync()
            }
        }

        Logger.log.info("Task sync complete")
    }
}