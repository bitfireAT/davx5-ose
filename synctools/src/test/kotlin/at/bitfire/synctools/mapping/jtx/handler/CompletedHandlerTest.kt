/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.JtxICalObject
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class CompletedHandlerTest {

    private val handler = CompletedHandler()

    @Test
    fun `No COMPLETED`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Completed>(Property.COMPLETED).getOrNull())
    }

    @Test
    fun `COMPLETED with value`() {
        val completed = 1_700_000_000_000L
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COMPLETED to completed))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Completed(Instant.ofEpochMilli(completed)),
            output.getProperty<Completed>(Property.COMPLETED).getOrNull()
        )
    }

    @Test
    fun `COMPLETED with invalid value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COMPLETED to "a"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Completed>(Property.COMPLETED).getOrNull())
    }

    @Test
    fun `COMPLETED without COMPLETED_TIMEZONE has no X-COMPLETEDTIMEZONE`() {
        val completed = 1_700_000_000_000L
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COMPLETED to completed))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Completed(Instant.ofEpochMilli(completed)),
            output.getProperty<Completed>(Property.COMPLETED).getOrNull()
        )
        assertNull(output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull())
    }

    @Test
    fun `COMPLETED with COMPLETED_TIMEZONE adds X-COMPLETEDTIMEZONE`() {
        val completed = 1_700_000_000_000L
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to completed,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "Europe/Vienna"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Completed(Instant.ofEpochMilli(completed)),
            output.getProperty<Completed>(Property.COMPLETED).getOrNull()
        )
        assertEquals(
            "Europe/Vienna",
            output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull()?.value
        )
    }

    @Test
    fun `COMPLETED with UTC COMPLETED_TIMEZONE`() {
        val completed = 1_000_000L
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to completed,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "Z"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            "Z",
            output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull()?.value
        )
    }

    @Test
    fun `COMPLETED with blank COMPLETED_TIMEZONE has no X-COMPLETEDTIMEZONE`() {
        val completed = 1_700_000_000_000L
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to completed,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "  "
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Completed(Instant.ofEpochMilli(completed)),
            output.getProperty<Completed>(Property.COMPLETED).getOrNull()
        )
        assertNull(output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull())
    }

    @Test
    fun `COMPLETED with invalid COMPLETED_TIMEZONE has no X-COMPLETEDTIMEZONE`() {
        val completed = 1_700_000_000_000L
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to completed,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "Not/AZone"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Completed(Instant.ofEpochMilli(completed)),
            output.getProperty<Completed>(Property.COMPLETED).getOrNull()
        )
        assertNull(output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull())
    }

    @Test
    fun `COMPLETED with TZ_ALLDAY is emitted as DATE`() {
        val completed = Instant.parse("2025-08-15T00:00:00Z").toEpochMilli()
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to completed,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to JtxContract.JtxICalObject.TZ_ALLDAY
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Completed(ParameterList(listOf(Value.DATE)), "20250815"),
            output.getProperty<Completed>(Property.COMPLETED).getOrNull()
        )
        assertEquals(
            JtxContract.JtxICalObject.TZ_ALLDAY,
            output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull()?.value
        )
    }

    @Test
    fun `COMPLETED_TIMEZONE without COMPLETED is ignored`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "Europe/Vienna"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Completed>(Property.COMPLETED).getOrNull())
        assertNull(output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull())
    }

    @Test
    fun `COMPLETED is never added to VJOURNAL`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to 1_700_000_000_000L,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "Europe/Vienna"
            )
        )
        val output = VJournal()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Completed>(Property.COMPLETED).getOrNull())
        assertNull(output.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull())
    }
}
