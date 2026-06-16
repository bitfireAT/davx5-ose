/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class PriorityHandlerTest {

    private val handler = PriorityHandler()

    @Test
    fun `No PRIORITY`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Priority>(Property.PRIORITY).getOrNull())
    }

    @Test
    fun `PRIORITY with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PRIORITY to 1))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Priority(1),
            output.getProperty<Priority>(Property.PRIORITY).getOrNull()
        )
    }

    @Test
    fun `PRIORITY with invalid value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PRIORITY to "a"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Priority>(Property.PRIORITY).getOrNull())
    }

    @Test
    fun `PRIORITY with VALUE_UNDEFINED`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PRIORITY to Priority.VALUE_UNDEFINED))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Priority>(Property.PRIORITY).getOrNull())
    }

    @Test
    fun `PRIORITY is never added to VJOURNAL`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PRIORITY to 1))
        val output = VJournal()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Priority>(Property.PRIORITY).getOrNull())
    }
}
