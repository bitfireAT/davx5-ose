/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Priority
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PriorityHandlerTest {

    private val handler = PriorityHandler()


    @Test
    fun `No PRIORITY doesn't add PRIORITY property`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.priority)
    }

    @Test
    fun `PRIORITY is 0 (undefined)`() {
        val input = Entity(contentValuesOf(Tasks.PRIORITY to 0))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

    }

    @Test
    fun `PRIORITY is 1 (high)`() {
        val input = Entity(contentValuesOf(Tasks.PRIORITY to 1))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Priority(1), task.priority)
    }

    @Test
    fun `PRIORITY is 5 (medium)`() {
        val input = Entity(contentValuesOf(Tasks.PRIORITY to 5))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Priority(5), task.priority)
    }

    @Test
    fun `PRIORITY is 9 (low)`() {
        val input = Entity(contentValuesOf(Tasks.PRIORITY to 9))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Priority(9), task.priority)
    }
}
