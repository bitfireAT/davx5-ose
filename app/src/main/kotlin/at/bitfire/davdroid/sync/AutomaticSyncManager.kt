/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import javax.inject.Inject

/**
 * Manages automatic synchronization, that is:
 *
 * - synchronization in given intervals, and
 * - synchronization on local data changes.
 */
class AutomaticSyncManager @Inject constructor(
    private val syncFramework: SyncFrameworkIntegration,
    private val workerManager: SyncWorkerManager
) {

    /**
     * Disable automatic synchronization for the given account and data type.
     */
    fun disable(account: Account, authority: String) {
        workerManager.disablePeriodic(account, authority)
        syncFramework.disableSyncAbility(account, authority)
    }

    /**
     * Enables automatic synchronization for the given account and data type and sets it to the given interval:
     *
     * 1. Sets up periodic sync for the given data type with the given interval.
     * 2. Enables sync in the sync framework for the given data type and sets up periodic sync with the given interval.
     *
     * @param account   the account to synchronize
     * @param authority the authority to synchronize
     * @param wifiOnly  whether to synchronize only on Wi-Fi
     * @param seconds   interval in seconds, or `null` to disable periodic sync (only sync on local data changes)
     */
    fun setSyncInterval(account: Account, authority: String, seconds: Long?, wifiOnly: Boolean) {
        if (seconds != null) {
            // update sync workers (needs already updated sync interval in AccountSettings)
            workerManager.enablePeriodic(account, authority, seconds, wifiOnly)
        } else
            workerManager.disablePeriodic(account, authority)

        // Also enable/disable content change triggered syncs
        if (seconds != null)
            syncFramework.enableSyncOnContentChange(account, authority)
        else
            syncFramework.disableSyncOnContentChange(account, authority)
    }

}