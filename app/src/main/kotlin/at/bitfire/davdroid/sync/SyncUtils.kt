/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.TaskUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Utility methods related to synchronization management (authorities, workers etc.)
 */
object SyncUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncUtilsEntryPoint {
        fun appDatabase(): AppDatabase
        fun settingsManager(): SettingsManager
    }

    /**
     * Returns a list of all available sync authorities:
     *
     *   1. calendar authority
     *   2. contacts authority
     *   3. current tasks authority (if available)
     *
     * Checking the availability of authorities may be relatively expensive, so the
     * result should be cached for the current operation.
     *
     * @return list of available sync authorities for main accounts
     */
    fun syncAuthorities(context: Context): List<String> {
        val result = mutableListOf(
            CalendarContract.AUTHORITY,
            ContactsContract.AUTHORITY
        )
        TaskUtils.currentProvider(context)?.let { taskProvider ->
            result += taskProvider.authority
        }
        return result
    }

}