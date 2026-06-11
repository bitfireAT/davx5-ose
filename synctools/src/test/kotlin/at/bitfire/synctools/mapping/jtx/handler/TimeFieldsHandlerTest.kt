/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.dtEnd
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.icalendar.due
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class TimeFieldsHandlerTest {

    private val handler = TimeFieldsHandler()

    @Test
    fun `No time fields`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.dtStart<Temporal>())
        assertNull(output.dtEnd<Temporal>())
        assertNull(output.due<Temporal>())
        assertNull(output.getProperty<Duration>(Property.DURATION).getOrNull())
    }

    @Test
    fun `DTSTART UTC`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.DTSTART to "20260518T120000Z"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val dtStart = output.dtStart<Temporal>()
        assertEquals("20260518T120000Z", dtStart?.value)
        assertNull(output.dtEnd<Temporal>())
        assertNull(output.due<Temporal>())
    }

    @Test
    fun `DTSTART with timezone`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to "20260518T120000",
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val dtStart = output.dtStart<Temporal>()
        assertNotNull(dtStart)
        assertEquals("20260518T120000", dtStart.value)
        assertEquals("Europe/Vienna", dtStart.getParameter<TzId>("TZID").getOrNull()?.value)
    }

    @Test
    fun `DTSTART floating (no timezone)`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.DTSTART to "20260518T120000"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val dtStart = output.dtStart<Temporal>()
        assertNotNull(dtStart)
        assertEquals("20260518T120000", dtStart.value)
        assertNull(dtStart.getParameter<TzId>("TZID").getOrNull())
    }

    @Test
    fun `DTSTART and DUE with timezones`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to "20260518T120000",
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.DUE to "20260520T120000",
                JtxContract.JtxICalObject.DUE_TIMEZONE to "Europe/Vienna"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val dtStart = output.dtStart<Temporal>()
        val due = output.due<Temporal>()

        assertNotNull(dtStart)
        assertNotNull(due)

        assertEquals("20260518T120000", dtStart.value)
        assertEquals("20260520T120000", due.value)
        assertEquals("Europe/Vienna", due.getParameter<TzId>("TZID")?.getOrNull()?.value)
    }

    @Test
    fun `DTEND with timezone`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to "20260518T120000Z",
                JtxContract.JtxICalObject.DTEND to "20260518T140000",
                JtxContract.JtxICalObject.DTEND_TIMEZONE to "Europe/Vienna"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val dtEnd = output.dtEnd<Temporal>()

        assertNotNull(dtEnd)

        assertEquals("20260518T140000", dtEnd.value)
        assertEquals(
            "Europe/Vienna",
            dtEnd.getParameter<TzId>("TZID")?.getOrNull()?.value
        )
    }

    @Test
    fun `DURATION only`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.DURATION to "P1D"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("P1D", output.getProperty<Duration>(Property.DURATION).getOrNull()?.value)
        assertNull(output.dtStart<Temporal>())
    }

    @Test
    fun `DTSTART with DURATION`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to "20260518T120000Z",
                JtxContract.JtxICalObject.DURATION to "PT2H"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("20260518T120000Z", output.dtStart<Temporal>()?.value)
        assertEquals("PT2H", output.getProperty<Duration>(Property.DURATION).getOrNull()?.value)
    }
}
