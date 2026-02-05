/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.TestCoroutineDispatchersModule.standardTestDispatcher
import at.bitfire.davdroid.di.scope.DefaultDispatcher
import at.bitfire.davdroid.di.scope.IoDispatcher
import at.bitfire.davdroid.di.scope.MainDispatcher
import at.bitfire.davdroid.di.scope.SyncDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private val standardTestDispatcher = StandardTestDispatcher()

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

   /**
     * Sets the [standardTestDispatcher] as [Dispatchers.Main] so that test dispatchers
     * created in the future use the same scheduler. See [StandardTestDispatcher] docs
     * for more information.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun initMainDispatcher() {
        Dispatchers.setMain(standardTestDispatcher)
    }

}