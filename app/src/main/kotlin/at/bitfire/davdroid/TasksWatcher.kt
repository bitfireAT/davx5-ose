/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.content.Context
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Watches whether a tasks app has been installed or uninstalled and updates
 * the preferred tasks app accordingly.
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
            if (TaskUtils.currentProvider(context) == null) {
                /* Currently no usable tasks provider.
                Iterate through all supported providers and select one, if available. */

                var providerSelected = false
                for (provider in TaskProvider.ProviderName.entries) {
                    val available = context.packageManager.resolveContentProvider(provider.authority, 0) != null
                    if (available) {
                        Logger.log.info("Selecting new tasks provider: $provider")
                        TaskUtils.selectProvider(context, provider, updateSyncSettings = false)
                        providerSelected = true
                        break
                    }
                }

                if (!providerSelected)
                    // no provider available, also clear setting
                    TaskUtils.selectProvider(context, null, updateSyncSettings = false)
            }

            // update sync settings
            SyncUtils.updateTaskSync(context)
        }
    }

}