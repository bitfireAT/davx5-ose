/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Looper
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.AccountSettings.Companion.CURRENT_VERSION
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SETTINGS_VERSION
import at.bitfire.davdroid.settings.migration.AccountSettingsMigration
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Collections
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider

/**
 * **Must not be called from main thread as it uses blocking I/O and may run migrations.**
 */
class AccountManagerSettingsStore @AssistedInject constructor(
    @Assisted val account: Account,
    @Assisted val abortOnMissingMigration: Boolean,
    context: Context,
    private val logger: Logger,
    private val migrations: Map<Int, @JvmSuppressWildcards Provider<AccountSettingsMigration>>,
) : AccountSettingsStore {

    @AssistedFactory
    interface Factory {
        /**
         * **Must not be called on main thread. Throws exceptions!** See [AccountSettings] for details.
         */
        @WorkerThread
        fun create(account: Account, abortOnMissingMigration: Boolean = false): AccountManagerSettingsStore
    }

    private val accountManager = AccountManager.get(context)

    init {
        if (Looper.getMainLooper() == Looper.myLooper())
            throw IllegalThreadStateException("AccountManagerSettingsStore may not be used on main thread")
    }

    init {
        if (account.type != context.getString(R.string.account_type))
            throw IllegalArgumentException("Invalid account type for AccountSettings(): ${account.type}")

        // synchronize because account migration must only be run one time
        synchronized(currentlyUpdating) {
            if (currentlyUpdating.contains(account))
                logger.warning("AccountSettings created during migration of $account – not running update()")
            else {
                val versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION) ?: throw InvalidAccountException(account)
                var version = 0
                try {
                    version = Integer.parseInt(versionStr)
                } catch (e: NumberFormatException) {
                    logger.log(Level.SEVERE, "Invalid account version: $versionStr", e)
                }
                logger.fine("Account ${account.name} has version $version, current version: $CURRENT_VERSION")

                if (version < CURRENT_VERSION) {
                    currentlyUpdating += account
                    try {
                        update(version, abortOnMissingMigration)
                    } finally {
                        currentlyUpdating -= account
                    }
                }
            }
        }
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
        return accountManager.getPassword(account)?.toSensitiveString()
    }

    /**
     * If [key] is [AccountSettings.KEY_PASSWORD], the password of the account is updated. Otherwise, [value] is stored
     * as plain text at [key] in user data (same as [putValue]).
     */
    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        accountManager.setPassword(account, value?.asString())
    }

    // update from previous account settings

    private fun update(baseVersion: Int, abortOnMissingMigration: Boolean) {
        for (toVersion in baseVersion+1 ..CURRENT_VERSION) {
            val fromVersion = toVersion - 1
            logger.info("Updating account ${account.name} settings version $fromVersion → $toVersion")

            val migration = migrations[toVersion]
            if (migration == null) {
                logger.severe("No AccountSettings migration $fromVersion → $toVersion")
                if (abortOnMissingMigration)
                    throw IllegalArgumentException("Missing AccountSettings migration $fromVersion → $toVersion")
            } else {
                try {
                    migration.get().migrate(account)

                    logger.info("Account settings version update to $toVersion successful")
                    accountManager.setAndVerifyUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Couldn't run AccountSettings migration $fromVersion → $toVersion", e)
                }
            }
        }
    }

    companion object {
        /** Static property to remember which AccountSettings updates/migrations are currently running */
        private val currentlyUpdating = Collections.synchronizedSet(mutableSetOf<Account>())
    }
}
