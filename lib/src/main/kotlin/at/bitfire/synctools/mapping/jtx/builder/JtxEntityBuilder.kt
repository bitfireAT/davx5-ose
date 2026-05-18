/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.synctools.exception.InvalidICalendarException
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo

interface JtxEntityBuilder {

    /**
     * Maps a specific part of the given item ([VToDo] or [VJournal]) into the provided [Entity].
     *
     * If [from] references the same object as [main], this method is called for a main item (not an
     * exception). If [from] references another object as [main], this method is called for an
     * exception (not a main item).
     *
     * Note: The result of the mapping is used to either create or update the item row in the
     * jtx Board content provider.
     * For updates, explicit `null` values are required for fields that should be `null` (otherwise
     * the value wouldn't be updated to `null` in case of an item update). Sub-rows of the [Entity]
     * will always be created anew, so there's no need to use `null` values in sub-rows.
     *
     * @param from item to map (will always be [VToDo] or [VJournal])
     * @param main main item (will always be [VToDo] or [VJournal])
     * @param to destination object where built values are stored (set `null` values, see note)
     *
     * @throws InvalidICalendarException on missing or invalid required properties
     */
    fun build(from: CalendarComponent, main: CalendarComponent, to: Entity)
}
