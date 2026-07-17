/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.os.Looper
import at.bitfire.davdroid.di.TestCoroutineDispatchersModule.testScheduler
import at.bitfire.davdroid.di.qualifier.IoDispatcher
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
 * [IoDispatcher] and [Dispatchers.Main] (which is used by
 * [androidx.lifecycle.viewModelScope] by default) are synchronized with [testScheduler],
 * so that [kotlinx.coroutines.test.runTest] can determine when launched coroutines are done etc.
 *
 * To test code that needs to run on the main [Looper] and for instance must not do network I/O there,
 * directly use [androidx.test.annotation.UiThreadTest] or [android.app.Instrumentation.runOnMainSync].
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoroutineDispatchersModule::class]
)
@OptIn(ExperimentalCoroutinesApi::class)
object TestCoroutineDispatchersModule {

    // synchronized dispatchers

    private val testScheduler = TestCoroutineScheduler()

    private val ioDispatcher = StandardTestDispatcher(testScheduler)
    private val mainDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = ioDispatcher

    /** Syncs [Dispatchers.Main] to [testScheduler] so e.g. `viewModelScope` is deterministic under `runTest`. */
    fun initMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

}