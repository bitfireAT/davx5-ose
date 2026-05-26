/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.Entity
import android.provider.CalendarContract
import at.bitfire.synctools.storage.calendar.EventsContract

/**
 * Handles SEQUENCE updates of main events.
 */
class SequenceUpdater {

    /**
     * Increases the event's SEQUENCE, if necessary. Usually called after an event is
     * retrieved from the calendar provider, but before it's mapped to an iCalendar.
     *
     * @param main  event to be checked (**will be modified** when SEQUENCE needs to be increased)
     *
     * @return updated sequence (or *null* if sequence was not increased/modified)
     */
    fun increaseSequence(main: Entity): Int? {
        val mainValues = main.entityValues
        val currentSeq = mainValues.getAsInteger(EventsContract.COLUMN_SEQUENCE)

        if (currentSeq == null) {
            /* First upload, request to set to 0 in calendar provider after upload.
            We can let it empty in the Entity because then no SEQUENCE property will be generated,
            which is equal to SEQUENCE:0. */
            return 0
        }

        val groupScheduled = main.subValues.any { it.uri == CalendarContract.Attendees.CONTENT_URI }
        if (groupScheduled) {
            /* Note: Events.IS_ORGANIZER is defined in CalendarDatabaseHelper.java as
            COALESCE(Events.IS_ORGANIZER, Events.ORGANIZER = Calendars.OWNER_ACCOUNT), so it's non-null when it's
            - either explicitly set for an event,
            - or the event's ORGANIZER is the same as the calendar's OWNER_ACCOUNT. */
            val weAreOrganizer = when (mainValues.getAsInteger(CalendarContract.Events.IS_ORGANIZER)) {
                null, 0 -> false
                /* explicitly set to non-zero, or 1 by provider calculation */ else -> true
            }

            return if (weAreOrganizer) {
                /* Upload of a group-scheduled event and we are the organizer, so we increase the SEQUENCE.
                We also have to store it into the Entity so that the new value will be mapped. */
                (currentSeq + 1).also { newSeq ->
                    mainValues.put(EventsContract.COLUMN_SEQUENCE, newSeq)
                }
            } else
            /* Upload of a group-scheduled event and we are not the organizer, so we don't increase the SEQUENCE. */
                null

        } else /* not group-scheduled */  {
            return if (currentSeq == 0) {
                /* The event was uploaded once and has SEQUENCE of 0 (which is mapped to an empty SEQUENCE property).
                We don't need to increase the SEQUENCE because the event is not group-scheduled. */
                null
            } else {
                /* Upload of a non-group-scheduled event where a SEQUENCE > 0 is present. Increase by one after upload.
                We also have to store it into the Entity so that the new value will be mapped. */
                (currentSeq + 1).also { newSeq ->
                    mainValues.put(EventsContract.COLUMN_SEQUENCE, newSeq)
                }
            }
        }
    }

}