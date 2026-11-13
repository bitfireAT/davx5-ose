/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.local.LocalAddressBook
import at.bitfire.davdroid.resource.local.LocalAddressBookStore
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.sync.adapter.SyncFrameworkIntegration
import at.bitfire.synctools.storage.LocalStorageClient
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncFrameworkSettingsUpdaterTest {

    private val accountId = DbAccountId(1)
    private val account = Account("test", "test")

    private val accountSettings = mockk<AccountSettings> {
        every { getSyncInterval(any()) } returns 3600L
    }
    private val accountSettingsFactory = mockk<AccountSettingsFactory> {
        every { create(accountId) } returns accountSettings
    }
    private val androidAccountManager = mockk<AndroidAccountManager> {
        every { getAndroidAccount(accountId) } returns account
    }
    private val addressBook = mockk<LocalAddressBook>(relaxed = true)
    private val localAddressBookStore = mockk<LocalAddressBookStore> {
        every { acquireLocalStorageClient(any()) } returns mockk<LocalStorageClient>(relaxed = true)
        every { getAll(accountId, any()) } returns listOf(addressBook)
    }
    private val serviceRepository = mockk<DavServiceRepository> {
        every { getByAccountIdAndTypeBlocking(accountId, any()) } returns mockk<Service>()
    }
    private val syncFramework = mockk<SyncFrameworkIntegration>(relaxed = true)

    private val updater = SyncFrameworkSettingsUpdater(
        accountSettingsFactory = accountSettingsFactory,
        androidAccountManager = androidAccountManager,
        localAddressBookStore = localAddressBookStore,
        serviceRepository = serviceRepository,
        syncFramework = syncFramework,
        tasksAppManager = { mockk(relaxed = true) }
    )

    @Test
    fun `update() looks up the Android account and updates all data types`() {
        updater.update(accountId)

        verify(exactly = 1) { androidAccountManager.getAndroidAccount(accountId) }
        verify { syncFramework.disableSyncAbility(account, ContactsContract.AUTHORITY) }
        verify { syncFramework.enableSyncOnContentChange(account, CalendarContract.AUTHORITY) }
    }

    @Test
    fun `update() for contacts disables main account and updates address books`() {
        updater.update(accountId, SyncDataType.CONTACTS)

        verify { syncFramework.disableSyncAbility(account, ContactsContract.AUTHORITY) }
        verify { addressBook.updateSyncFrameworkSettings() }
    }

    @Test
    fun `update() for contacts without main account still updates address books`() {
        every { androidAccountManager.getAndroidAccount(accountId) } returns null

        updater.update(accountId, SyncDataType.CONTACTS)

        verify { addressBook.updateSyncFrameworkSettings() }
        confirmVerified(syncFramework)
    }

    @Test
    fun `update() for events without main account does nothing`() {
        every { androidAccountManager.getAndroidAccount(accountId) } returns null

        updater.update(accountId, SyncDataType.EVENTS)

        confirmVerified(syncFramework)
    }

}
