/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Duration
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DurationHandlerTest {

    private val handler = DurationHandler()

    @Test
    fun `legacy No DURATION leaves duration null`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.duration)
    }

    @Test
    fun `legacy DURATION PT1H is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.DURATION to "PT1H"), task)
        assertEquals(Duration(AndroidTimeUtils.parseDuration("PT1H")), task.duration)
    }

    @Test
    fun `legacy DURATION P1D is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.DURATION to "P1D"), task)
        assertEquals(Duration(AndroidTimeUtils.parseDuration("P1D")), task.duration)
    }

    @Test
    fun `No DURATION leaves duration null`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.duration)
    }

    @Test
    fun `DURATION PT1H is mapped correctly`() {
        val input = Entity(contentValuesOf(Tasks.DURATION to "PT1H"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Duration("PT1H"), task.duration)
    }

    @Test
    fun `DURATION P1D is mapped correctly`() {
        val input = Entity(contentValuesOf(Tasks.DURATION to "P1D"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Duration("P1D"), task.duration)
    }
}
