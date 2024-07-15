/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level

/**
 * Sync logic for tasks in CalDAV collections ({@code VTODO}).
 */
class TaskSyncer @AssistedInject constructor(
    @ApplicationContext context: Context,
    db: AppDatabase,
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
    private val tasksSyncManagerFactory: TasksSyncManager.Factory
): Syncer(context, db, account, extras, authority, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): TaskSyncer
    }

    override fun sync() {

        // 0. preparations

        // acquire ContentProviderClient
        val provider = try {
            context.contentResolver.acquireContentProviderClient(authority)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Missing permissions for authority $authority", e)
            null
        }

        if (provider == null) {
            /* Can happen if
             - we're not allowed to access the content provider, or
             - the content provider is not available at all, for instance because the respective
               system app, like "calendar storage" is disabled */
            Logger.log.warning("Couldn't connect to content provider of authority $authority")
            syncResult.stats.numParseExceptions++ // hard sync error
            return
        }

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
        val remoteCollections = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)
        if (service != null)
            for (collection in db.collectionDao().getSyncTaskLists(service.id))
                remoteCollections[collection.url] = collection

        // 2. delete/update local task lists and determine new remote collections
        val updateColors = accountSettings.getManageCalendarColors()
        val newCollections = HashMap(remoteCollections)
        for (list in DmfsTaskList.find(account, taskProvider, LocalTaskList.Factory, null, null))
            list.syncId?.let {
                val url = it.toHttpUrl()
                val info = remoteCollections[url]
                if (info == null) {
                    Logger.log.fine("Deleting obsolete local task list $url")
                    list.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.log(Level.FINE, "Updating local task list $url", info)
                    list.update(info, updateColors)
                    // we already have a local task list for this remote collection, don't create a new local task list
                    newCollections -= url
                }
            }

        // 3. create new local task lists
        for ((_,info) in newCollections) {
            Logger.log.log(Level.INFO, "Adding local task list", info)
            LocalTaskList.create(account, taskProvider, info)
        }

        // 4. sync local task lists
        val localTaskLists = DmfsTaskList
            .find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)
        for (localTaskList in localTaskLists) {
            Logger.log.info("Synchronizing task list #${localTaskList.id} [${localTaskList.syncId}]")

            val url = localTaskList.syncId?.toHttpUrl()
            remoteCollections[url]?.let { collection ->
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

        // close content provider client which is acquired above
        provider.close()

        Logger.log.info("Task sync complete")
    }

    override fun getSyncCollections(serviceId: Long): List<Collection> {
        TODO("Not yet implemented")
    }

    override fun preparation() {
        TODO("Not yet implemented")
    }

    override fun getServiceType(): String {
        TODO("Not yet implemented")
    }

    override fun getLocalResourceUrls(): List<HttpUrl?> {
        TODO("Not yet implemented")
    }

    override fun deleteLocalResource(url: HttpUrl?) {
        TODO("Not yet implemented")
    }

    override fun updateLocalResource(collection: Collection) {
        TODO("Not yet implemented")
    }

    override fun createLocalResource(collection: Collection) {
        TODO("Not yet implemented")
    }

    override fun getLocalSyncableResourceUrls(): List<HttpUrl?> {
        TODO("Not yet implemented")
    }

    override fun syncLocalResource(collection: Collection) {
        TODO("Not yet implemented")
    }
}