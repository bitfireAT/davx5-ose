/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.Context
import androidx.preference.PreferenceManager
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * Not a per-account migration, but not a database migration, too, so it fits best there.
 * Best future solution would be that SettingsManager manages versions and migrations.
 *
 * Updates proxy settings from override_proxy_* to proxy_type, proxy_host, proxy_port.
 */
class AccountSettingsMigration13 @Inject constructor(
    @ApplicationContext private val context: Context
): AccountSettingsMigration {

    override fun migrate(account: Account, accountSettings: AccountSettings) {
        // proxy settings are managed by SharedPreferencesProvider
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // old setting names
        val overrideProxy = "override_proxy"
        val overrideProxyHost = "override_proxy_host"
        val overrideProxyPort = "override_proxy_port"

        val edit = preferences.edit()
        if (preferences.contains(overrideProxy)) {
            if (preferences.getBoolean(overrideProxy, false))
            // override_proxy set, migrate to proxy_type = HTTP
                edit.putInt(Settings.PROXY_TYPE, Settings.PROXY_TYPE_HTTP)
            edit.remove(overrideProxy)
        }
        if (preferences.contains(overrideProxyHost)) {
            preferences.getString(overrideProxyHost, null)?.let { host ->
                edit.putString(Settings.PROXY_HOST, host)
            }
            edit.remove(overrideProxyHost)
        }
        if (preferences.contains(overrideProxyPort)) {
            val port = preferences.getInt(overrideProxyPort, 0)
            if (port != 0)
                edit.putInt(Settings.PROXY_PORT, port)
            edit.remove(overrideProxyPort)
        }
        edit.apply()
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(13)
        abstract fun provide(impl: AccountSettingsMigration13): AccountSettingsMigration
    }

}