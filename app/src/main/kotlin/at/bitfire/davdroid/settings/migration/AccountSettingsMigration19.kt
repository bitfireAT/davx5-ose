/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import at.bitfire.davdroid.settings.AccountSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * Sync workers are now not per authority anymore, but per [at.bitfire.davdroid.sync.SyncDataType]. So we have to
 *
 * 1. cancel all current periodic sync workers (which have "authority" input data),
 * 2. re-enqueue periodic sync workers (now with "data type" input data), if applicable.
 */
class AccountSettingsMigration19 @Inject constructor(
    // @ApplicationContext private val context: Context
): AccountSettingsMigration {

    override fun migrate(account: Account, accountSettings: AccountSettings) {
        // FIXME re-enqueue periodic sync workers
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(19)
        abstract fun provide(impl: AccountSettingsMigration19): AccountSettingsMigration
    }

}