/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.model.component.CalendarComponent

interface JtxObjectEntityHandler {

    /**
     * Takes specific data from a jtx object (= jtx object row plus data rows, taken from the jtx
     * Board content provider) and maps it into the given [CalendarComponent].
     *
     * If [from] references the same object as [main], this method is called for a main jtx object
     * (not an exception). If [from] references another object as [main], this method is called for
     * an exception (not a main jtx object).
     *
     * @param from jtx object from content provider
     * @param main main jtx object from content provider
     * @param to destination object where the mapped data are stored (no explicit `null` values
     *   needed for fields that are not present)
     *
     * @throws InvalidLocalResourceException on missing or invalid required fields
     */
    fun process(from: Entity, main: Entity, to: CalendarComponent)
}
