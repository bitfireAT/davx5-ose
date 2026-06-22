/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.jtx.JtxProperty
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class GeoHandlerTest {

    private val handler = GeoHandler()

    @Test
    fun `No GEO`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Geo>(Property.GEO).getOrNull())
    }

    @Test
    fun `GEO latitude and longitude set`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.GEO_LAT to 48.2,
                JtxContract.JtxICalObject.GEO_LONG to 16.3
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Geo(48.2.toBigDecimal(), 16.3.toBigDecimal()), output.getRequiredProperty<Geo>(Property.GEO))
    }

    @Test
    fun `GEO without latitude is ignored`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.GEO_LONG to 16.3))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Geo>(Property.GEO).getOrNull())
    }

    @Test
    fun `GEO without longitude is ignored`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.GEO_LAT to 48.2))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Geo>(Property.GEO).getOrNull())
    }

    @Test
    fun `X-GEOFENCE-RADIUS set`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.GEOFENCE_RADIUS to 500))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            "500",
            output.getProperty<XProperty>(JtxProperty.X_GEOFENCE_RADIUS).getOrNull()?.value
        )
    }

    @Test
    fun `GEO and X-GEOFENCE-RADIUS set`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.GEO_LAT to 48.2,
                JtxContract.JtxICalObject.GEO_LONG to 16.3,
                JtxContract.JtxICalObject.GEOFENCE_RADIUS to 500
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Geo(48.2.toBigDecimal(), 16.3.toBigDecimal()), output.getRequiredProperty<Geo>(Property.GEO))
        assertEquals(
            "500",
            output.getProperty<XProperty>(JtxProperty.X_GEOFENCE_RADIUS).getOrNull()?.value
        )
    }
}
