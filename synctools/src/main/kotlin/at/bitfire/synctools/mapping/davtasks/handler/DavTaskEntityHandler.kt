/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.model.component.VToDo

/**
 * Structurally mirrors [at.bitfire.synctools.mapping.tasks.handler.DmfsTaskEntityHandler].
 */
interface DavTaskEntityHandler {

    /**
     * Takes specific data from a task (row + sub-rows, taken from the content provider) and maps
     * it into the given [VToDo].
     *
     * @throws InvalidLocalResourceException on missing or invalid required fields
     */
    fun process(from: Entity, main: Entity, to: VToDo)

}
