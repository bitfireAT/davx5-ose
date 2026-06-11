/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.propertyListOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.property.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class LocationBuilderTest {

    private val builder = LocationBuilder()

    @Test
    fun `No LOCATION`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.LOCATION))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.LOCATION))
        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.LOCATION_ALTREP))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.LOCATION_ALTREP))
    }

    @Test
    fun `LOCATION has value`() {
        val task = VToDo(propertyListOf(Location("Office")))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals("Office", output.entityValues.get(JtxContract.JtxICalObject.LOCATION))
        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.LOCATION_ALTREP))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.LOCATION_ALTREP))
    }

    @Test
    fun `LOCATION with ALTREP`() {
        val task = VToDo(
            propertyListOf(
                Location(
                    ParameterList(listOf(AltRep(URI.create("https://example.com/location")))),
                    "Office"
                )
            )
        )
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals("Office", output.entityValues.get(JtxContract.JtxICalObject.LOCATION))
        assertEquals(
            "https://example.com/location",
            output.entityValues.get(JtxContract.JtxICalObject.LOCATION_ALTREP)
        )
    }
}
