/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose

import android.content.Context
import at.bitfire.davdroid.ui.DebugInfoActivity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class DebugInfoCrashHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
): Thread.UncaughtExceptionHandler {

    @Module
    @InstallIn(SingletonComponent::class)
    interface DebugInfoCrashHandlerModule {
        @Binds
        fun debugInfoCrashHandler(
            debugInfoCrashHandler: DebugInfoCrashHandler
        ): Thread.UncaughtExceptionHandler
    }

    // See https://developer.android.com/about/versions/oreo/android-8.0-changes#loue
    val originalCrashHandler = Thread.getDefaultUncaughtExceptionHandler()


    override fun uncaughtException(t: Thread, e: Throwable) {
        logger.log(Level.SEVERE, "Unhandled exception in thread ${t.id}!", e)

        // start debug info activity with exception (will be started in a new process)
        val intent = DebugInfoActivity.IntentBuilder(context)
            .withCause(e)
            .newTask()
            .build()
        context.startActivity(intent)

        // pass through to default handler to kill the process
        originalCrashHandler?.uncaughtException(t, e)
    }

}