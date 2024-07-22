/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.startup

import android.content.Context
import android.os.StrictMode
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.startup.StartupPlugin.Companion.PRIORITY_DEFAULT
import at.bitfire.davdroid.startup.StartupPlugin.Companion.PRIORITY_HIGHEST
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.Optional
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * Sets up the uncaught exception (crash) handler and enables StrictMode in debug builds.
 */
class CrashHandlerSetup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val crashHandler: Optional<Thread.UncaughtExceptionHandler>
): StartupPlugin {

    @Module
    @InstallIn(SingletonComponent::class)
    interface CrashHandlerSetupModule {
        // allows to inject Optional<Thread.UncaughtExceptionHandler>
        @BindsOptionalOf
        fun optionalDebugInfoCrashHandler(): Thread.UncaughtExceptionHandler

        @Binds
        @IntoSet
        fun crashHandlerSetup(impl: CrashHandlerSetup): StartupPlugin
    }


    override fun onAppCreate() {
        if (BuildConfig.DEBUG) {
            logger.info("Debug build, enabling StrictMode with logging")

            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyFlashScreen()
                    .penaltyLog()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )

        } else {
            // release build
            val handler = crashHandler.getOrNull()
            if (handler != null) {
                logger.info("Setting uncaught exception handler: ${handler.javaClass.name}")
                Thread.setDefaultUncaughtExceptionHandler(handler)
            } else
                logger.info("Using default uncaught exception handler")
        }
   }

    override fun priority() = PRIORITY_HIGHEST

    override suspend fun onAppCreateAsync() {
    }

    override fun priorityAsync(): Int = PRIORITY_DEFAULT

}