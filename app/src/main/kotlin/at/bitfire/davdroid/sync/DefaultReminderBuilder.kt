/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventAndExceptions

class DefaultReminderBuilder(
    private val minBefore: Int
) {

    /**
     * Adds a default alarm ([minBefore] minutes before) to
     *
     * - the main event and
     * - each exception event,
     *
     * except for those events which
     *
     * - are all-day, or
     * - already have another reminder.
     */
    fun add(to: EventAndExceptions) {
        // add default reminder to main event and exceptions
        val events = mutableListOf(to.main)
        events += to.exceptions

        for (event in events)
            addToEvent(to = event)
    }

    @VisibleForTesting
    internal fun addToEvent(to: Entity) {
        // don't add default reminder if there's already another reminder
        if (to.subValues.any { it.uri == Reminders.CONTENT_URI })
            return

        // don't add default reminder to all-day events
        if (to.entityValues.getAsInteger(Events.ALL_DAY) == 1)
            return

        to.addSubValue(Reminders.CONTENT_URI, contentValuesOf(
            Reminders.MINUTES to minBefore,
            Reminders.METHOD to Reminders.METHOD_ALERT      // will trigger an alarm on the Android device
        ))
    }

}