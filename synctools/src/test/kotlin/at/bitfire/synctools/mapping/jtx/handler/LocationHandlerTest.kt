/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.property.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class LocationHandlerTest {

    private val handler = LocationHandler()

    @Test
    fun `No LOCATION`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.location)
    }

    @Test
    fun `LOCATION set`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.LOCATION to "Vienna, Austria"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Location("Vienna, Austria"), output.location)
    }

    @Test
    fun `LOCATION with ALTREP`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.LOCATION to "Office",
                JtxContract.JtxICalObject.LOCATION_ALTREP to "https://example.com/location"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("Office", output.location.value)
        assertEquals(
            "https://example.com/location",
            output.location.getParameter<AltRep>(Parameter.ALTREP).getOrNull()?.value
        )
    }

    @Test
    fun `ALTREP without LOCATION is ignored`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.LOCATION_ALTREP to "https://example.com/location"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.location)
    }
}
