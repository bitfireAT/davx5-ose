/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PercentCompleteBuilderTest {

    private val builder = PercentCompleteBuilder()

    @Test
    fun `No PERCENT-COMPLETE`() {
        val output = Entity(ContentValues())
        val task = VToDo()

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.PERCENT))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.PERCENT))
    }

    @Test
    fun `PERCENT-COMPLETE is 50`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += PercentComplete(50)
        }

        builder.build(from = task, main = task, to = output)

        assertEquals(50, output.entityValues.get(JtxContract.JtxICalObject.PERCENT))
    }

    @Test
    fun `PERCENT-COMPLETE is 100`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += PercentComplete(100)
        }

        builder.build(from = task, main = task, to = output)

        assertEquals(100, output.entityValues.get(JtxContract.JtxICalObject.PERCENT))
    }
}
