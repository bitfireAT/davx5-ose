/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.setAndVerifyUserData
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
    @ApplicationContext private val context: Context
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        // add calendar colors
        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { client ->
            val provider = AndroidCalendarProvider(account, client)
            provider.provideCss3ColorIndices()
        }

        // update allowed WiFi settings key
        val accountManager = AccountManager.get(context)
        val onlySSID = accountManager.getUserData(account, "wifi_only_ssid")
        accountManager.setAndVerifyUserData(account, AccountSettings.KEY_WIFI_ONLY_SSIDS, onlySSID)
        accountManager.setAndVerifyUserData(account, "wifi_only_ssid", null)
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(7)
        abstract fun provide(impl: AccountSettingsMigration7): AccountSettingsMigration
    }

}