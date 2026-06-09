/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.synctools.exception.InvalidICalendarException
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo

interface JtxObjectEntityBuilder {

    /**
     * Maps a specific part of the given component ([VToDo] or [VJournal]) into the provided [Entity].
     *
     * If [from] references the same object as [main], this method is called for a main component
     * (not an exception). If [from] references another object as [main], this method is called for
     * an exception (not a main component).
     *
     * Note: The result of the mapping is used to either create or update the jtx object row in the
     * jtx Board content provider.
     * For updates, explicit `null` values are required for fields that should be `null` (otherwise
     * the value wouldn't be updated to `null` in case of a jtx object update). Sub-rows of the
     * [Entity] will always be created anew, so there's no need to use `null` values in sub-rows.
     *
     * @param from component to map (will always be [VToDo] or [VJournal])
     * @param main main component (will always be [VToDo] or [VJournal])
     * @param to destination object where built values are stored (set `null` values, see note)
     *
     * @throws InvalidICalendarException on missing or invalid required properties
     */
    fun build(from: CalendarComponent, main: CalendarComponent, to: Entity)
}
