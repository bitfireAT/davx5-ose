/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeFieldsBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = TimeFieldsBuilder()

    @Test
    fun `VJOURNAL with DTSTART`() {
        val output = Entity(ContentValues())
        val journal = VJournal().apply {
            this += DtStart(dateTimeValue("20260518T120000Z"))
        }
        val main = VJournal()

        builder.build(from = journal, main = main, output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to 1779105600000L,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Z",
                JtxContract.JtxICalObject.DTEND to null,
                JtxContract.JtxICalObject.DTEND_TIMEZONE to null,
                JtxContract.JtxICalObject.DUE to null,
                JtxContract.JtxICalObject.DUE_TIMEZONE to null,
                JtxContract.JtxICalObject.DURATION to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `VJOURNAL without DTSTART`() {
        val output = Entity(ContentValues())
        val journal = VJournal()

        builder.build(from = journal, main = journal, output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to null,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to null,
                JtxContract.JtxICalObject.DTEND to null,
                JtxContract.JtxICalObject.DTEND_TIMEZONE to null,
                JtxContract.JtxICalObject.DUE to null,
                JtxContract.JtxICalObject.DUE_TIMEZONE to null,
                JtxContract.JtxICalObject.DURATION to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `VJOURNAL with DTEND, DUE, and DURATION should ignore properties`() {
        val output = Entity(ContentValues())
        val journal = VJournal().apply {
            this += DtStart(dateTimeValue("20260518T120000Z"))
            this += DtEnd(dateTimeValue("20260518T140000Z"))
            this += Due(dateTimeValue("20260518T140000Z"))
            this += Duration("PT2H")
        }

        builder.build(from = journal, main = journal, output)

        assertEquals(null, output.entityValues.getAsString(JtxContract.JtxICalObject.DTEND))
        assertEquals(null, output.entityValues.getAsString(JtxContract.JtxICalObject.DTEND_TIMEZONE))
        assertEquals(null, output.entityValues.getAsString(JtxContract.JtxICalObject.DUE))
        assertEquals(null, output.entityValues.getAsString(JtxContract.JtxICalObject.DUE_TIMEZONE))
        assertEquals(null, output.entityValues.getAsString(JtxContract.JtxICalObject.DURATION))
    }

    @Test
    fun `VTODO with DTSTART`() {
        val output = Entity(ContentValues())
        val journal = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000Z"))
        }

        builder.build(from = journal, main = journal, output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to 1779105600000L,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Z",
                JtxContract.JtxICalObject.DTEND to null,
                JtxContract.JtxICalObject.DTEND_TIMEZONE to null,
                JtxContract.JtxICalObject.DUE to null,
                JtxContract.JtxICalObject.DUE_TIMEZONE to null,
                JtxContract.JtxICalObject.DURATION to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `VTODO without DTSTART`() {
        val output = Entity(ContentValues())
        val journal = VToDo()

        builder.build(from = journal, main = journal, output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to null,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to null,
                JtxContract.JtxICalObject.DTEND to null,
                JtxContract.JtxICalObject.DTEND_TIMEZONE to null,
                JtxContract.JtxICalObject.DUE to null,
                JtxContract.JtxICalObject.DUE_TIMEZONE to null,
                JtxContract.JtxICalObject.DURATION to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `VTODO with DTSTART using DATE-TIME and DUE using DATE`() {
        val output = Entity(ContentValues())
        val journal = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000", tzVienna))
            this += Due(dateValue("20260520"))
        }

        builder.build(from = journal, main = journal, output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to 1779098400000L,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.DTEND to null,
                JtxContract.JtxICalObject.DTEND_TIMEZONE to null,
                JtxContract.JtxICalObject.DUE to 1779235200000L,
                JtxContract.JtxICalObject.DUE_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.DURATION to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `VTODO with DTSTART using DATE and DUE using DATE-TIME`() {
        val output = Entity(ContentValues())
        val journal = VToDo().apply {
            this += DtStart(dateValue("20260518"))
            this += Due(dateTimeValue("20260520T120000", tzVienna))
        }

        builder.build(from = journal, main = journal, output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to 1779062400000L,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.DTEND to null,
                JtxContract.JtxICalObject.DTEND_TIMEZONE to null,
                JtxContract.JtxICalObject.DUE to 1779271200000L,
                JtxContract.JtxICalObject.DUE_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.DURATION to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `VTODO with DTSTART and DURATION`() {
        val output = Entity(ContentValues())
        val journal = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000", tzVienna))
            this += Duration("P1D")
        }

        builder.build(from = journal, main = journal, output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to 1779098400000L,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.DTEND to null,
                JtxContract.JtxICalObject.DTEND_TIMEZONE to null,
                JtxContract.JtxICalObject.DUE to null,
                JtxContract.JtxICalObject.DUE_TIMEZONE to null,
                JtxContract.JtxICalObject.DURATION to "P1D"
            ),
            output.entityValues
        )
    }

    @Test
    fun `VTODO with DURATION and without DTSTART should ignore DURATION`() {
        val output = Entity(ContentValues())
        val journal = VToDo().apply {
            this += Duration("P1D")
        }

        builder.build(from = journal, main = journal, output)

        assertEquals(null, output.entityValues.getAsString(JtxContract.JtxICalObject.DURATION))
    }

    @Test
    fun `VTODO with DURATION and DUE should ignore DURATION`() {
        val output = Entity(ContentValues())
        val journal = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T100000Z"))
            this += Due(dateTimeValue("20260518T120000Z"))
            this += Duration("P1D")
        }

        builder.build(from = journal, main = journal, output)

        assertEquals(null, output.entityValues.getAsString(JtxContract.JtxICalObject.DURATION))
    }
}
