/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.qualifier.ApplicationScope
import at.bitfire.davdroid.di.qualifier.DefaultDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CoroutineScopesModule {

    @Singleton
    @Provides
    @ApplicationScope
    fun applicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(
        /* 1) If one child fails, don't cancel all other ones → SupervisorJob.
         * 2) We explicitly specify a dispatcher here to have a deterministic default because the
         * scope can be created by Hilt whenever it's first injected, with any dispatcher in the parent
         * coroutine. See `@ApplicationScope` KDoc. */
        SupervisorJob() + defaultDispatcher
    )

}