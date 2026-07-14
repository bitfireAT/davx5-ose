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
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_AUTH_STATE
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_CERTIFICATE_ALIAS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_PRECONFIGURATION_URL
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_USERNAME
import at.bitfire.davdroid.settings.migration.AccountSettingsMigration
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider

class AccountSettings @AssistedInject constructor(
    @Assisted override val accountId: LegacyAccount,
    @Assisted val abortOnMissingMigration: Boolean,
    override val automaticSyncManager: AutomaticSyncManager,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val migrations: Map<Int, @JvmSuppressWildcards Provider<AccountSettingsMigration>>,
    override val settingsManager: SettingsManager
) : AccountSettingsStore<LegacyAccount> {

    @AssistedFactory
    interface Factory {
        /**
         * **Must not be called on main thread. Throws exceptions!** See [AccountSettings] for details.
         */
        @WorkerThread
        fun create(accountId: LegacyAccount, abortOnMissingMigration: Boolean = false): AccountSettings

        @WorkerThread
        @Deprecated("Use AccountId overload directly")
        fun create(account: Account, abortOnMissingMigration: Boolean = false) = create(LegacyAccount(account), abortOnMissingMigration)
    }

    init {
        if (Looper.getMainLooper() == Looper.myLooper())
            throw IllegalThreadStateException("AccountManagerSettingsStore may not be used on main thread")
    }

    private val account: Account = accountId.androidAccount

    val accountManager: AccountManager = AccountManager.get(context)
    init {
        if (account.type != context.getString(R.string.account_type))
            throw IllegalArgumentException("Invalid account type for AccountManagerSettingsStore(): ${account.type}")

        // synchronize because account migration must only be run one time
        synchronized(currentlyUpdating) {
            if (currentlyUpdating.contains(account))
                logger.warning("AccountManagerSettingsStore created during migration of $account – not running update()")
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

    override fun get(key: String): String? {
        return accountManager.getUserData(account, key)
    }

    override fun getSensitiveValue(key: String): SensitiveString? {
        return if (key == AccountSettingsStore.KEY_PASSWORD)
            accountManager.getPassword(account)?.toSensitiveString()
        else
            accountManager.getUserData(account, key)?.toSensitiveString()
    }

    override fun set(key: String, value: String?) {
        accountManager.setAndVerifyUserData(account, key, value)
    }

    override fun setSensitiveValue(key: String, value: SensitiveString?) {
        if (key == AccountSettingsStore.KEY_PASSWORD)
            accountManager.setPassword(account, value?.asString())
        else
            accountManager.setAndVerifyUserData(account, key, value?.asString())
    }

    // update from previous account settings

    private fun update(baseVersion: Int, abortOnMissingMigration: Boolean) {
        for (toVersion in baseVersion+1 ..CURRENT_VERSION) {
            val fromVersion = toVersion - 1
            logger.info("Updating account ${account.name} settings version $fromVersion → $toVersion")

            val migration = migrations[toVersion]
            if (migration == null) {
                logger.severe("No AccountManagerSettingsStore migration $fromVersion → $toVersion")
                if (abortOnMissingMigration)
                    throw IllegalArgumentException("Missing AccountManagerSettingsStore migration $fromVersion → $toVersion")
            } else {
                try {
                    migration.get().migrate(account)

                    logger.info("Account settings version update to $toVersion successful")
                    accountManager.setAndVerifyUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Couldn't run AccountManagerSettingsStore migration $fromVersion → $toVersion", e)
                }
            }
        }
    }


    companion object {

        /**
         * Current (usually the newest) account settings version. It's used to
         * determine whether a migration ([AccountSettingsMigration])
         * should be performed.
         */
        const val CURRENT_VERSION = 21
        const val KEY_SETTINGS_VERSION = "version"

        /** Static property to remember which AccountSettings updates/migrations are currently running */
        private val currentlyUpdating = Collections.synchronizedSet(mutableSetOf<Account>())

        /**
         * Returns the initial user data (as in [AccountManager.setUserData]) for creating a new account.
         * Processed user data:
         *
         * - account settings scheme version
         * - authentication details
         * - client certificate alias
         * - preconfiguration URL
         */
        fun initialUserData(credentials: Credentials?, preconfigurationUrl: String?) = buildMap<String, String> {
            put(KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())

            if (credentials != null) {
                if (credentials.username != null)
                    put(KEY_USERNAME, credentials.username)

                if (credentials.certificateAlias != null)
                    put(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

                if (credentials.authState != null)
                    put(KEY_AUTH_STATE, credentials.authState.jsonSerializeString())
            }

            if (preconfigurationUrl != null)
                put(KEY_PRECONFIGURATION_URL, preconfigurationUrl)
        }

    }

}
