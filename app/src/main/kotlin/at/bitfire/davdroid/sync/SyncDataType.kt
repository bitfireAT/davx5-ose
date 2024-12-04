/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.R
import at.bitfire.ical4android.TaskProvider

/**
 * Represents the kind of data to be synced.
 *
 * The specific content provider that syncs this kind of data is specified by the authority.
 */
enum class SyncDataType {
    CONTACTS,
    EVENTS,
    TASKS;

    /**
     * Gets the content provider authority for this kind of data.
     * If the data type is [TASKS], the [tasksApp] function is used to determine the authority.
     *
     * @param tasksApp a function that returns the task provider to use if the data type is [TASKS]
     *
     * @return the content provider authority, or `null` if the data type is [TASKS] and there's no task provider
     */
    fun toAuthority(tasksApp: () -> TaskProvider.ProviderName?): String? =
        when (this) {
            CONTACTS -> ContactsContract.AUTHORITY
            EVENTS -> CalendarContract.AUTHORITY
            TASKS -> tasksApp()?.authority
        }


    companion object {

        fun fromAuthority(context: Context, authority: String): SyncDataType =
            when (authority) {
                ContactsContract.AUTHORITY,
                context.getString(R.string.address_books_authority) -> CONTACTS

                CalendarContract.AUTHORITY -> EVENTS

                TaskProvider.ProviderName.JtxBoard.authority,
                TaskProvider.ProviderName.OpenTasks.authority,
                TaskProvider.ProviderName.TasksOrg.authority -> TASKS
                else -> throw IllegalArgumentException("Unknown authority: $authority")
            }

    }

}