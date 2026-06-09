/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import android.content.Entity
import org.dmfs.tasks.contract.TaskContract.Tasks

class SequenceUpdater {

    /**
     * Increases the task's SEQUENCE, if necessary. Usually called after a task is
     * retrieved from the tasks provider, but before it's mapped to an iCalendar.
     *
     * @param mainTask  task to be checked (**will be modified** when SEQUENCE needs to be increased)
     *
     * @return updated sequence (or *null* if sequence was not increased/modified)
     */
    @Suppress("RedundantNullableReturnType")
    fun increaseSequence(mainTask: Entity): Int? {
        /* In the future, this should only increase the sequence for group-scheduled tasks
        and return null for non-group-scheduled tasks. However, at the moment we don't support
        attendees and thus there's no way to determine whether a task is group-scheduled or not. */

        val mainValues = mainTask.entityValues
        val currentSeq = mainValues.getAsInteger(Tasks.SYNC_VERSION)

        val newSeq = if (currentSeq == null)
            0                   // sequence has not been assigned yet (i.e. this task was just locally created)
        else
            currentSeq + 1      // task was modified, increase sequence

        // update in mainTask
        mainValues.put(Tasks.SYNC_VERSION, newSeq)

        return newSeq
    }

}
