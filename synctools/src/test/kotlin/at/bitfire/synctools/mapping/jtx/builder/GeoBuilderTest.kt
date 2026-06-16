/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.synctools.icalendar.propertyListOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeoBuilderTest {

    private val builder = GeoBuilder()

    @Test
    fun `No GEO`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.GEO_LAT))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.GEO_LAT))
        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.GEO_LONG))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.GEO_LONG))
    }

    @Test
    fun `GEO stores latitude and longitude as Double`() {
        val task = VToDo(propertyListOf(Geo(48.2.toBigDecimal(), 16.3.toBigDecimal())))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals(48.2, output.entityValues.getAsDouble(JtxContract.JtxICalObject.GEO_LAT), 0.0)
        assertEquals(16.3, output.entityValues.getAsDouble(JtxContract.JtxICalObject.GEO_LONG), 0.0)
    }

    @Test
    fun `No X-GEOFENCE-RADIUS`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
    }

    @Test
    fun `X-GEOFENCE-RADIUS valid value`() {
        val task = VToDo(propertyListOf(XProperty(JtxICalObject.X_PROP_GEOFENCE_RADIUS, "500")))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals(500, output.entityValues.getAsInteger(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
    }

    @Test
    fun `X-GEOFENCE-RADIUS invalid value`() {
        val task = VToDo(propertyListOf(XProperty(JtxICalObject.X_PROP_GEOFENCE_RADIUS, "abc")))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
    }
}
