/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import at.bitfire.davdroid.log.LogManager
import at.bitfire.davdroid.startup.StartupPlugin
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.UiUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class App: Application(), Thread.UncaughtExceptionHandler, Configuration.Provider {

    @Inject
    lateinit var logger: Logger

    /**
     * Creates the [LogManager] singleton and thus initializes logging.
     */
    @Inject
    lateinit var logManager: LogManager

    @Inject
    lateinit var plugins: Set<@JvmSuppressWildcards StartupPlugin>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()


    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG)
            // debug builds
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectFileUriExposure()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build())
        else // if (BuildConfig.FLAVOR == FLAVOR_STANDARD)
            // handle uncaught exceptions in non-debug standard flavor
            Thread.setDefaultUncaughtExceptionHandler(this)

        NotificationUtils.createChannels(this)

        // set light/dark mode
        UiUtils.updateTheme(this)   // when this is called in the asynchronous thread below, it recreates
                                 // some current activity and causes an IllegalStateException in rare cases

        // run startup plugins (sync)
        for (plugin in plugins.sortedBy { it.priority() }) {
            logger.fine("Running startup plugin: $plugin (onAppCreate)")
            plugin.onAppCreate()
        }

        // don't block UI for some background checks
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.Default) {
            // clean up orphaned accounts in DB from time to time
            AccountsCleanupWorker.enqueue(this@App)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this@App)

            // run startup plugins (async)
            for (plugin in plugins.sortedBy { it.priorityAsync() }) {
                logger.fine("Running startup plugin: $plugin (onAppCreateAsync)")
                plugin.onAppCreateAsync()
            }
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        logger.log(Level.SEVERE, "Unhandled exception!", e)

        val intent = DebugInfoActivity.IntentBuilder(this)
            .withCause(e)
            .newTask()
            .build()
        startActivity(intent)

        exitProcess(1)
    }

}