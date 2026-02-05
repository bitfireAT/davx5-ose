/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import at.bitfire.davdroid.di.scope.DefaultDispatcher
import at.bitfire.davdroid.log.LogManager
import at.bitfire.davdroid.startup.StartupPlugin
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import at.bitfire.davdroid.ui.UiUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidApp
class App: Application(), Configuration.Provider {

    @Inject
    lateinit var logger: Logger

    /**
     * Creates the [at.bitfire.davdroid.log.LogManager] singleton and thus initializes logging.
     */
    @Inject
    lateinit var logManager: LogManager

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

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

        logger.fine("Logging using LogManager $logManager")

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
        GlobalScope.launch(defaultDispatcher) {
            // clean up orphaned accounts in DB from time to time
            AccountsCleanupWorker.Companion.enable(this@App)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this@App)

            // run startup plugins (async)
            for (plugin in plugins.sortedBy { it.priorityAsync() }) {
                logger.fine("Running startup plugin: $plugin (onAppCreateAsync)")
                plugin.onAppCreateAsync()
            }
        }
    }

}