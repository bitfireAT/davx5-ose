/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.ContentResolver
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.settings.AccountSettingsStore
import at.bitfire.davdroid.sync.TasksAppManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * The tasks sync interval should be stored in account settings. It's used to set the sync interval
 * again when the tasks provider is switched.
 */
class AccountSettingsMigration11 @Inject constructor(
    private val androidAccountManager: AndroidAccountManager,
    private val tasksAppManager: TasksAppManager
): AccountSettingsMigration {

    override fun migrate(accountId: AccountId, store: AccountSettingsStore) {
        val account = androidAccountManager.getAndroidAccount(accountId)
        tasksAppManager.currentProvider()?.let { provider ->
            val interval = getSyncFrameworkInterval(account, provider.authority)
            if (interval != null)
                store.putValue(KEY_SYNC_INTERVAL_TASKS, interval.toString())
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

    companion object {
        private const val KEY_SYNC_INTERVAL_TASKS = "sync_interval_tasks"
        private const val SYNC_INTERVAL_MANUALLY = -1L
    }
}