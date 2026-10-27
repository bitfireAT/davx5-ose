/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.accounts.AccountId
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent


/**
 * Used to decide on whether login and sync are allowed to happen.
 */
interface SyncValidator {

    /**
     * Called before synchronization when a sync adapter is started. Can be used for license checks etc. Must be thread-safe.
     *
     * @param accountId [AccountId] of the account about to be synchronized
     * @return whether synchronization shall take place (false to abort)
     */
    suspend fun beforeSync(accountId: AccountId): Boolean

}

@Module
@InstallIn(SingletonComponent::class)
interface SyncValidatorModule {
    @BindsOptionalOf
    fun syncValidator(): SyncValidator
}
