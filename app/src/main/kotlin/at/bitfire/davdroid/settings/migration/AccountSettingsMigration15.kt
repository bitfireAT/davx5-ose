/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * Updates the periodic sync workers by re-setting the same sync interval.
 *
 * The goal is to add the [BaseSyncWorker.commonTag] to all existing periodic sync workers so that they can be detected by
 * the new [BaseSyncWorker.exists] and [at.bitfire.davdroid.ui.AccountsActivity.Model].
 */
class AccountSettingsMigration15 @Inject constructor(
    private val syncWorkerManager: SyncWorkerManager
): AccountSettingsMigration {

    override fun migrate(account: Account, accountSettings: AccountSettings) {
        for (authority in syncWorkerManager.syncAuthorities()) {
            val interval = accountSettings.getSyncInterval(authority)
            accountSettings.setSyncInterval(authority, interval ?: AccountSettings.SYNC_INTERVAL_MANUALLY)
        }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(15)
        abstract fun provide(impl: AccountSettingsMigration15): AccountSettingsMigration
    }

}