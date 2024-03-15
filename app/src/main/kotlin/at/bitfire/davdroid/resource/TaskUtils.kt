/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.ical4android.TaskProvider.ProviderName
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

object TaskUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskUtilsEntryPoint {
        fun settingsManager(): SettingsManager
    }

    /**
     * Returns the currently selected tasks provider (if it's still available = installed).
     *
     * @return the currently selected tasks provider, or null if none is available
     */
    fun currentProvider(context: Context): ProviderName? {
        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
        val preferredAuthority = settingsManager.getString(Settings.SELECTED_TASKS_PROVIDER) ?: return null
        return preferredAuthorityToProviderName(preferredAuthority, context.packageManager)
    }

    /**
     * Returns the currently selected tasks provider (if it's still available = installed).
     *
     * @return the currently selected tasks provider, or null if none is available
     */
    fun currentProviderLive(context: Context): LiveData<ProviderName?> {
        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
        return settingsManager.getStringLive(Settings.SELECTED_TASKS_PROVIDER).map { preferred ->
            if (preferred != null)
                preferredAuthorityToProviderName(preferred, context.packageManager)
            else
                null
        }
    }

    private fun preferredAuthorityToProviderName(
        preferredAuthority: String,
        packageManager: PackageManager
    ): ProviderName? {
        ProviderName.entries.toTypedArray()
            .sortedByDescending { it.authority == preferredAuthority }
            .forEach { providerName ->
                if (packageManager.resolveContentProvider(providerName.authority, 0) != null)
                    return providerName
            }
        return null
    }

    fun isAvailable(context: Context) = currentProvider(context) != null

    fun selectProvider(context: Context, providerName: ProviderName?, updateSyncSettings: Boolean = false) {
        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
        settingsManager.putString(Settings.SELECTED_TASKS_PROVIDER, providerName?.authority)

        if (updateSyncSettings)
            SyncUtils.updateTaskSync(context)
    }

}