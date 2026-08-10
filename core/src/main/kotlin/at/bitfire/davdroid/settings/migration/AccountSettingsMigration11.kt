/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettings.Companion.SYNC_INTERVAL_MANUALLY
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Provider

/**
 * The tasks sync interval should be stored in account settings. It's used to set the sync interval
 * again when the tasks provider is switched.
 */
class AccountSettingsMigration11 @Inject constructor(
    private val accountManager: Provider<AccountManager>,
    private val tasksAppManager: TasksAppManager
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        tasksAppManager.currentProvider()?.let { provider ->
            val interval = getSyncFrameworkInterval(account, provider.authority)
            if (interval != null)
                accountManager.get()
                    .setAndVerifyUserData(account, AccountSettings.KEY_SYNC_INTERVAL_TASKS, interval.toString())
        }
    }

    private fun getSyncFrameworkInterval(account: Account, authority: String): Long? {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null

        return if (ContentResolver.getSyncAutomatically(account, authority))
            ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period ?: SYNC_INTERVAL_MANUALLY
        else
            SYNC_INTERVAL_MANUALLY
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(11)
        abstract fun provide(impl: AccountSettingsMigration11): AccountSettingsMigration
    }

}