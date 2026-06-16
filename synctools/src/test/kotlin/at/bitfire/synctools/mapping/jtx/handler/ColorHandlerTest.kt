/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.Css3Color
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class ColorHandlerTest {

    private val handler = ColorHandler()

    @Test
    fun `No COLOR`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Color>(Color.PROPERTY_NAME).getOrNull())
    }

    @Test
    fun `COLOR is converted to nearest CSS3 color name`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COLOR to Css3Color.black.argb))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("black", output.getProperty<Color>(Color.PROPERTY_NAME).getOrNull()?.value)
    }

    @Test
    fun `Invalid COLOR is ignored`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COLOR to "invalid"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Color>(Color.PROPERTY_NAME).getOrNull())
    }
}
