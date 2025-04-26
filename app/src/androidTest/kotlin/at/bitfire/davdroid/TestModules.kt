/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import at.bitfire.davdroid.startup.StartupPlugin
import at.bitfire.davdroid.startup.TasksAppWatcher
import at.bitfire.davdroid.util.CoroutineDispatcherModule
import at.bitfire.davdroid.util.DefaultDispatcher
import at.bitfire.davdroid.util.IoDispatcher
import at.bitfire.davdroid.util.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

// remove TasksAppWatcherModule from Android tests
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [TasksAppWatcher.TasksAppWatcherModule::class]
)
abstract class TestTasksAppWatcherModuleModule {
    // provides empty set of plugins
    @Multibinds
    abstract fun empty(): Set<StartupPlugin>
}


/**
 * If you run tests that switch context to one of these dispatchers, use `runTest(mainDispatcher)`
 * with `mainDispatcher` being an injected [MainDispatcher] instead of plain `runTest`.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoroutineDispatcherModule::class]
)
object TestCoroutineDispatchers {

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