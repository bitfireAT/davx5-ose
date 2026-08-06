/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo

/**
 * Structurally mirrors [at.bitfire.synctools.mapping.tasks.builder.DmfsTaskEntityBuilder].
 */
interface DavTaskEntityBuilder {

    /**
     * Maps a specific part of the given [VToDo] into the provided [Entity].
     *
     * Note: The result of the mapping is used to either create or update the task row in the
     * content provider. For updates, explicit `null` values are required for fields that should
     * be `null` (otherwise the value wouldn't be updated to `null` in case of a task update).
     */
    fun build(from: VToDo, to: Entity)

    /**
     * If [from] references the same object as [main], this is the main task (not an exception).
     * By default delegates to [build] without [main]; override when the distinction matters.
     */
    fun build(from: VToDo, main: VToDo, to: Entity) = build(from, to)

}
