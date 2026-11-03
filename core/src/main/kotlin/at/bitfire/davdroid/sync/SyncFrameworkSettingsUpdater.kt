/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.local.LocalAddressBookStore
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.sync.adapter.SyncFrameworkIntegration
import javax.inject.Inject
import javax.inject.Provider

/**
 * Updates the sync framework settings (sync-ability and content-triggered sync) of an Android account
 * according to the account services and settings.
 *
 * This is the only part of automatic synchronization that depends on the Android account. Address book accounts
 * are updated even when the main Android account doesn't exist (yet).
 */
class SyncFrameworkSettingsUpdater @Inject constructor(
    private val accountSettingsFactory: AccountSettingsFactory,
    private val androidAccountManager: AndroidAccountManager,
    private val localAddressBookStore: LocalAddressBookStore,
    private val serviceRepository: DavServiceRepository,
    private val syncFramework: SyncFrameworkIntegration,
    private val tasksAppManager: Provider<TasksAppManager>
) {

    /**
     * Whether automatic synchronization is possible for the given account and data type, i.e. whether
     * there's a [Service] for it and (for tasks) a tasks app is installed.
     */
    @WorkerThread
    fun isAutomaticSyncPossible(accountId: AccountId, dataType: SyncDataType): Boolean {
        val serviceType = when (dataType) {
            SyncDataType.CONTACTS -> Service.TYPE_CARDDAV
            SyncDataType.EVENTS,
            SyncDataType.TASKS -> Service.TYPE_CALDAV
        }
        val hasService = serviceRepository.getByAccountIdAndTypeBlocking(accountId, serviceType) != null

        val hasProvider = if (dataType == SyncDataType.TASKS)
            tasksAppManager.get().currentProvider() != null
        else
            true

        return hasService && hasProvider
    }

    /**
     * Updates the sync framework settings of the Android account of [accountId] (if it exists) and its
     * address book accounts for all data types.
     */
    @WorkerThread
    fun update(accountId: AccountId) {
        val account = androidAccountManager.getAndroidAccount(accountId)
        for (dataType in SyncDataType.entries)
            update(accountId, account, dataType)
    }

    /**
     * Like [update], but only for the given data type.
     */
    @WorkerThread
    fun update(accountId: AccountId, dataType: SyncDataType) {
        update(accountId, androidAccountManager.getAndroidAccount(accountId), dataType)
    }

    private fun update(accountId: AccountId, account: Account?, dataType: SyncDataType) {
        if (isAutomaticSyncPossible(accountId, dataType))
            enable(accountId, account, dataType)
        else
            disable(account, dataType)
    }

    private fun disable(account: Account?, dataType: SyncDataType) {
        if (account == null)
            return
        for (authority in dataType.possibleAuthorities()) {
            syncFramework.disableSyncAbility(account, authority)
            // no need to disable content-triggered sync, as it can't be active when sync-ability is disabled
        }
    }

    private fun enable(accountId: AccountId, account: Account?, dataType: SyncDataType) {
        when (dataType) {
            SyncDataType.CONTACTS -> {
                // Contact updates are handled by their respective address book accounts, so we must always
                // disable the content-triggered sync for the main account.
                if (account != null)
                    syncFramework.disableSyncAbility(account, ContactsContract.AUTHORITY)

                // pass through request to update all existing address books
                localAddressBookStore.acquireLocalStorageClient()?.use { client ->
                    for (addressBookAccount in localAddressBookStore.getAll(accountId, client))
                        addressBookAccount.updateSyncFrameworkSettings()
                }
            }
            SyncDataType.EVENTS ->
                if (account != null)
                    enableAuthority(accountId, account, dataType, CalendarContract.AUTHORITY)
            SyncDataType.TASKS ->
                if (account != null)
                    enableAuthority(accountId, account, dataType, tasksAppManager.get().currentProvider()?.authority)
        }
    }

    private fun enableAuthority(accountId: AccountId, account: Account, dataType: SyncDataType, authority: String?) {
        val syncInterval = accountSettingsFactory.create(accountId).getSyncInterval(dataType)
        val possibleAuthorities = dataType.possibleAuthorities()
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
