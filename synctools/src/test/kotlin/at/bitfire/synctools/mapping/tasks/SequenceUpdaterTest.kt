/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import at.bitfire.ical4android.Task
import junit.framework.Assert.assertEquals
import org.junit.Test

class SequenceUpdaterTest {

    private val updater = SequenceUpdater()

    @Test
    fun `Current SEQUENCE is null`() {
        val task = Task().apply {
            sequence = null
        }
        val newSeq = updater.increaseSequence(task)
        assertEquals(0, newSeq)
        assertEquals(0, task.sequence)
    }

    @Test
    fun `Current SEQUENCE is 0`() {
        val task = Task().apply {
            sequence = 0
        }
        val newSeq = updater.increaseSequence(task)
        assertEquals(1, newSeq)
        assertEquals(1, task.sequence)
    }

    @Test
    fun `Current SEQUENCE is 1`() {
        val task = Task().apply {
            sequence = 1
        }
        val newSeq = updater.increaseSequence(task)
        assertEquals(2, newSeq)
        assertEquals(2, task.sequence)
    }

}