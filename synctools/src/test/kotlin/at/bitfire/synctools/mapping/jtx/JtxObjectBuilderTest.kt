/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import android.content.Entity
import android.net.Uri
import at.bitfire.synctools.icalendar.AssociatedComponents
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.StringReader
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class JtxObjectBuilderTest {

    private val builder = JtxObjectBuilder(
        collectionId = 1,
        fileName = null,
        eTag = null,
        scheduleTag = null,
        flags = 0
    )

    @Test
    fun `build() with VToDo`() {
        val component = AssociatedComponents<CalendarComponent>(
            main = VToDo(),
            exceptions = emptyList()
        )

        val result = builder.build(component)

        assertNotNull(result.main)
        assertTrue(result.exceptions.isEmpty())
        assertEquals("VTODO", result.main.entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }

    @Test
    fun `build() with VToDo and exception`() {
        val exception = VToDo().apply {
            this += RecurrenceId(Instant.now())
        }
        val component = AssociatedComponents<CalendarComponent>(
            main = VToDo(),
            exceptions = listOf(exception)
        )

        val result = builder.build(component)

        assertNotNull(result.main)
        assertFalse(result.exceptions.isEmpty())
        assertEquals("VTODO", result.main.entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
        assertEquals("VTODO", result.exceptions.single().entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }

    @Test
    fun `build() maps PRIORITY to PRIORITY`() {
        val main = VToDo().apply {
            this += Priority(5)
        }
        val component = AssociatedComponents<CalendarComponent>(
            main = main,
            exceptions = emptyList()
        )

        val result = builder.build(component)

        assertEquals(5, result.main.entity.entityValues.get(JtxContract.JtxICalObject.PRIORITY))
    }

    @Test
    fun `build() maps X-STATUS to EXTENDED_STATUS`() {
        val main = VToDo().apply {
            this += XProperty(JtxProperty.X_XSTATUS, "Bla")
        }
        val component = AssociatedComponents<CalendarComponent>(
            main = main,
            exceptions = emptyList()
        )

        val result = builder.build(component)

        assertEquals(
            "Bla",
            result.main.entity.entityValues.getAsString(JtxContract.JtxICalObject.EXTENDED_STATUS)
        )
    }

    @Test
    fun `build() wires implemented builders`() {
        val calendar = ICalendarParser().parse(
            StringReader(
                """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//bitfire//test//EN
            BEGIN:VTODO
            UID:rich-uid
            SUMMARY:Rich task
            DESCRIPTION:Description
            CLASS:PRIVATE
            PRIORITY:4
            STATUS:IN-PROCESS
            X-STATUS:custom-status
            PERCENT-COMPLETE:40
            COMPLETED:20260610T120000Z
            X-COMPLETEDTIMEZONE:UTC
            CREATED:20260609T120000Z
            LAST-MODIFIED:20260609T130000Z
            DTSTAMP:20260609T140000Z
            SEQUENCE:3
            COLOR:#FF112233
            CONTACT:Jane Contact
            GEO:37.386013;-122.082932
            X-GEOFENCE-RADIUS:25
            LOCATION;ALTREP="https://domain.example/location":Conference Room
            URL:https://domain.example/task
            DTSTART:20260610T090000Z
            DUE:20260610T100000Z
            RRULE:FREQ=DAILY;COUNT=2
            ATTENDEE;CN=Jane Attendee:mailto:attendee@domain.example
            CATEGORIES:one,two
            COMMENT:comment
            ORGANIZER;CN=Jane Organizer:mailto:organizer@domain.example
            RELATED-TO:parent-uid
            RESOURCES:projector
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:reminder
            TRIGGER:-PT15M
            END:VALARM
            ATTACH;FMTTYPE=text/plain;FILENAME=file.txt:https://domain.example/file.txt
            X-UNKNOWN:unknown
            END:VTODO
            END:VCALENDAR
            """.trimIndent()
            )
        )
        val task = calendar.getComponent<VToDo>(Component.VTODO).get()
        val builder = JtxObjectBuilder(
            collectionId = 23,
            fileName = "rich.ics",
            eTag = "etag",
            scheduleTag = "schedule-tag",
            flags = 7
        )
        val component = AssociatedComponents<CalendarComponent>(
            main = task,
            exceptions = emptyList()
        )

        val result = builder.build(component)

        val values = result.main.entity.entityValues
        assertEquals(23L, values.getAsLong(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID))
        assertEquals("rich.ics", values.getAsString(JtxContract.JtxICalObject.FILENAME))
        assertEquals("etag", values.getAsString(JtxContract.JtxICalObject.ETAG))
        assertEquals("schedule-tag", values.getAsString(JtxContract.JtxICalObject.SCHEDULETAG))
        assertEquals(7, values.getAsInteger(JtxContract.JtxICalObject.FLAGS))
        assertEquals(false, values.getAsBoolean(JtxContract.JtxICalObject.DIRTY))
        assertEquals(false, values.getAsBoolean(JtxContract.JtxICalObject.DELETED))
        assertEquals("rich-uid", values.getAsString(JtxContract.JtxICalObject.UID))
        assertEquals("Rich task", values.getAsString(JtxContract.JtxICalObject.SUMMARY))
        assertEquals("PRIVATE", values.getAsString(JtxContract.JtxICalObject.CLASSIFICATION))
        assertEquals("IN-PROCESS", values.getAsString(JtxContract.JtxICalObject.STATUS))
        assertEquals("Conference Room", values.getAsString(JtxContract.JtxICalObject.LOCATION))
        assertEquals("https://domain.example/task", values.getAsString(JtxContract.JtxICalObject.URL))
        assertEquals(40, values.getAsInteger(JtxContract.JtxICalObject.PERCENT))
        assertEquals(3L, values.getAsLong(JtxContract.JtxICalObject.SEQUENCE))
        assertEquals(0xFF112233.toInt(), values.getAsInteger(JtxContract.JtxICalObject.COLOR))
        assertEquals("Jane Contact", values.getAsString(JtxContract.JtxICalObject.CONTACT))
        assertEquals(25, values.getAsInteger(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
        assertNotNull(values.get(JtxContract.JtxICalObject.COMPLETED))
        assertNotNull(values.get(JtxContract.JtxICalObject.CREATED))
        assertNotNull(values.get(JtxContract.JtxICalObject.LAST_MODIFIED))
        assertNotNull(values.get(JtxContract.JtxICalObject.DTSTAMP))
        assertNotNull(values.get(JtxContract.JtxICalObject.DTSTART))
        assertNotNull(values.get(JtxContract.JtxICalObject.DUE))
        assertEquals("FREQ=DAILY;COUNT=2", values.getAsString(JtxContract.JtxICalObject.RRULE))

        val subValues = result.main.entity.subValues
        assertSubValue(
            subValues,
            JtxContract.JtxAttendee.CONTENT_URI,
            JtxContract.JtxAttendee.CALADDRESS,
            "mailto:attendee@domain.example"
        )
        assertSubValue(subValues, JtxContract.JtxCategory.CONTENT_URI, JtxContract.JtxCategory.TEXT, "one")
        assertSubValue(subValues, JtxContract.JtxComment.CONTENT_URI, JtxContract.JtxComment.TEXT, "comment")
        assertSubValue(
            subValues,
            JtxContract.JtxOrganizer.CONTENT_URI,
            JtxContract.JtxOrganizer.CALADDRESS,
            "mailto:organizer@domain.example"
        )
        assertSubValue(subValues, JtxContract.JtxRelatedto.CONTENT_URI, JtxContract.JtxRelatedto.TEXT, "parent-uid")
        assertSubValue(subValues, JtxContract.JtxResource.CONTENT_URI, JtxContract.JtxResource.TEXT, "projector")
        assertSubValue(subValues, JtxContract.JtxAlarm.CONTENT_URI, JtxContract.JtxAlarm.DESCRIPTION, "reminder")
        assertTrue(subValues.any {
            it.uri == JtxContract.JtxUnknown.CONTENT_URI &&
                    it.values.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE).contains("X-UNKNOWN")
        })

        val attachment = result.main.binaryDataRows.single()
        assertEquals(JtxContract.JtxAttachment.CONTENT_URI, attachment.uri)
        assertEquals("https://domain.example/file.txt", attachment.values.getAsString(JtxContract.JtxAttachment.URI))
    }

    @Test
    fun `build() with VJournal`() {
        val component = AssociatedComponents<CalendarComponent>(
            main = VJournal(),
            exceptions = emptyList()
        )

        val result = builder.build(component)

        assertNotNull(result.main)
        assertTrue(result.exceptions.isEmpty())
        assertEquals("VJOURNAL", result.main.entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }

    @Test
    fun `build() with VJournal and exception`() {
        val exception = VJournal().apply {
            this += RecurrenceId(Instant.now())
        }
        val component = AssociatedComponents<CalendarComponent>(
            main = VJournal(),
            exceptions = listOf(exception)
        )

        val result = builder.build(component)

        assertNotNull(result.main)
        assertFalse(result.exceptions.isEmpty())
        assertEquals("VJOURNAL", result.main.entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
        assertEquals("VJOURNAL", result.exceptions.single().entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }

    @Test
    fun `build() with VJournal and VToDo exception should throw`() {
        val exception = VToDo().apply {
            this += RecurrenceId(Instant.now())
        }
        val component = AssociatedComponents<CalendarComponent>(
            main = VJournal(),
            exceptions = listOf(exception)
        )

        try {
            builder.build(component)
            fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Exceptions need to be of same type as main component", e.message)
        }
    }

    @Test
    fun `build() with VToDo exception and without main component`() {
        val exception = VToDo().apply {
            this += RecurrenceId(Instant.now())
        }
        val component = AssociatedComponents<CalendarComponent>(
            main = null,
            exceptions = listOf(exception)
        )

        val result = builder.build(component)

        assertNotNull(result.main)
        assertFalse(result.exceptions.isEmpty())
        assertEquals("VTODO", result.main.entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
        assertEquals("VTODO", result.exceptions.single().entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }

    @Test
    fun `build() with VJournal exception and without main component`() {
        val exception = VJournal().apply {
            this += RecurrenceId(Instant.now())
        }
        val component = AssociatedComponents<CalendarComponent>(
            main = null,
            exceptions = listOf(exception)
        )

        val result = builder.build(component)

        assertNotNull(result.main)
        assertFalse(result.exceptions.isEmpty())
        assertEquals("VJOURNAL", result.main.entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
        assertEquals("VJOURNAL", result.exceptions.single().entity.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }

    private fun assertSubValue(
        subValues: List<Entity.NamedContentValues>,
        uri: Uri,
        column: String,
        value: String
    ) {
        assertTrue(subValues.any { it.uri == uri && it.values.getAsString(column) == value })
    }
}
