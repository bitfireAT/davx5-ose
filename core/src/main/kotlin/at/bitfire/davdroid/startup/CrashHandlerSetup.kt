/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_FIRST
import java.util.Optional
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * Sets up the uncaught exception (crash) handler in release builds.
 */
class CrashHandlerSetup @Inject constructor(
    private val crashHandler: Optional<Thread.UncaughtExceptionHandler>,
    private val logger: Logger
) : StartupAction {

    override val priority = PRIORITY_FIRST

    override fun onAppCreate() {
        // we don't need this for debugging because we can observe crashes directly then
        if (BuildConfig.DEBUG)
            return

        val handler = crashHandler.getOrNull()
        if (handler != null) {
            logger.info("Setting uncaught exception handler: ${handler.javaClass.name}")
            Thread.setDefaultUncaughtExceptionHandler(handler)
        } else
            logger.info("Using default uncaught exception handler")
   }

}