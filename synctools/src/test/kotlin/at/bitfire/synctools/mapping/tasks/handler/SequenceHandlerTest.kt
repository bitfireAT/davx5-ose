/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceHandlerTest {

    private val handler = SequenceHandler()

    @Test
    fun `legacy No sequence`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.sequence)
    }

    @Test
    fun `legacy Sequence is 0`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.SYNC_VERSION to 0), task)
        assertEquals(0, task.sequence)
    }

    @Test
    fun `legacy Sequence is positive`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.SYNC_VERSION to 3), task)
        assertEquals(3, task.sequence)
    }

    @Test
    fun `No sequence`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.sequence)
    }

    @Test
    fun `Sequence is 0`() {
        val input = Entity(contentValuesOf(Tasks.SYNC_VERSION to 0))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(0, task.sequence.sequenceNo)
    }

    @Test
    fun `Sequence is positive`() {
        val input = Entity(contentValuesOf(Tasks.SYNC_VERSION to 3))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(3, task.sequence.sequenceNo)
    }
}
