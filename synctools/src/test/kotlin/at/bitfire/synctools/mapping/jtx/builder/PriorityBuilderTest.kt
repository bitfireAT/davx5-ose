/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PriorityBuilderTest {

    private val builder = PriorityBuilder()

    @Test
    fun `No PRIORITY`() {
        val output = Entity(ContentValues())
        val task = VToDo()

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.PRIORITY))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.PRIORITY))
    }

    @Test
    fun `PRIORITY is 5`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Priority(5)
        }

        builder.build(from = task, main = task, to = output)

        assertEquals(5, output.entityValues.get(JtxContract.JtxICalObject.PRIORITY))
    }

    @Test
    fun `PRIORITY is 0`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Priority(0)
        }

        builder.build(from = task, main = task, to = output)

        assertEquals(0, output.entityValues.get(JtxContract.JtxICalObject.PRIORITY))
    }
}
