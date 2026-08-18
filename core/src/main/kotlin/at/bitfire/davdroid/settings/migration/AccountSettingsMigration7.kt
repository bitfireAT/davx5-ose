/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.settings.AccountSettingsStore
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

class AccountSettingsMigration7 @Inject constructor(
    private val accountAccountManager: AndroidAccountManager,
    @ApplicationContext private val context: Context
): AccountSettingsMigration {

    override fun migrate(accountId: AccountId, store: AccountSettingsStore) {
        addCalendarColors(accountId)
        updateWifiOnlySsids(store)
    }

    private fun addCalendarColors(accountId: AccountId) {
        val account = accountAccountManager.getAndroidAccount(accountId)
        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { client ->
            val provider = AndroidCalendarProvider(account, client)
            provider.provideCss3ColorIndices()
        }
    }

    private fun updateWifiOnlySsids(store: AccountSettingsStore) {
        val onlySSID = store.getValue(KEY_WIFI_ONLY_SSID_OLD)
        store.putValue(KEY_WIFI_ONLY_SSIDS_NEW, onlySSID)
        store.putValue(KEY_WIFI_ONLY_SSID_OLD, null)
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(7)
        abstract fun provide(impl: AccountSettingsMigration7): AccountSettingsMigration
    }

    companion object {
        private const val KEY_WIFI_ONLY_SSID_OLD = "wifi_only_ssid"
        private const val KEY_WIFI_ONLY_SSIDS_NEW = "wifi_only_ssids"
    }
}