/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * If you run tests that switch context to one of these dispatchers, use `runTest(mainDispatcher)`
 * with `mainDispatcher` being an injected [MainDispatcher] instead of plain `runTest`.
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

}