/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo

// TODO: Once all super-classes of DmfsTaskFieldBuilder are migrated to DmfsTaskFieldBuilderVToDo, we can remove the old DmfsTaskFieldBuilder and rename this interface to DmfsTaskFieldBuilder
interface DmfsTaskFieldBuilderVToDo {

    /**
     * Maps a specific part of the given [VToDo] into the provided [Entity].
     *
     * Note: The result of the mapping is used to either create or update the task row in the
     * content provider. For updates, explicit `null` values are required for fields that should
     * be `null` (otherwise the value wouldn't be updated to `null` in case of a task update).
     *
     * @param from  task to map
     * @param to    destination [Entity] where built values are stored (set `null` values, see note)
     */
    fun build(from: VToDo, to: Entity)

    /**
     * Maps a specific part of the given [VToDo] into the provided [Entity].
     *
     * If [from] references the same object as [main], this is the main task (not an exception).
     * If [from] references a different object, this is an exception instance.
     *
     * Use referential equality to distinguish them:
     * ```
     * val isMain = from === main
     * ```
     *
     * By default delegates to [build] without [main]; override when the distinction matters.
     *
     * @param from  task to map
     * @param main  main (non-exception) VToDo
     * @param to    destination [Entity] where built values are stored (set `null` values, see note)
     */
    fun build(from: VToDo, main: VToDo, to: Entity) = build(from, to)

}
