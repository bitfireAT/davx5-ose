/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Status
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusHandlerTest {

    private val handler = StatusHandler()


    @Test
    fun `No STATUS defaults to NEEDS-ACTION`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Status(Status.VALUE_NEEDS_ACTION), task.status)
    }

    @Test
    fun `STATUS_NEEDS_ACTION maps to NEEDS-ACTION`() {
        val input = Entity(contentValuesOf(Tasks.STATUS to Tasks.STATUS_NEEDS_ACTION))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Status(Status.VALUE_NEEDS_ACTION), task.status)
    }

    @Test
    fun `STATUS_IN_PROCESS maps to IN-PROCESS`() {
        val input = Entity(contentValuesOf(Tasks.STATUS to Tasks.STATUS_IN_PROCESS))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Status(Status.VALUE_IN_PROCESS), task.status)
    }

    @Test
    fun `STATUS_COMPLETED maps to COMPLETED`() {
        val input = Entity(contentValuesOf(Tasks.STATUS to Tasks.STATUS_COMPLETED))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Status(Status.VALUE_COMPLETED), task.status)
    }

    @Test
    fun `STATUS_CANCELLED maps to CANCELLED`() {
        val input = Entity(contentValuesOf(Tasks.STATUS to Tasks.STATUS_CANCELLED))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Status(Status.VALUE_CANCELLED), task.status)
    }

    @Test
    fun `Unknown STATUS defaults to NEEDS-ACTION`() {
        val input = Entity(contentValuesOf(Tasks.STATUS to 99))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Status(Status.VALUE_NEEDS_ACTION), task.status)
    }

}
