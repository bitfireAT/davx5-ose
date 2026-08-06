/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks

import android.net.Uri
import at.bitfire.dateTimeValue
import at.bitfire.parameterListOf
import at.bitfire.synctools.exception.ResourceMappingException
import at.bitfire.synctools.icalendar.AssociatedTasks
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskAlarms
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.Uid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DavTaskBuilderTest {

    private val taskList = mockk<DavTaskList> {
        every { id } returns 1L
        every { tasksPropertiesUri(asSyncAdapter = false) } returns Uri.parse("content://test.authority/properties")
        every { tasksAlarmsUri(asSyncAdapter = false) } returns Uri.parse("content://test.authority/alarms")
    }

    private fun builder(syncId: String? = null, eTag: String? = null, flags: Int = 0) =
        DavTaskBuilder(taskList = taskList, syncId = syncId, eTag = eTag, flags = flags)

    private fun propertyRows(entity: android.content.Entity, mimetype: String) =
        entity.subValues.filter { it.values.getAsString(TaskProperties.MIMETYPE) == mimetype }


    // recurrence exceptions (matches the pre-existing DMFS backend limitation, #2357)

    @Test(expected = ResourceMappingException::class)
    fun `build throws when main task is missing`() {
        val exception = VToDoUtil.build(Uid("uid-1"), RecurrenceId(dateTimeValue("20260101T120000Z")))
        builder().build(AssociatedTasks(main = null, exceptions = listOf(exception)))
    }

    @Test
    fun `build drops recurrence exceptions`() {
        val main = VToDoUtil.build(Uid("uid-1"), DtStart(dateTimeValue("20250101T000000Z")))
        val exception = VToDoUtil.build(Uid("uid-1"), RecurrenceId(dateTimeValue("20260101T000000Z")))

        val result = builder().build(AssociatedTasks(main = main, exceptions = listOf(exception)))

        assertEquals(0, result.exceptions.size)
    }


    // main-row fields

    @Test
    fun `build maps basic fields`() {
        val main = VToDoUtil.build(
            Uid("uid-1"),
            net.fortuna.ical4j.model.property.Summary("Buy milk"),
            net.fortuna.ical4j.model.property.Priority(5)
        )

        val result = builder(syncId = "task1.ics", eTag = "etag123").build(AssociatedTasks(main = main, exceptions = emptyList()))

        val values = result.main.entityValues
        assertEquals("uid-1", values.getAsString(Tasks._UID))
        assertEquals("task1.ics", values.getAsString(Tasks._SYNC_ID))
        assertEquals("Buy milk", values.getAsString(Tasks.SUMMARY))
        assertEquals(5, values.getAsInteger(Tasks.PRIORITY))
        assertEquals(1L, values.getAsLong(Tasks.LIST_ID))
        assertEquals(false, values.getAsBoolean(Tasks._DIRTY))
    }

    /**
     * Unlike the DMFS backend (which maps ORGANIZER to just an email address, dropping CN/SENT-BY),
     * this contract stores the full CAL-ADDRESS plus CN and SENT-BY (§1 of the design doc).
     */
    @Test
    fun `build maps organizer with CN and SENT-BY`() {
        val organizer = Organizer("mailto:boss@example.com").apply {
            this += Cn("The Boss")
            this += SentBy("mailto:secretary@example.com")
        }
        val main = VToDoUtil.build(Uid("uid-1"), organizer)

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        val values = result.main.entityValues
        assertEquals("mailto:boss@example.com", values.getAsString(Tasks.ORGANIZER))
        assertEquals("The Boss", values.getAsString(Tasks.ORGANIZER_CN))
        assertEquals("mailto:secretary@example.com", values.getAsString(Tasks.ORGANIZER_SENT_BY))
    }


    // sub-rows: Properties

    @Test
    fun `build maps categories as separate rows`() {
        val main = VToDoUtil.build(
            Uid("uid-1"),
            net.fortuna.ical4j.model.property.Categories(net.fortuna.ical4j.model.TextList("Home,Errands"))
        )

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        val rows = propertyRows(result.main, TaskProperties.MIMETYPE_CATEGORY)
        assertEquals(setOf("Home", "Errands"), rows.map { it.values.getAsString(TaskProperties.DATA1) }.toSet())
    }

    /**
     * Full ATTENDEE parameter fidelity, unlike the DMFS backend which maps to Android's lossy
     * ATTENDEE_TYPE/ATTENDEE_RELATIONSHIP enums.
     */
    @Test
    fun `build maps attendee with full parameters`() {
        val attendee = Attendee(URI("mailto:alice@example.com")).apply {
            this += Cn("Alice")
            this += CuType.INDIVIDUAL
            this += Role.REQ_PARTICIPANT
            this += PartStat.NEEDS_ACTION
            this += Rsvp.TRUE
        }
        val main = VToDoUtil.build(Uid("uid-1"), attendee)

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        val row = propertyRows(result.main, TaskProperties.MIMETYPE_ATTENDEE).single()
        assertEquals("mailto:alice@example.com", row.values.getAsString(TaskProperties.DATA1))
        assertEquals("Alice", row.values.getAsString(TaskProperties.DATA2))
        assertEquals(CuType.INDIVIDUAL.value, row.values.getAsString(TaskProperties.DATA3))
        assertEquals(Role.REQ_PARTICIPANT.value, row.values.getAsString(TaskProperties.DATA10))
        assertEquals(PartStat.NEEDS_ACTION.value, row.values.getAsString(TaskProperties.DATA9))
        assertEquals(Rsvp.TRUE.value, row.values.getAsString(TaskProperties.DATA11))
    }

    /**
     * RFC 9253 task-dependency RELTYPE values must round-trip losslessly - unlike the DMFS
     * backend, which only recognizes PARENT/CHILD/SIBLING and silently collapses everything else.
     */
    @Test
    fun `build maps RFC 9253 RELTYPE values verbatim`() {
        val relatedTo = RelatedTo(parameterListOf(RelType("DEPENDS-ON")), "other-task-uid")
        val main = VToDoUtil.build(Uid("uid-1"), relatedTo)

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        val row = propertyRows(result.main, TaskProperties.MIMETYPE_RELATION).single()
        assertEquals("other-task-uid", row.values.getAsString(TaskProperties.DATA1))
        assertEquals("DEPENDS-ON", row.values.getAsString(TaskProperties.DATA2))
    }


    // sub-rows: Alarms - full VALARM fidelity, unlike the DMFS backend's minutes_before collapse

    @Test
    fun `build maps alarm with relative trigger`() {
        val alarm = VAlarm(propertyListOf(
            Action(Action.VALUE_DISPLAY),
            Trigger(java.time.Duration.ofMinutes(-15))
        ))
        val main = VToDoUtil.build(listOf(Uid("uid-1")), listOf(alarm))

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        val row = result.main.subValues.first { it.values.containsKey(TaskAlarms.ACTION) }
        assertEquals(TaskAlarms.Action.DISPLAY, row.values.getAsString(TaskAlarms.ACTION))
        assertEquals("PT-15M", row.values.getAsString(TaskAlarms.TRIGGER_RELATIVE))
        assertNull(row.values.getAsLong(TaskAlarms.TRIGGER_ABSOLUTE))
    }

    @Test
    fun `build maps alarm with absolute trigger`() {
        val absoluteTime = Instant.parse("2026-01-01T09:00:00Z")
        val alarm = VAlarm(propertyListOf(
            Action(Action.VALUE_DISPLAY),
            Trigger(absoluteTime)
        ))
        val main = VToDoUtil.build(listOf(Uid("uid-1")), listOf(alarm))

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        val row = result.main.subValues.first { it.values.containsKey(TaskAlarms.ACTION) }
        assertEquals(absoluteTime.toEpochMilli(), row.values.getAsLong(TaskAlarms.TRIGGER_ABSOLUTE))
        assertNull(row.values.getAsString(TaskAlarms.TRIGGER_RELATIVE))
    }


    // due/duration mutual exclusivity is a caller responsibility (RFC 5545 §3.6.2), but both must
    // map through when the source data only has one of them

    @Test
    fun `build maps due`() {
        val main = VToDoUtil.build(Uid("uid-1"), Due(dateTimeValue("20260115T170000Z")))

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        assertNotNull(result.main.entityValues.getAsLong(Tasks.DUE))
    }

    @Test
    fun `build maps duration`() {
        val main = VToDoUtil.build(Uid("uid-1"), DtStart(dateTimeValue("20260115T170000Z")), Duration(java.time.Duration.ofHours(2)))

        val result = builder().build(AssociatedTasks(main = main, exceptions = emptyList()))

        assertNotNull(result.main.entityValues.getAsString(Tasks.DURATION))
    }

}
