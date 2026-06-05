/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import at.bitfire.ical4android.Task

class SequenceUpdater {

    /**
     * Increases the task's SEQUENCE, if necessary. Usually called after a task is
     * retrieved from the tasks provider, but before it's mapped to an iCalendar.
     *
     * @param task  task to be checked (**will be modified** when SEQUENCE needs to be increased)
     *
     * @return updated sequence (or *null* if sequence was not increased/modified)
     */
    fun increaseSequence(task: Task): Int {
        // In the future, this should use the same algorithm as

        val currentSeq = task.sequence
        val newSeq = if (currentSeq == null)
            0                   // sequence has not been assigned yet (i.e. this task was just locally created)
        else
            currentSeq + 1      // task was modified, increase sequence

        // update in Task
        task.sequence = newSeq

        return newSeq
    }

}