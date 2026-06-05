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
    fun increaseSequence(task: Task): Int? {
        val currentSeq = task.sequence
        if (currentSeq == null)   // sequence has not been assigned yet (i.e. this task was just locally created)
            task.sequence = 0
        else                    // task was modified, increase sequence
            task.sequence = currentSeq + 1
    }

}