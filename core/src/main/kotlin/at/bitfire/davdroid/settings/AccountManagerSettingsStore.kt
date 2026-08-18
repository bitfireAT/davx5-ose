/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import androidx.annotation.WorkerThread
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * [AccountSettingsStore] that uses [AccountManager] to store settings.
 */
class AccountManagerSettingsStore @AssistedInject constructor(
    @Assisted val account: Account,
    private val accountManager: AccountManager,
) : AccountSettingsStore {

    @AssistedFactory
    interface Factory {
        /**
         * **Must not be called on main thread. Throws exceptions!** See [AccountSettings] for details.
         */
        @WorkerThread
        fun create(account: Account): AccountManagerSettingsStore
    }

    override fun getAllValues(): Map<String, String> {
        throw UnsupportedOperationException("AccountManager does not support retrieving all user data at once")
    }

    /**
     * Retrieves the value stored in user data of [account] at [key]. May be `null` if not set.
     */
    override fun getValue(key: String): String? = accountManager.getUserData(account, key)

    /**
     * Updates the value stored as user data for [account] at [key].
     * `null` [value] clears the stored value.
     */
    override fun putValue(key: String, value: String?) {
        accountManager.setAndVerifyUserData(account, key, value)
    }

    /**
     * If [key] is [AccountSettings.KEY_PASSWORD], retrieves the password of the account.
     * Otherwise, does the same as [getValue].
     */
    override fun getSensitiveValue(key: String): SensitiveString? {
        val value = if (key == AccountSettings.KEY_PASSWORD)
            accountManager.getPassword(account)
        else
            getValue(key)
        return value?.toSensitiveString()
    }

    /**
     * If [key] is [AccountSettings.KEY_PASSWORD], the password of the account is updated. Otherwise, [value] is stored
     * as plain text at [key] in user data (same as [putValue]).
     */
    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        if (key == AccountSettings.KEY_PASSWORD)
            accountManager.setPassword(account, value?.asString())
        else
            putValue(key, value?.asString())
    }
}
