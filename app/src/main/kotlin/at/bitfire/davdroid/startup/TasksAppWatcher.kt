/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import android.content.Context
import at.bitfire.davdroid.startup.StartupPlugin.Companion.PRIORITY_DEFAULT
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.util.packageChangedFlow
import at.bitfire.ical4android.TaskProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Watches whether a tasks app has been installed or uninstalled and updates
 * the selected tasks app and task sync settings accordingly.
 */
class TasksAppWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val tasksAppManager: Provider<TasksAppManager>
): StartupPlugin {

    @Module
    @InstallIn(SingletonComponent::class)
    interface TasksAppWatcherModule {
        @Binds
        @IntoSet
        fun tasksAppWatcher(impl: TasksAppWatcher): StartupPlugin
    }


    override fun onAppCreate() {
    }

    override fun priority() = PRIORITY_DEFAULT

    override suspend fun onAppCreateAsync() {
        logger.info("Watching for package changes in order to detect tasks app changes")
        packageChangedFlow(context).collect {
            onPackageChanged()
        }
    }

    override fun priorityAsync() = PRIORITY_DEFAULT


    private fun onPackageChanged() {
        val manager = tasksAppManager.get()
        val currentProvider = manager.currentProvider()
        logger.info("App launched or package (un)installed; current tasks provider = $currentProvider")

        if (currentProvider == null) {
            // Iterate through all supported providers and select one, if available.
            var newProvider = TaskProvider.ProviderName.entries
                .firstOrNull { provider ->
                    context.packageManager.resolveContentProvider(provider.authority, 0) != null
                }

            // Select provider or clear setting and sync if now provider available
            logger.info("Selecting new tasks provider: $newProvider")
            manager.selectProvider(null)
        }
    }

}