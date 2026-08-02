/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import android.net.Uri
import at.bitfire.dateTimeValue
import at.bitfire.synctools.exception.ResourceMappingException
import at.bitfire.synctools.icalendar.AssociatedTasks
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Uid
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class DmfsTaskBuilderTest {

    private val taskList = mockk<DmfsTaskList> {
        every { id } returns 1L
        every { tasksPropertiesUri() } returns Uri.parse("content://org.dmfs.tasks/properties")
    }

    private val builder = DmfsTaskBuilder(
        taskList = taskList,
        syncId = null,
        eTag = null,
        flags = 0
    )

    // expected: Currently not supported. Tasks depend on recurrence support first to support this.
    @Test(expected = ResourceMappingException::class)
    fun `build synthesizes recurring main when only exceptions are present`() {
        val exception = VToDoUtil.build(
            Uid("uid-1"),
            DtStart(dateTimeValue("20260101T120000Z")),
            RecurrenceId(dateTimeValue("20260101T120000Z"))
        )

        val result = builder.build(
            AssociatedTasks(
                main = null,
                exceptions = listOf(exception)
            )
        )

        assertNotNull(result.main.entityValues.getAsString(Tasks.RDATE))
        assertEquals(null, result.exceptions.first().entityValues.getAsString(Tasks.RRULE))
        assertEquals(null, result.exceptions.first().entityValues.getAsString(Tasks.RDATE))
    }

    // Drop this test once recurring task exception support is implemented (#2357)
    @Test
    fun `build with main task and exceptions drops exceptions`() {
        val main = VToDoUtil.build(
            Uid("uid-1"),
            DtStart(dateTimeValue("20250101T000000Z")),
            RRule<Temporal>("FREQ=YEARLY")
        )
        val exception = VToDoUtil.build(
            Uid("uid-1"),
            DtStart(dateTimeValue("20260101T000000Z")),
            RecurrenceId(dateTimeValue("20260101T000000Z"))
        )

        val result = builder.build(
            AssociatedTasks(main = main, exceptions = listOf(exception))
        )

        assertEquals(0, result.exceptions.size)
    }
}
