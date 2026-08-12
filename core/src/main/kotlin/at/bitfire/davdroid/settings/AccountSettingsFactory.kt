/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import androidx.annotation.WorkerThread
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import javax.inject.Inject

class AccountSettingsFactory @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    private val accountManagerSettingsStoreFactory: AccountManagerSettingsStore.Factory,
    private val dbAccountSettingsStoreFactory: DbAccountSettingsStore.Factory,
) {
    @WorkerThread
    fun create(accountId: AccountId, abortOnMissingMigration: Boolean = false): AccountSettings {
        val store = when(accountId) {
            is LegacyAccount -> accountManagerSettingsStoreFactory.create(accountId.androidAccount, abortOnMissingMigration)
        }
        return accountSettingsFactory.create(accountId, store)
    }
}
