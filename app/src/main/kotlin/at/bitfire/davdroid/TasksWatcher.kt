/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.content.Context
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Watches whether a tasks app has been installed or uninstalled and updates
 * the selected tasks app and task sync settings accordingly.
 */
class TasksWatcher private constructor(
    context: Context
): PackageChangedReceiver(context) {

    companion object {

        fun watch(context: Context) {
            TasksWatcher(context).register(true)
        }

    }

    override fun onPackageChanged() {
        CoroutineScope(Dispatchers.Default).launch {
            val currentProvider = TaskUtils.currentProvider(context)
            Logger.log.info("App launched or package (un)installed; current tasks provider = $currentProvider")

            if (currentProvider == null) {
                // Iterate through all supported providers and select one, if available.
                var providerSelected = false
                for (provider in TaskProvider.ProviderName.entries) {
                    val available = context.packageManager.resolveContentProvider(provider.authority, 0) != null
                    if (available) {
                        Logger.log.info("Selecting new tasks provider: $provider")
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

}