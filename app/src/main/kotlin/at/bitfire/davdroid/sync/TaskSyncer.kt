/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Sync logic for tasks in CalDAV collections ({@code VTODO}).
 */
class TaskSyncer @AssistedInject constructor(
    @ApplicationContext context: Context,
    serviceRepository: DavServiceRepository,
    collectionRepository: DavCollectionRepository,
    private val tasksSyncManagerFactory: TasksSyncManager.Factory,
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
    private val logger: Logger
): Syncer(context, serviceRepository, collectionRepository, account, extras, authority, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): TaskSyncer
    }

    private lateinit var taskProvider: TaskProvider

    private var updateColors = accountSettings.getManageCalendarColors()
    private val localTaskLists = mutableMapOf<HttpUrl, LocalTaskList>()
    private val localSyncTaskLists = mutableMapOf<HttpUrl, LocalTaskList>()

    override fun beforeSync() {

        // Acquire task provider
        val providerName = TaskProvider.ProviderName.fromAuthority(authority)
        taskProvider = try {
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

        // Find all task lists and sync-enabled task lists
        DmfsTaskList.find(account, taskProvider, LocalTaskList.Factory, null, null)
            .forEach { localTaskList ->
                localTaskList.syncId?.let { url ->
                    localTaskLists[url.toHttpUrl()] = localTaskList
                }
            }
        localTaskLists.forEach { (url, localCalendar) ->
            if (localCalendar.isSynced)
                localSyncTaskLists[url] = localCalendar
        }
    }

    override fun getServiceType(): String =
        Service.TYPE_CALDAV

    override fun getLocalResourceUrls(): List<HttpUrl?> =
        localTaskLists.keys.toList()

    override fun deleteLocalResource(url: HttpUrl?) {
        logger.log(Level.INFO, "Deleting obsolete local task list", url)
        localTaskLists[url]?.delete()
    }

    override fun updateLocalResource(collection: Collection) {
        logger.log(Level.FINE, "Updating local task list ${collection.url}", collection)
        localTaskLists[collection.url]?.update(collection, updateColors)
    }

    override fun createLocalResource(collection: Collection) {
        logger.log(Level.INFO, "Adding local task list", collection)
        LocalTaskList.create(account, taskProvider, collection)
    }

    override fun getLocalSyncableResourceUrls(): List<HttpUrl?> =
        localSyncTaskLists.keys.toList()

    override fun syncLocalResource(collection: Collection) {
        val taskList = localSyncTaskLists[collection.url]
            ?: return

        logger.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")

        val syncManager = tasksSyncManagerFactory.tasksSyncManager(
            account,
            accountSettings,
            httpClient.value,
            extras,
            authority,
            syncResult,
            taskList,
            collection
        )
        syncManager.performSync()
    }

    override fun afterSync() {
        taskProvider.close()
    }
}