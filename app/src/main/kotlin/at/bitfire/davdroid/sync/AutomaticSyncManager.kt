/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
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
     * Enables automatic synchronization and sets it to the given interval.
     *
     * @param account   the account to synchronize
     * @param dataType  kind of data to synchronize
     * @param wifiOnly  whether to synchronize only on Wi-Fi
     * @param minutes   interval in minutes, or `null` to disable periodic sync (only sync on local data changes)
     */
    fun setSyncInterval(account: Account, dataType: SyncDataType, wifiOnly: Boolean, minutes: Int?) {
        val authority = dataType.toAuthority { tasksAppManager.get().currentProvider() } ?: return

        // periodic sync worker
        if (minutes != null)
            workerManager.enablePeriodic(account, authority, minutes*60L, wifiOnly)
        else
            workerManager.disablePeriodic(account, authority)

        // sync on local data changes
        if (minutes != null) {
            if (!frameworkIntegration.isSyncable(account, authority))
                frameworkIntegration.enableSyncAbility(account, authority)
            frameworkIntegration.enableSyncOnContentChange(account, authority)
        } else {
            frameworkIntegration.disableSyncOnContentChange(account, authority)
        }
    }

}