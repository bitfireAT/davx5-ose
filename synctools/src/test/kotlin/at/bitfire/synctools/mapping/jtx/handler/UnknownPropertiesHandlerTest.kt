/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesHandlerTest {

    private val handler = UnknownPropertiesHandler()

    @Test
    fun `No unknown sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<XProperty>("X-UNKNOWN").getOrNull())
    }

    @Test
    fun `Sub-values with a different URI are ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxCategory.CONTENT_URI,
            contentValuesOf(JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN","ignored"]""")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<XProperty>("X-UNKNOWN").getOrNull())
    }

    @Test
    fun `Single unknown property`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxUnknown.CONTENT_URI,
            contentValuesOf(JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN","value"]""")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val property = output.getProperty<XProperty>("X-UNKNOWN").getOrNull()
        assertEquals("value", property?.value)
    }

    @Test
    fun `Multiple unknown properties`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxUnknown.CONTENT_URI,
            contentValuesOf(JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN-1","one"]""")
        )
        input.addSubValue(
            JtxContract.JtxUnknown.CONTENT_URI,
            contentValuesOf(JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN-2","two"]""")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("one", output.getProperty<XProperty>("X-UNKNOWN-1").getOrNull()?.value)
        assertEquals("two", output.getProperty<XProperty>("X-UNKNOWN-2").getOrNull()?.value)
    }

    @Test
    fun `Unknown property with parameters`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxUnknown.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN","value",{"X-CUSTOM":"custom-value"}]"""
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val property = output.getProperty<XProperty>("X-UNKNOWN").getOrNull()
        assertEquals("value", property?.value)
        assertEquals("custom-value", property?.getParameter<XParameter>("X-CUSTOM")?.getOrNull()?.value)
    }

    @Test
    fun `Unknown property with invalid JSON is ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxUnknown.CONTENT_URI,
            contentValuesOf(JtxContract.JtxUnknown.UNKNOWN_VALUE to "not JSON")
        )
        input.addSubValue(
            JtxContract.JtxUnknown.CONTENT_URI,
            contentValuesOf(JtxContract.JtxUnknown.UNKNOWN_VALUE to """["X-UNKNOWN","value"]""")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("value", output.getProperty<XProperty>("X-UNKNOWN").getOrNull()?.value)
    }
}
