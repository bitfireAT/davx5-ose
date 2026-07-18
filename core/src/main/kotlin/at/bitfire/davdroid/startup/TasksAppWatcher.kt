/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import android.content.Context
import at.bitfire.davdroid.di.qualifier.ApplicationScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_DEFAULT
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.util.packageChangedFlow
import at.bitfire.synctools.storage.TaskProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Watches whether a tasks app has been installed or uninstalled and updates
 * the selected tasks app and task sync settings accordingly.
 */
class TasksAppWatcher @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val tasksAppManager: Provider<TasksAppManager>
) : StartupAction {

    override val priorityAsync = PRIORITY_DEFAULT

    override fun onAppCreateAsync() {
        logger.info("Watching for package changes in order to detect tasks app changes")
        applicationScope.launch(ioDispatcher) {
            packageChangedFlow(context).collect {
                onPackageChanged()
            }
        }
    }


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
            manager.selectProvider(newProvider)
        }
    }

}