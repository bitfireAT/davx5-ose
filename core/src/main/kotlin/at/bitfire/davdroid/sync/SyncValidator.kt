/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl


/**
 * Used to decide on whether login and sync are allowed to happen.
 */
interface SyncValidator {

    /**
     * Called before login/setup to validate if the server is allowed for this app variant.
     * Must be thread-safe.
     *
     * @param baseUrl The base URL of the server to validate
     * @return whether login/setup shall proceed (false to abort)
     */
    fun beforeLogin(baseUrl: HttpUrl): Boolean

    /**
     * Called before synchronization when a sync adapter is started. Can be used for license checks etc. Must be thread-safe.
     *
     * @param account The account about to be synchronized
     * @return whether synchronization shall take place (false to abort)
     */
    fun beforeSync(account: Account): Boolean

}

@Module
@InstallIn(SingletonComponent::class)
interface SyncValidatorModule {
    @BindsOptionalOf
    fun syncValidator(): SyncValidator
}
