/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ProdId

/**
 * Represents a set of components (like VEVENT) stored in a calendar object resource as defined
 * in RFC 4791 section 4.1. It consists of
 *
 * - an (optional) main component,
 * - optional exceptions of this main component.
 *
 * Note: It's possible and valid that there's no main component, but only exceptions, for instance
 * when the user has been invited to a specific instance (= exception) of a recurring event, but
 * not to the event as a whole (→ main event is unknown / not present).
 *
 * @param main          main component (with or without UID, but without RECURRENCE-ID), may be `null` if only exceptions are present
 * @param exceptions    exceptions (each without RECURRENCE-ID); UID must be
 *   1. the same as the UID of [main],
 *   2. the same for all exceptions.
 * @param prodId        optional `PRODID` related to the components
 *
 * If no [main] is present, [exceptions] must not be empty.
 *
 * @throws IllegalArgumentException   when the constraints above are violated
 */
data class AssociatedComponents<T: CalendarComponent>(
    val main: T?,
    val exceptions: List<T>,
    val prodId: ProdId? = null
) {

    init {
        validate()
    }

    /**
     * Validates the requirements of [main] and [exceptions] UIDs.
     *
     * @throws IllegalArgumentException     if [main] and/or [exceptions] UIDs don't match
     */
    private fun validate() {
        if (main == null && exceptions.isEmpty())
            throw IllegalArgumentException("At least one component is required")

        val mainUid =
            if (main != null) {
                if (main.recurrenceId != null)
                    throw IllegalArgumentException("Main event must not have a RECURRENCE-ID")

                main.uid
            }
            else
                null

        val exceptionsUid =
            if (exceptions.isNotEmpty()) {
                if (exceptions.any { it.recurrenceId == null } )
                    throw IllegalArgumentException("Exceptions must have RECURRENCE-ID")

                val firstExceptionUid = exceptions.first().uid
                if (exceptions.any { it.uid != firstExceptionUid })
                    throw IllegalArgumentException("Exceptions must not have different UIDs")
                firstExceptionUid
            } else
                null

        if (main != null && exceptions.isNotEmpty() && exceptionsUid != mainUid)
            throw IllegalArgumentException("Exceptions must have the same UID as the main event")
    }

}

typealias AssociatedEvents = AssociatedComponents<VEvent>