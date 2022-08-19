/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.content.Context
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.ical4android.TaskProvider.ProviderName
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TaskUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskUtilsEntryPoint {
        fun settingsManager(): SettingsManager
    }

    fun currentProvider(context: Context): ProviderName? {
        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
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
        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
        settingsManager.putString(Settings.PREFERRED_TASKS_PROVIDER, providerName.authority)
        CoroutineScope(Dispatchers.Default).launch {
            SyncUtils.updateTaskSync(context)
        }
    }

}