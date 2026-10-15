/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettingsStore
import at.bitfire.davdroid.sync.AutomaticSyncManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Updates the periodic sync workers by re-setting the same sync interval.
 *
 * The goal is to add the [at.bitfire.davdroid.sync.worker.BaseSyncWorker.commonTag] to all existing periodic sync workers so that they
 * can be detected correctly.
 */
class AccountSettingsMigration15 @Inject constructor(
    private val automaticSyncManager: AutomaticSyncManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
): AccountSettingsMigration {

    override fun migrate(accountId: AccountId, store: AccountSettingsStore) {
        runBlocking(ioDispatcher) {
            automaticSyncManager.updateAutomaticSync(accountId)
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