/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.TaskProvider
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level

/**
 * Sync logic for tasks in CalDAV collections ({@code VTODO}).
 */
class TaskSyncer(context: Context): Syncer(context) {

    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        try {
            val providerName = TaskProvider.ProviderName.fromAuthority(authority)
            val taskProvider = TaskProvider.fromProviderClient(context, providerName, provider)

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

            updateLocalTaskLists(taskProvider, account, accountSettings)

            val taskLists = AndroidTaskList
                .find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)
            for (taskList in taskLists) {
                Logger.log.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")
                TasksSyncManager(context, account, accountSettings, httpClient.value, extras, authority, syncResult, taskList).performSync()
            }
        } catch (e: TaskProvider.ProviderTooOldException) {
            SyncUtils.notifyProviderTooOld(context, e)
            syncResult.databaseError = true
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't sync task lists", e)
            syncResult.databaseError = true
        }

        Logger.log.info("Task sync complete")
    }

    private fun updateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)

        val remoteTaskLists = mutableMapOf<HttpUrl, Collection>()
        if (service != null)
            for (collection in db.collectionDao().getSyncTaskLists(service.id)) {
                remoteTaskLists[collection.url] = collection
            }

        // delete/update local task lists
        val updateColors = settings.getManageCalendarColors()

        for (list in AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null))
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

        // create new local task lists
        for ((_,info) in remoteTaskLists) {
            Logger.log.log(Level.INFO, "Adding local task list", info)
            LocalTaskList.create(account, provider, info)
        }
    }
}