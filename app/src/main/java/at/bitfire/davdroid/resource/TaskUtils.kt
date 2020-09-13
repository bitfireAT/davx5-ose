package at.bitfire.davdroid.resource

import android.content.Context
import at.bitfire.davdroid.TasksWatcher
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.ical4android.TaskProvider.ProviderName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TaskUtils {

    fun currentProvider(context: Context): ProviderName? {
        val preferredAuthority = SettingsManager.getInstance(context).getString(Settings.PREFERRED_TASKS_PROVIDER)
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
        SettingsManager.getInstance(context).putString(Settings.PREFERRED_TASKS_PROVIDER, providerName.authority)
        CoroutineScope(Dispatchers.Default).launch {
            TasksWatcher.updateTaskSync(context)
        }
    }

}