/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Sequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceHandlerTest {

    private val handler = SequenceHandler()

    @Test
    fun `No SEQUENCE`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.sequence)
    }

    @Test
    fun `SEQUENCE with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to 3))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Sequence(3), output.sequence)
    }

    @Test
    fun `SEQUENCE with invalid value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to "a"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.sequence)
    }
}
