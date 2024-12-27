/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import at.bitfire.davdroid.sync.AutomaticSyncManager
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
 * The goal is to add the [at.bitfire.davdroid.sync.worker.BaseSyncWorker.commonTag] to all existing periodic sync workers so that they
 * can be detected correctly.
 */
class AccountSettingsMigration15 @Inject constructor(
    private val automaticSyncManager: AutomaticSyncManager
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        automaticSyncManager.updateAutomaticSync(account)
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(15)
        abstract fun provide(impl: AccountSettingsMigration15): AccountSettingsMigration
    }

}