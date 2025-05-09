/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.TestCoroutineDispatchersModule.standardTestDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * Provides test dispatchers to be injected instead of the normal ones.
 *
 * The [standardTestDispatcher] is set as main dispatcher in [at.bitfire.davdroid.HiltTestRunner],
 * so that tests can just use [kotlinx.coroutines.test.runTest] without providing [standardTestDispatcher].
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoroutineDispatchersModule::class]
)
object TestCoroutineDispatchersModule {

    val scheduler = TestCoroutineScheduler()
    val standardTestDispatcher = StandardTestDispatcher(scheduler)

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = standardTestDispatcher

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = standardTestDispatcher

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = standardTestDispatcher

    @Provides
    @SyncDispatcher
    fun syncDispatcher(): CoroutineDispatcher = standardTestDispatcher

}