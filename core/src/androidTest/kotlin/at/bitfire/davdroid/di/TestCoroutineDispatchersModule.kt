/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.qualifier.DefaultDispatcher
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

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

    private val testScheduler = TestCoroutineScheduler()

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun initMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    }

}