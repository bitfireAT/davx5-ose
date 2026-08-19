/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.qualifier.DefaultDispatcher
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.SyncDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
class CoroutineDispatchersModule {

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @SyncDispatcher
    fun syncDispatcher(): CoroutineDispatcher = limitedDispatcher(
        nrThreads = Runtime.getRuntime().availableProcessors()
    )


    /**
     * Creates a dispatcher whose thread count is not taken from another
     * dispatcher's pool.
     */
    private fun limitedDispatcher(nrThreads: Int) =
        Dispatchers.IO.limitedParallelism(nrThreads)    // special case: Dispatchers.IO elasticity (see docs)

}