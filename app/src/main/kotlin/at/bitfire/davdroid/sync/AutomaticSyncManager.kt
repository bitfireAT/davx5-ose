/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider

/**
 * Manages automatic synchronization, that is:
 *
 * - synchronization in given intervals, and
 * - synchronization on local data changes.
 *
 * Integrates with both the periodic sync workers and the sync framework. So this class should be used when
 * the caller just wants to update the automatic sync, without needing to know about the underlying details.
 *
 * Automatic synchronization stands in contrast to manual synchronization, which is only triggered by the user.
 */
class AutomaticSyncManager @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    private val localAddressBookStore: LocalAddressBookStore,
    private val serviceRepository: DavServiceRepository,
    private val syncFramework: SyncFrameworkIntegration,
    private val tasksAppManager: Provider<TasksAppManager>,
    private val workerManager: SyncWorkerManager
) {

    /**
     * Disable automatic synchronization for the given account and data type.
     */
    private fun disableAutomaticSync(account: Account, dataType: SyncDataType) {
        workerManager.disablePeriodic(account, dataType)

        for (authority in dataType.possibleAuthorities()) {
            syncFramework.disableSyncAbility(account, authority)
            // no need to disable content-triggered sync, as it can't be active when sync-ability is disabled
        }
    }

    /**
     * Enables/Disables automatic synchronization for the given account and data type and sets it to the given interval,
     * based on sync interval setting in account settings:
     *
     * 1. Enables/Disables periodic sync worker for the given data type with the given interval.
     * 2. Enables/Disables sync in the sync framework and enables or disables content-triggered syncs for the given data type
     *
     * @param account   the account to synchronize
     * @param dataType  the data type to synchronize
     */
    private fun enableAutomaticSync(
        account: Account,
        dataType: SyncDataType
    ) {
        val accountSettings = accountSettingsFactory.create(account)
        val syncInterval = accountSettings.getSyncInterval(dataType)

        // 1. Update sync workers (needs already updated sync interval in AccountSettings).
        if (syncInterval != null) {
            val wifiOnly = accountSettings.getSyncWifiOnly()
            workerManager.enablePeriodic(account, dataType, syncInterval, wifiOnly)
        } else
            workerManager.disablePeriodic(account, dataType)

        // 2. Enable/disable content-triggered syncs.
        if (dataType == SyncDataType.CONTACTS) {
            // Contact updates are handled by their respective address book accounts, so we must always
            // disable the content-triggered sync for the main account.
            syncFramework.disableSyncAbility(account, ContactsContract.AUTHORITY)

            // pass through request to update all existing address books
            localAddressBookStore.acquireContentProvider()?.use { provider ->
                for (addressBookAccount in localAddressBookStore.getAll(account, provider))
                    addressBookAccount.updateAutomaticSync()
            }

        } else {
            // everything but contacts
            val possibleAuthorities = dataType.possibleAuthorities()
            val authority: String? = when (dataType) {
                SyncDataType.CONTACTS -> throw IllegalStateException()  // handled above
                SyncDataType.EVENTS -> CalendarContract.AUTHORITY
                SyncDataType.TASKS -> tasksAppManager.get().currentProvider()?.authority
            }
            if (authority != null && syncInterval != null) {
                // enable given authority, but completely disable all other possible authorities
                // (for instance, tasks apps which are not the current task app)
                syncFramework.enableSyncOnContentChange(account, authority)
                for (disableAuthority in possibleAuthorities - authority)
                    syncFramework.disableSyncAbility(account, disableAuthority)
            } else
                for (authority in possibleAuthorities)
                    syncFramework.disableSyncOnContentChange(account, authority)
        }
    }

    /**
     * Updates automatic synchronization of the given account and all data types according to the account settings.
     *
     * If there's a [Service] for the given account and data type, automatic sync is enabled (with details from [AccountSettings]).
     * Otherwise, automatic synchronization is disabled.
     *
     * @param account   account for which automatic synchronization shall be updated
     */
    fun updateAutomaticSync(account: Account) {
        for (dataType in SyncDataType.entries)
            updateAutomaticSync(account, dataType)
    }

    /**
     * Updates automatic synchronization of the given account and data type according to the account services and settings.
     *
     * If there's a [Service] for the given account and data type, automatic sync may be enabled if sync interval is set
     * in [AccountSettings].
     * Otherwise, automatic synchronization is disabled.
     *
     * @param account   account for which automatic synchronization shall be updated
     * @param dataType  sync data type for which automatic synchronization shall be updated
     */
    fun updateAutomaticSync(account: Account, dataType: SyncDataType) {
        val serviceType = when (dataType) {
            SyncDataType.CONTACTS -> Service.TYPE_CARDDAV
            SyncDataType.EVENTS,
            SyncDataType.TASKS -> Service.TYPE_CALDAV
        }
        val hasService = runBlocking { serviceRepository.getByAccountAndType(account.name, serviceType) != null }

        val hasProvider = if (dataType == SyncDataType.TASKS)
            tasksAppManager.get().currentProvider() != null
        else
            true

        if (hasService && hasProvider)
            enableAutomaticSync(account, dataType)
        else
            disableAutomaticSync(account, dataType)
    }

}