/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.content.Context
import at.bitfire.davdroid.TasksWatcher
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.ical4android.TaskProvider.ProviderName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object TaskUtils: KoinComponent {

    val settingsManager by inject<SettingsManager>()

    fun currentProvider(context: Context): ProviderName? {
        val preferredAuthority = settingsManager.getString(Settings.PREFERRED_TASKS_PROVIDER)
        ProviderName.values()
                .sortedByDescending { it.authority == preferredAuthority }
                .forEach { providerName ->
            if (context.packageManager.resolveContentProvider(providerName.authority, 0) != null)
                return providerName
        }
        return null
    }

    fun isAvailable(context: Context) = currentProvider(context) != null

    fun setPreferredProvider(context: Context, providerName: ProviderName) {
        settingsManager.putString(Settings.PREFERRED_TASKS_PROVIDER, providerName.authority)
        CoroutineScope(Dispatchers.Default).launch {
            TasksWatcher.updateTaskSync(context)
        }
    }

}