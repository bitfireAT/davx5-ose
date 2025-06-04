/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
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


    fun possibleAuthorities(): List<String> =
        when (this) {
            CONTACTS -> listOf(
                ContactsContract.AUTHORITY
            )
            EVENTS -> listOf(
                CalendarContract.AUTHORITY
            )
            TASKS ->
                TaskProvider.ProviderName.entries.map { it.authority }
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
