/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface UncaughtExceptionHandlerModule {

    // allows to inject Optional<Thread.UncaughtExceptionHandler> by providing an empty Optional as default
    @BindsOptionalOf
    fun optionalDebugInfoCrashHandler(): Thread.UncaughtExceptionHandler

}