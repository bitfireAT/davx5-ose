/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VEvent

interface AndroidEntityBuilder {

    /**
     * Maps a specific part of the given event into the provided [Entity].
     *
     * If [from] references the same object as [main], this method is called for a main event (not an exception).
     * If [from] references another object as [main], this method is called for an exception (not a main event).
     *
     * So you can use (note the referential equality operator):
     *
     * ```
     * val buildsMainEvent = from === main
     * ```
     *
     * Note: The result of the mapping is used to either create or update the event row in the content provider.
     * For updates, explicit `null` values are required for fields that should be `null` (otherwise the value
     * wouldn't be updated to `null` in case of an event update). Sub-rows of the [Entity] will always be created
     * anew, so there's no need to use `null` values in sub-rows.
     *
     * @param from  event to map
     * @param main  main event
     * @param to    destination object where built values are stored (set `null` values, see note)
     *
     * @throws at.bitfire.synctools.exception.InvalidICalendarException on missing or invalid required properties (like DTSTART)
     */
    fun build(from: VEvent, main: VEvent, to: Entity)

}