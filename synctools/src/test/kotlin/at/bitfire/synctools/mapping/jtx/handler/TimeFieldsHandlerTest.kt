/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.icalendar.due
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.jtx.builder.TimeFieldsBuilder
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class TimeFieldsHandlerTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = TimeFieldsBuilder()
    private val handler = TimeFieldsHandler()

    private fun buildAndProcess(original: VToDo): VToDo {
        val entity = Entity(ContentValues())
        builder.build(from = original, main = original, to = entity)
        val result = VToDo()
        handler.process(from = entity, main = entity, to = result)
        return result
    }

    @Test
    fun `No time fields`() {
        val result = buildAndProcess(VToDo())

        assertNull(result.dtStart<Temporal>())
        assertNull(result.dtEnd<Temporal>())
        assertNull(result.due<Temporal>())
        assertNull(result.getProperty<Duration>(Property.DURATION).getOrNull())
    }

    @Test
    fun `DTSTART UTC round-trip`() {
        val original = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000Z"))
        }

        val result = buildAndProcess(original)

        assertEquals(original.dtStart<Temporal>(), result.dtStart<Temporal>())
        assertNull(result.due<Temporal>())
    }

    @Test
    fun `DTSTART with timezone round-trip`() {
        val original = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000", tzVienna))
        }

        val result = buildAndProcess(original)

        assertEquals(original.dtStart<Temporal>(), result.dtStart<Temporal>())
    }

    @Test
    fun `DTSTART floating round-trip`() {
        val original = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000"))
        }

        val result = buildAndProcess(original)

        assertEquals(original.dtStart<Temporal>(), result.dtStart<Temporal>())
    }

    @Test
    fun `DTSTART all-day round-trip`() {
        val original = VToDo().apply {
            this += DtStart(dateValue("20260518"))
        }

        val result = buildAndProcess(original)

        assertEquals(original.dtStart<Temporal>(), result.dtStart<Temporal>())
    }

    @Test
    fun `DTSTART and DUE with timezone round-trip`() {
        val original = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000", tzVienna))
            this += Due(dateTimeValue("20260520T120000", tzVienna))
        }

        val result = buildAndProcess(original)

        assertEquals(original.dtStart<Temporal>(), result.dtStart<Temporal>())
        assertEquals(original.due<Temporal>(), result.due<Temporal>())
    }

    @Test
    fun `DTSTART and DURATION round-trip`() {
        val original = VToDo().apply {
            this += DtStart(dateTimeValue("20260518T120000", tzVienna))
            this += Duration("P1D")
        }

        val result = buildAndProcess(original)

        assertEquals(original.dtStart<Temporal>(), result.dtStart<Temporal>())
        assertEquals(
            original.getProperty<Duration>(Property.DURATION).getOrNull()?.value,
            result.getProperty<Duration>(Property.DURATION).getOrNull()?.value
        )
    }
}
