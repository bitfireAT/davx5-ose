/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.os.Handler
import android.os.Looper
import at.bitfire.davdroid.di.TestCoroutineDispatchersModule.testScheduler
import at.bitfire.davdroid.di.qualifier.DefaultDispatcher
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.RealMainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

/**
 * Provides test dispatchers to be injected instead of the normal ones.
 *
 * [DefaultDispatcher]/[IoDispatcher] and [Dispatchers.Main] (whichs is used by
 * [androidx.lifecycle.viewModelScope] by default) are synchronized with [testScheduler],
 * so that [kotlinx.coroutines.test.runTest] can determine when launched coroutines are done etc.
 *
 * In contrast, [RealMainDispatcher] can be used to test code that needs to run
 * on the main [Looper] and for instance must not do network I/O there.
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

    private val defaultDispatcher = StandardTestDispatcher(testScheduler)
    private val ioDispatcher = StandardTestDispatcher(testScheduler)
    private val mainDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = defaultDispatcher

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = ioDispatcher

    /** Syncs [Dispatchers.Main] to [testScheduler] so e.g. `viewModelScope` is deterministic under `runTest`. */
    fun initMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }


    // Android Looper

    /** Dispatcher that is bound to the real main [Looper] with its restrictions, like that no
     * network traffic is allowed. See also class KDoc. */
    @Provides
    @RealMainDispatcher
    fun realMainDispatcher(): CoroutineDispatcher =
        Handler(Looper.getMainLooper()).asCoroutineDispatcher()

}