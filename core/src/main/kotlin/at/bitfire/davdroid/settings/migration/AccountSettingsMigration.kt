/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import at.bitfire.davdroid.settings.AccountManagerSettingsStore

interface AccountSettingsMigration {

    /**
     * Migrate the account settings from the old version to the new version which
     * is set in [AccountManagerSettingsStore.CURRENT_VERSION].
     *
     * **The new (target) version number is registered in the Hilt module as [Int] key of the multi-binding of [AccountManagerSettingsStore].**
     *
     * This method should depend on current architecture of [AccountManagerSettingsStore] as little as possible. Methods of [AccountManagerSettingsStore]
     * may change in future and it shouldn't be necessary to change migrations as well. So it's better to operate "low-level"
     * directly on the account user-data – which is also better testable.
     *
     * @param   account          The account to migrate
     */
    fun migrate(account: Account)

}