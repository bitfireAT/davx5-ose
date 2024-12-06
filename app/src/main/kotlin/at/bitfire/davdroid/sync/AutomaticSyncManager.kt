/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.ical4android.TaskProvider
import dagger.Lazy
import javax.inject.Inject

/**
 * Manages automatic synchronization, that is:
 *
 * - synchronization in given intervals, and
 * - synchronization on local data changes.
 */
class AutomaticSyncManager @Inject constructor(
    private val frameworkIntegration: SyncFrameworkIntegration,
    private val workerManager: SyncWorkerManager,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    /**
     * Disables automatic synchronization for the given account and datatype.
     */
    fun disable(account: Account, dataType: SyncDataType) {
        val authorities = mutableListOf<String>()

        when (dataType) {
            SyncDataType.CONTACTS -> authorities += ContactsContract.AUTHORITY
            SyncDataType.EVENTS -> authorities += CalendarContract.AUTHORITY
            SyncDataType.TASKS -> authorities += TaskProvider.TASK_PROVIDERS.map { it.authority }
        }

        for (authority in authorities)
            frameworkIntegration.disableSyncAbility(account, authority)
    }

    /**
     * Enables automatic synchronization for the given account and data type and sets it to the given interval:
     *
     * 1. Sets up periodic sync for the given data type with the given interval.
     * 2. Enables sync in the sync framework for the given data type and sets up periodic sync with the given interval.
     *
     * If [dataType] is [SyncDataType.TASKS] and not tasks provider is set, this method does nothing.
     *
     * @param account   the account to synchronize
     * @param dataType  kind of data to synchronize
     * @param wifiOnly  whether to synchronize only on Wi-Fi
     * @param minutes   interval in minutes, or `null` to disable periodic sync (only sync on local data changes)
     */
    fun setSyncInterval(account: Account, dataType: SyncDataType, wifiOnly: Boolean, minutes: Int?) {
        // periodic sync worker
        if (minutes != null)
            workerManager.enablePeriodic(account, dataType, minutes*60L, wifiOnly)
        else
            workerManager.disablePeriodic(account, dataType)

        // sync on local data changes
        val authority = dataType.toAuthority { tasksAppManager.get().currentProvider() } ?: return
        if (minutes != null) {
            if (!frameworkIntegration.isSyncable(account, authority))
                frameworkIntegration.enableSyncAbility(account, authority)
            frameworkIntegration.enableSyncOnContentChange(account, authority)
        } else {
            frameworkIntegration.disableSyncOnContentChange(account, authority)
        }
    }

}