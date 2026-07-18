/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import at.bitfire.davdroid.log.LogManager
import at.bitfire.davdroid.startup.StartupAction
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import at.bitfire.davdroid.ui.UiUtils
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.concurrent.thread

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

        // set light/dark mode
        UiUtils.updateTheme(this)   // when this is called in the asynchronous thread below, it recreates
                                 // some current activity and causes an IllegalStateException in rare cases

        // run synchronous startup actions
        actions.filter { it.priority() != null }.sortedBy { it.priority() }.forEach { action ->
            logger.fine("Running blocking startup action: $action.onAppCreate()")
            action.onAppCreate()
        }

        /* Don't block app startup for some background tasks. A thread is used instead of coroutines
        because neither the scope nor the dispatcher should be set here. There may also be non-suspending
        actions that may still need some time, like updating dynamic shortcuts etc. */
        thread {
            // clean up orphaned accounts in DB from time to time
            AccountsCleanupWorker.enable(this@CoreApp)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this@CoreApp)

            // run asynchronous startup actions
            actions.filter { it.priorityAsync() != null }.sortedBy { it.priorityAsync() }.forEach { action ->
                logger.fine("Running background startup action: $action.onAppCreateAsync()")
                action.onAppCreateAsync()
            }
        }
    }

}