/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

enum class SyncDataType {

    CONTACTS,
    EVENTS,
    TASKS;

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncDataTypeEntryPoint {
        fun tasksAppManager(): TasksAppManager
    }

    /**
     * Returns authorities which exist for this sync data type. Used on [TASKS] the method
     * may return an empty list if there are no task providers (installed tasks apps).
     *
     * @return list of authorities matching this data type
     */
    fun possibleAuthorities(): List<String> =
        when (this) {
            CONTACTS -> listOf(ContactsContract.AUTHORITY)
            EVENTS -> listOf(CalendarContract.AUTHORITY)
            TASKS -> TaskProvider.ProviderName.entries.map { it.authority }
        }

    /**
     * Returns the authority corresponding to this datatype.
     * When more than one tasks provider exists (tasks apps installed) the authority for the active
     * tasks provider (user selected tasks app) is returned.
     *
     * @param context android context used to determine the active/selected tasks provider
     * @return the authority matching this data type or *null* for [TASKS] if no tasks app is installed
     */
    fun currentAuthority(context: Context): String? =
        when (this) {
            CONTACTS -> ContactsContract.AUTHORITY
            EVENTS -> CalendarContract.AUTHORITY
            TASKS -> EntryPointAccessors.fromApplication<SyncDataTypeEntryPoint>(context)
                .tasksAppManager()
                .currentProvider()
                ?.authority
        }


    companion object {

        fun fromAuthority(authority: String): SyncDataType {
            return when (authority) {
                ContactsContract.AUTHORITY ->
                    CONTACTS
                CalendarContract.AUTHORITY ->
                    EVENTS
                TaskProvider.ProviderName.JtxBoard.authority,
                TaskProvider.ProviderName.TasksOrg.authority,
                TaskProvider.ProviderName.OpenTasks.authority ->
                    TASKS
                else -> throw IllegalArgumentException("Unknown authority: $authority")
            }
        }

    }

}
