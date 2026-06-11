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
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeoFenceRadiusBuilderTest {

    private val builder = GeoFenceRadiusBuilder()

    @Test
    fun `No X-GEOFENCE-RADIUS`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
    }

    @Test
    fun `X-GEOFENCE-RADIUS stores value as String`() {
        val task = VToDo(propertyListOf(XProperty(JtxICalObject.X_PROP_GEOFENCE_RADIUS, "500")))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals("500", output.entityValues.getAsString(JtxContract.JtxICalObject.GEOFENCE_RADIUS))
    }
}
