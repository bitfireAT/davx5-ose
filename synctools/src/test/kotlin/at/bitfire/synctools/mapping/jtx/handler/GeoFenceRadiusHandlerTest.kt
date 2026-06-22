/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.jtx.JtxProperty
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class GeoFenceRadiusHandlerTest {

    private val handler = GeoFenceRadiusHandler()

    @Test
    fun `No GEOFENCE_RADIUS`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<XProperty>(JtxProperty.X_GEOFENCE_RADIUS).getOrNull())
    }

    @Test
    fun `GEOFENCE_RADIUS is added as XProperty`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.GEOFENCE_RADIUS to 500))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("500", output.getProperty<XProperty>(JtxProperty.X_GEOFENCE_RADIUS).getOrNull()?.value)
    }

    @Test
    fun `Non-numeric radius is ignored`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.GEOFENCE_RADIUS to "abc"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<XProperty>(JtxProperty.X_GEOFENCE_RADIUS).getOrNull())
    }
}
