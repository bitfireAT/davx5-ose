/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import androidx.annotation.VisibleForTesting
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.CalendarComponent
import kotlin.jvm.optionals.getOrNull

class CalendarUidSplitter<T: CalendarComponent> {

    /**
     * Splits iCalendar components by UID and classifies them as
     * - main events (which do not have a RECURRENCE-ID) or
     * - exceptions (which do have a RECURRENCE-ID).
     *
     * When there are multiple components with the same UID and RECURRENCE-ID, but different SEQUENCE,
     * this method keeps only the ones with the highest SEQUENCE.
     *
     * @param calendar The calendar to split
     * @param componentName The name of the component to split (e.g. "VEVENT")
     *
     * @return A map of UID to [AssociatedComponents]
     */
    fun associateByUid(calendar: Calendar, componentName: String): Map<String?, AssociatedComponents<T>> {
        // get all components of type T (for instance: all VEVENTs)
        val all = calendar.getComponents<T>(componentName)

        // Note for VEVENT: UID is REQUIRED in RFC 5545 section 3.6.1, but optional in RFC 2445 section 4.6.1,
        // so it's possible that the Uid is null.
        val byUid: Map<String?, List<T>> = all
            .groupBy { it.uid.getOrNull()?.value }
            .mapValues { filterBySequence(it.value) }

        val result = mutableMapOf<String?, AssociatedComponents<T>>()
        for ((uid, vEventsWithUid) in byUid) {
            val mainVEvent = vEventsWithUid.lastOrNull { it.recurrenceId == null }
            val exceptions = vEventsWithUid.filter { it.recurrenceId != null }
            result[uid] = AssociatedComponents(mainVEvent, exceptions)
        }

        return result
    }

    /**
     * Keeps only the events with the highest SEQUENCE (per RECURRENCE-ID).
     *
     * @param events    list of VEVENTs with the same UID, but different RECURRENCE-IDs (could be `null`) and SEQUENCEs
     *
     * @return same as input list, but each RECURRENCE-ID occurs only with the highest SEQUENCE
     */
    @VisibleForTesting
    internal fun filterBySequence(events: List<T>): List<T> {
        // group by RECURRENCE-ID (could be null)
        val byRecurId = events.groupBy { it.recurrenceId?.value }.values

        // for every RECURRENCE-ID: keep only event with the highest sequence
        val latest = byRecurId.map { sameUidAndRecurId ->
            sameUidAndRecurId.maxBy { it.sequence?.sequenceNo ?: 0 }
        }

        return latest
    }

}