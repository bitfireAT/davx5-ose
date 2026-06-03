/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PercentCompleteHandlerTest {

    private val handler = PercentCompleteHandler()

    @Test
    fun `legacy No PERCENT_COMPLETE leaves percentComplete null`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.percentComplete)
    }

    @Test
    fun `legacy PERCENT_COMPLETE 0 is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PERCENT_COMPLETE to 0), task)
        assertEquals(0, task.percentComplete)
    }

    @Test
    fun `legacy PERCENT_COMPLETE 100 is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PERCENT_COMPLETE to 100), task)
        assertEquals(100, task.percentComplete)
    }

    @Test
    fun `No PERCENT_COMPLETE leaves percentComplete null`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.percentComplete)
    }

    @Test
    fun `PERCENT_COMPLETE 0 is mapped correctly`() {
        val input = Entity(contentValuesOf(Tasks.PERCENT_COMPLETE to 0))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(PercentComplete(0), task.percentComplete)
    }

    @Test
    fun `PERCENT_COMPLETE 100 is mapped correctly`() {
        val input = Entity(contentValuesOf(Tasks.PERCENT_COMPLETE to 100))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(PercentComplete(100), task.percentComplete)
    }
}
