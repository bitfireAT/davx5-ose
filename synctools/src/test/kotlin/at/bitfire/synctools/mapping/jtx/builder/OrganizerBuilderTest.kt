/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.parameterListOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerBuilderTest {

    private val builder = OrganizerBuilder()

    @Test
    fun `No ORGANIZER`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertTrue(output.subValues.isEmpty())
    }

    @Test
    fun `ORGANIZER with mailto`() {
        val task = VToDo(propertyListOf(
            Organizer(parameterListOf(), "mailto:john@example.com")
        ))
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertEquals(JtxContract.JtxOrganizer.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxOrganizer.CALADDRESS to "mailto:john@example.com",
                JtxContract.JtxOrganizer.CN to null,
                JtxContract.JtxOrganizer.DIR to null,
                JtxContract.JtxOrganizer.SENTBY to null,
                JtxContract.JtxOrganizer.LANGUAGE to null,
                JtxContract.JtxOrganizer.OTHER to null
            ),
            subValue.values
        )
    }

    @Test
    fun `ORGANIZER with CN parameter`() {
        val task = VToDo(propertyListOf(
            Organizer(parameterListOf(Cn("John Doe")), "mailto:john@example.com")
        ))
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxOrganizer.CALADDRESS to "mailto:john@example.com",
                JtxContract.JtxOrganizer.CN to "John Doe",
                JtxContract.JtxOrganizer.DIR to null,
                JtxContract.JtxOrganizer.SENTBY to null,
                JtxContract.JtxOrganizer.LANGUAGE to null,
                JtxContract.JtxOrganizer.OTHER to null
            ),
            subValue.values
        )
    }

    @Test
    fun `ORGANIZER with multiple parameters`() {
        val task = VToDo(propertyListOf(
            Organizer(
                parameterListOf(
                    Cn("John Doe"),
                    Dir("ldap://example.com/o=ABC,c=US???(cn=John%20Doe)"),
                    SentBy("mailto:assistant@example.com"),
                    Language("en-US")
                ),
                "mailto:john@example.com"
            )
        ))
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxOrganizer.CALADDRESS to "mailto:john@example.com",
                JtxContract.JtxOrganizer.CN to "John Doe",
                JtxContract.JtxOrganizer.DIR to "ldap://example.com/o=ABC,c=US???(cn=John%20Doe)",
                JtxContract.JtxOrganizer.SENTBY to "mailto:assistant@example.com",
                JtxContract.JtxOrganizer.LANGUAGE to "en-US",
                JtxContract.JtxOrganizer.OTHER to null
            ),
            subValue.values
        )
    }

    @Test
    fun `ORGANIZER with X-parameter`() {
        val task = VToDo(propertyListOf(
            Organizer(
                parameterListOf(
                    Cn("John Doe"),
                    XParameter("X-CUSTOM", "custom-value")
                ),
                "mailto:john@example.com"
            )
        ))
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxOrganizer.CALADDRESS to "mailto:john@example.com",
                JtxContract.JtxOrganizer.CN to "John Doe",
                JtxContract.JtxOrganizer.DIR to null,
                JtxContract.JtxOrganizer.SENTBY to null,
                JtxContract.JtxOrganizer.LANGUAGE to null,
                JtxContract.JtxOrganizer.OTHER to """{"X-CUSTOM":"custom-value"}"""
            ),
            subValue.values
        )
    }

    @Test
    fun `ORGANIZER for VJournal`() {
        val journal = VJournal(propertyListOf(
            Organizer(parameterListOf(Cn("Jane Smith")), "mailto:jane@example.com")
        ))
        val output = Entity(ContentValues())

        builder.build(from = journal, main = journal, to = output)

        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertEquals(JtxContract.JtxOrganizer.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxOrganizer.CALADDRESS to "mailto:jane@example.com",
                JtxContract.JtxOrganizer.CN to "Jane Smith",
                JtxContract.JtxOrganizer.DIR to null,
                JtxContract.JtxOrganizer.SENTBY to null,
                JtxContract.JtxOrganizer.LANGUAGE to null,
                JtxContract.JtxOrganizer.OTHER to null
            ),
            subValue.values
        )
    }
}
