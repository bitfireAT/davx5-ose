/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface SyncValidator {

    /**
     * Called before synchronization when a sync adapter is started. Can be used for license checks etc. Must be thread-safe.
     *
     * @return whether synchronization shall take place (false to abort)
     */
    fun beforeSync(): Boolean

}

@Module
@InstallIn(SingletonComponent::class)
interface SyncValidatorModule {
    @BindsOptionalOf
    fun syncValidator(): SyncValidator
}
