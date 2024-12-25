/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import at.bitfire.davdroid.settings.AccountSettings

interface AccountSettingsMigration {

    /**
     * Migrate the account settings from the old version to the new version.
     *
     * The target version number is registered in the Hilt module as [Int] key of the multi-binding of [AccountSettings].
     *
     * @param   account          The account to migrate.
     *
     * This method should depend on current architecture of [AccountSettings] as little as possible. Methods of [AccountSettings]
     * may change in future and it shouldn't be necessary to change migrations as well. So it's better to operate "low-level"
     * directly on the account user-data – which is also better testable.
     */
    fun migrate(account: Account)

}