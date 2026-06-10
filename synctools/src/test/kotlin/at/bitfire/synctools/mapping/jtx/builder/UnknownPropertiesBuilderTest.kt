/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.StringReader
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesBuilderTest {

    private val builder = UnknownPropertiesBuilder()

    @Test
    fun `known properties should be ignored`() {
        val calendar = ICalendarParser().parse(StringReader(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:something
            BEGIN:VTODO
            ATTACH:https://domain.example/image.jpeg
            ATTENDEE:mailto:attendee@domain.example
            CATEGORIES:ONE,TWO
            CLASS:PUBLIC
            COLOR:aliceblue
            COMMENT:comment
            COMPLETED:20260610T120000Z
            CONTACT:Jim Dolittle\, ABC Industries\, +1-919-555-1234
            CREATED:20260610T120000Z
            DESCRIPTION:description
            DTEND:20260610T120000Z
            DTSTART:20260610T120000Z
            DUE:20260610T120000Z
            DURATION:PT1S
            EXDATE:20260611T120000Z
            GEO:37.386013;-122.082932
            LAST-MODIFIED:20260610T120000Z
            LOCATION:location
            ORGANIZER:mailto:organizer@domain.example
            PERCENT-COMPLETE:3
            PRIORITY:1
            RECURRENCE-ID:20260610T120000Z
            RELATED-TO:other-uid
            RESOURCES:thing
            RDATE:20260610T140000Z
            RRULE:FREQ=DAILY;COUNT=2
            SEQUENCE:1
            STATUS:NEEDS-ACTION
            SUMMARY:summary
            UID:uid
            URL:https://domain.example/
            DTSTAMP:20260610T120000Z
            PRODID:why here
            END:VTODO
            END:VCALENDAR
            """.trimIndent()
        ))
        val task = calendar.getComponent<VToDo>(Component.VTODO).get()
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertTrue(output.subValues.isEmpty())
    }

    @Test
    fun `unknown properties`() {
        val task = VToDo(
            propertyListOf(
                XProperty("X-UNKNOWN-1", "one"),
                XProperty("X-UNKNOWN-2", ParameterList(listOf(XParameter("key", "value"))), "two")
            )
        )
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertEquals(2, output.subValues.size)
        val unknownPropertyOne = output.subValues.first {
            "X-UNKNOWN-1" in it.values.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE)
        }
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN-1","one"]"""
            ),
            unknownPropertyOne.values
        )
        val unknownPropertyTwo = output.subValues.first {
            "X-UNKNOWN-2" in it.values.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE)
        }
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN-2","two",{"key":"value"}]"""
            ),
            unknownPropertyTwo.values
        )
    }
}
