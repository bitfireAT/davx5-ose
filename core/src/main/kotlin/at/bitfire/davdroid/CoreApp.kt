/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import at.bitfire.davdroid.log.LogManager
import at.bitfire.davdroid.startup.StartupAction
import java.util.logging.Logger
import javax.inject.Inject

/**
 * The actual app should extend this class. The derived class must then be set
 * as `@HiltAndroidApp.`
 */
abstract class CoreApp: Application() {

    @Inject
    lateinit var logger: Logger

    /**
     * Creates the [at.bitfire.davdroid.log.LogManager] singleton and thus initializes logging.
     */
    @Inject
    lateinit var logManager: LogManager

    @Inject
    lateinit var actions: Set<@JvmSuppressWildcards StartupAction>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory


    override fun onCreate() {
        super.onCreate()

        logger.fine("Logging using LogManager $logManager")

        // run startup actions synchronously (they launch a coroutine if possible)
        actions.sortedBy { it.priority }.forEach { action ->
            logger.fine("Running startup action: $action")
            action.onAppCreate()
        }
    }

}