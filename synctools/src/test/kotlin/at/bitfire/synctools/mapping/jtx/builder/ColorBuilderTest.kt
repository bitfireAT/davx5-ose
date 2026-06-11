/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.icalendar.propertyListOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorBuilderTest {

    private val builder = ColorBuilder()

    @Test
    fun `No COLOR`() {
        val task = VToDo()
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.COLOR))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.COLOR))
    }

    @Test
    fun `COLOR is set - css name`() {
        val task = VToDo(propertyListOf(Color(null, Css3Color.nearestMatch(0xFF112233.toInt()).name)))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals(Css3Color.nearestMatch(0xFF112233.toInt()).argb, output.entityValues.get(JtxContract.JtxICalObject.COLOR))
    }

    @Test
    fun `COLOR is set - hex`() {
        val task = VToDo(propertyListOf(Color(null, "#FF112233")))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals(0xFF112233.toInt(), output.entityValues.get(JtxContract.JtxICalObject.COLOR))
    }
}
