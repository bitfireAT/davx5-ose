/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.Entity
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.model.component.VToDo

interface DmfsTaskFieldHandler2 {

    /**
     * Takes specific data from a task (= task row + data rows, taken from the content provider)
     * and maps it into the given [VToDo].
     *
     * If [from] references the same object as [main], this method is called for a main task (not an exception).
     * If [from] references another object as [main], this method is called for an exception (not a main task).
     *
     * @param from task from the content provider
     * @param main main task from the content provider
     * @param to destination object where the mapped data is stored
     *
     * @throws InvalidLocalResourceException on missing or invalid required fields
     */
    fun process(from: Entity, main: Entity, to: VToDo)
}
