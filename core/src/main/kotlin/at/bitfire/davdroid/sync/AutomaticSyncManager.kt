/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import androidx.annotation.WorkerThread
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import javax.inject.Inject

/**
 * Manages automatic synchronization, that is:
 *
 * - synchronization in given intervals, and
 * - synchronization on local data changes.
 *
 * Integrates with both the periodic sync workers and the sync framework (over [SyncFrameworkSettingsUpdater]). So this
 * class should be used when the caller just wants to update the automatic sync, without needing to know about the
 * underlying details.
 *
 * Automatic synchronization stands in contrast to manual synchronization, which is only triggered by the user.
 */
class AutomaticSyncManager @Inject constructor(
    private val accountSettingsFactory: AccountSettingsFactory,
    private val androidAccountManager: AndroidAccountManager,
    private val syncFrameworkSettingsUpdater: SyncFrameworkSettingsUpdater,
    private val workerManager: SyncWorkerManager
) {

    /**
     * Updates automatic synchronization of the given account and all data types according to the account settings.
     *
     * If there's a [Service] for the given account and data type, automatic sync is enabled (with details from [AccountSettings]).
     * Otherwise, automatic synchronization is disabled.
     *
     * @param accountId [AccountId] of the account for which automatic synchronization shall be updated
     */
    @WorkerThread
    fun updateAutomaticSync(accountId: AccountId) {
        for (dataType in SyncDataType.entries)
            updateAutomaticSync(accountId, dataType)
    }

    /**
     * Updates automatic synchronization of the given account and data type according to the account services and settings:
     *
     * 1. Enables/Disables periodic sync worker for the given data type with the interval from [AccountSettings].
     * 2. Updates the sync framework settings of the Android account.
     *
     * If there's a [Service] for the given account and data type, automatic sync may be enabled if sync interval is set
     * in [AccountSettings].
     * Otherwise, automatic synchronization is disabled.
     *
     * @param accountId [AccountId] of the account for which automatic synchronization shall be updated
     * @param dataType  sync data type for which automatic synchronization shall be updated
     */
    @WorkerThread
    fun updateAutomaticSync(accountId: AccountId, dataType: SyncDataType) {
        // 1. Update sync workers (needs already updated sync interval in AccountSettings).
        val accountSettings =
            if (syncFrameworkSettingsUpdater.isAutomaticSyncPossible(accountId, dataType))
                accountSettingsFactory.create(accountId)
            else
                null
        val syncInterval = accountSettings?.getSyncInterval(dataType)
        if (accountSettings != null && syncInterval != null)
            workerManager.enablePeriodic(accountId, dataType, syncInterval, accountSettings.getSyncWifiOnly())
        else
            workerManager.disablePeriodic(accountId, dataType)

        // 2. Update sync framework settings.
        val account = androidAccountManager.getAndroidAccount(accountId)
        syncFrameworkSettingsUpdater.update(accountId, account, dataType)
    }

}
