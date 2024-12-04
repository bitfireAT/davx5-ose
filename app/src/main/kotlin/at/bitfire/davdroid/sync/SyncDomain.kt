/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.ical4android.TaskProvider

/**
 * Represents the kind of data to be synced.
 *
 * The specific content provider that syncs this kind of data is specified by the authority.
 */
enum class SyncDomain {
    CONTACTS,
    EVENTS,
    TASKS;

    companion object {

        fun fromAuthority(authority: String): SyncDomain =
            when (authority) {
                ContactsContract.AUTHORITY -> CONTACTS
                CalendarContract.AUTHORITY -> EVENTS
                TaskProvider.ProviderName.JtxBoard.authority -> TASKS
                TaskProvider.ProviderName.OpenTasks.authority -> TASKS
                TaskProvider.ProviderName.TasksOrg.authority -> TASKS
                else -> throw IllegalArgumentException()
            }

    }

}