/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.Manifest.permission_group.CONTACTS
import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manages automatic synchronization, that is:
 *
 * - synchronization in given intervals, and
 * - synchronization on local data changes.
 *
 * Integrates with both the periodic sync workers and the sync framework. So this class should be used when
 * the caller just wants to update the automatic sync, without needing to know about the underlying details.
 */
class AutomaticSyncManager @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext private val context: Context,
    private val syncFramework: SyncFrameworkIntegration,
    private val tasksAppManager: TasksAppManager,
    private val workerManager: SyncWorkerManager
) {

    /**
     * Disable automatic synchronization for the given account and data type.
     */
    fun disable(account: Account, dataType: SyncDataType) {
        workerManager.disablePeriodic(account, dataType)

        for (authority in dataType.possibleAuthorities(context))
            syncFramework.disableSyncAbility(account, authority)
    }

    /**
     * Enables automatic synchronization for the given account and data type and sets it to the given interval:
     *
     * 1. Sets up periodic sync for the given data type with the given interval.
     * 2. Enables sync in the sync framework for the given data type and sets up periodic sync with the given interval.
     *
     * @param account   the account to synchronize
     * @param dataType  the data type to synchronize
     * @param wifiOnly  whether to synchronize only on Wi-Fi (default value takes the account setting)
     * @param seconds   interval in seconds, or `null` to disable periodic sync (only sync on local data changes)
     */
    fun enableOrUpdate(
        account: Account,
        dataType: SyncDataType,
        seconds: Long?,
        wifiOnly: Boolean = accountSettingsFactory.create(account).getSyncWifiOnly()
    ) {
        if (seconds != null) {
            // update sync workers (needs already updated sync interval in AccountSettings)
            workerManager.enablePeriodic(account, dataType, seconds, wifiOnly)
        } else
            workerManager.disablePeriodic(account, dataType)

        // Also enable/disable content change triggered syncs
        val authority: String? = when (dataType) {
            SyncDataType.CONTACTS -> context.getString(R.string.address_books_authority)
            SyncDataType.EVENTS -> CalendarContract.AUTHORITY
            SyncDataType.TASKS -> tasksAppManager.currentProvider()?.authority
        }
        if (authority != null && seconds != null)
            syncFramework.enableSyncOnContentChange(account, authority)
        else {
            for (authority in dataType.possibleAuthorities(context))
                syncFramework.disableSyncOnContentChange(account, authority)
        }
    }

}