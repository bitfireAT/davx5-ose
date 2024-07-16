/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import android.content.Context
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.davdroid.util.packageChangedFlow
import at.bitfire.ical4android.TaskProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.logging.Logger

/**
 * Watches whether a tasks app has been installed or uninstalled and updates
 * the selected tasks app and task sync settings accordingly.
 */
class TasksAppWatcher private constructor(
    private val context: Context,
    private val logger: Logger
): StartupPlugin {

    @Module
    @InstallIn(SingletonComponent::class)
    class TasksAppWatcherModule {
        @Provides
        @IntoSet
        fun tasksAppWatcher(
            @ApplicationContext context: Context,
            logger: Logger
        ): StartupPlugin = TasksAppWatcher(context, logger)
    }


    override fun onAppCreate() {
    }

    override fun priority() = 100

    override suspend fun onAppCreateAsync() {
        logger.info("Watching for package changes in order to detect tasks app changes")
        packageChangedFlow(context).collect {
            onPackageChanged()
        }
    }

    override fun priorityAsync() = 100


    private fun onPackageChanged() {
        val currentProvider = TaskUtils.currentProvider(context)
        logger.info("App launched or package (un)installed; current tasks provider = $currentProvider")

        if (currentProvider == null) {
            // Iterate through all supported providers and select one, if available.
            var providerSelected = false
            for (provider in TaskProvider.ProviderName.entries) {
                val available = context.packageManager.resolveContentProvider(provider.authority, 0) != null
                if (available) {
                    logger.info("Selecting new tasks provider: $provider")
                    TaskUtils.selectProvider(context, provider)
                    providerSelected = true
                    break
                }
            }

            if (!providerSelected)
                // no provider available (anymore), also clear setting and sync
                TaskUtils.selectProvider(context, null)
        }
    }

}