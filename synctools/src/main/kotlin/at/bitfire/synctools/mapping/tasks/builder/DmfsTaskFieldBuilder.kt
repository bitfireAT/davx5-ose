/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity

@Deprecated("DmfsTaskFieldBuilderVToDo should be used instead")
interface DmfsTaskFieldBuilder {

    /**
     * Maps a specific part of the given task into the provided [Entity].
     *
     * Note: The result of the mapping is used to either create or update the task row in the
     * content provider. For updates, explicit `null` values are required for fields that should
     * be `null` (otherwise the value wouldn't be updated to `null` in case of a task update).
     *
     * @param from  task to map
     * @param to    destination [Entity] where built values are stored (set `null` values, see note)
     */
    fun build(from: Task, to: Entity)

}
