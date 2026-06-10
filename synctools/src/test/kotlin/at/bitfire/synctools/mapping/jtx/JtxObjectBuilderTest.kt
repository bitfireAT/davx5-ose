/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import at.bitfire.synctools.icalendar.AssociatedComponents
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        assertEquals("VTODO", result.main.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
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
        assertEquals("VTODO", result.main.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
        assertEquals("VTODO", result.exceptions.single().entityValues.get(JtxContract.JtxICalObject.COMPONENT))
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
        assertEquals("VJOURNAL", result.main.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
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
        assertEquals("VJOURNAL", result.main.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
        assertEquals("VJOURNAL", result.exceptions.single().entityValues.get(JtxContract.JtxICalObject.COMPONENT))
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
}
