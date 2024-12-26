/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.R
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


    fun possibleAuthorities(context: Context): List<String> =
        when (this) {
            CONTACTS -> listOf(
                ContactsContract.AUTHORITY,
                context.getString(R.string.address_books_authority)
            )
            EVENTS -> listOf(
                CalendarContract.AUTHORITY
            )
            TASKS ->
                TaskProvider.ProviderName.entries.map { it.authority }
        }


    /**
     * Gets the authority that should be used for managing sync adapters for this account and data type.
     *
     * In case of contacts, it will return the address books authority (and not the contacts authority).
     */
    fun toSyncAuthority(context: Context): String? = when(this) {
        CONTACTS ->
            context.getString(R.string.address_books_authority)
        EVENTS ->
            CalendarContract.AUTHORITY
        TASKS -> {
            val entryPoint = EntryPointAccessors.fromApplication<SyncDataTypeEntryPoint>(context)
            val tasksAppManager = entryPoint.tasksAppManager()
            tasksAppManager.currentProvider()?.authority
        }
    }


    companion object {

        fun fromAuthority(context: Context, authority: String): SyncDataType {
            return when (authority) {
                context.getString(R.string.address_books_authority),
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