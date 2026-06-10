/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Clazz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassificationBuilderTest {

    private val builder = ClassificationBuilder()

    @Test
    fun `No CLASS`() {
        val output = Entity(ContentValues())
        val task = VToDo()

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.CLASSIFICATION))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.CLASSIFICATION))
    }

    @Test
    fun `CLASS has text`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Clazz("TOP-SECRET")
        }

        builder.build(from = task, main = task, to = output)

        assertEquals("TOP-SECRET", output.entityValues.get(JtxContract.JtxICalObject.CLASSIFICATION))
    }
}
