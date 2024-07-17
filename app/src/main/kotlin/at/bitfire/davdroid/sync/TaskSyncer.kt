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
import org.dmfs.tasks.contract.TaskContract
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
    @Assisted syncResult: SyncResult,
    @Assisted override val authority: String,
    private val logger: Logger
): Syncer<LocalTaskList>(context, serviceRepository, collectionRepository, account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): TaskSyncer
    }

    private lateinit var taskProvider: TaskProvider

    private var updateColors = accountSettings.getManageCalendarColors()

    override val serviceType: String
        get() = Service.TYPE_CALDAV
    override val localCollections: List<LocalTaskList>
        get() = DmfsTaskList.find(account, taskProvider, LocalTaskList.Factory, null, null)
    override val localSyncCollections: List<LocalTaskList>
        get() = DmfsTaskList.find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)

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
    }

    override fun getUrl(localCollection: LocalTaskList): HttpUrl? =
        localCollection.syncId?.toHttpUrl()

    override fun delete(localCollection: LocalTaskList) {
        logger.log(Level.INFO, "Deleting obsolete local task list", localCollection.syncId)
        localCollection.delete()
    }

    override fun update(localCollection: LocalTaskList, remoteCollection: Collection) {
        logger.log(Level.FINE, "Updating local task list ${remoteCollection.url}", remoteCollection)
        localCollection.update(remoteCollection, updateColors)
    }

    override fun create(remoteCollection: Collection) {
        logger.log(Level.INFO, "Adding local task list", remoteCollection)
        LocalTaskList.create(account, taskProvider, remoteCollection)
    }

    override fun syncCollection(localCollection: LocalTaskList, remoteCollection: Collection) {
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

    override fun afterSync() {
        taskProvider.close()
    }
}