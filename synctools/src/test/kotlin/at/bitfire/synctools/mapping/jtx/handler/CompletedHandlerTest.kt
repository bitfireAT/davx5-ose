/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Completed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class CompletedHandlerTest {

    private val handler = CompletedHandler()

    @Test
    fun `No COMPLETED`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Completed>(Property.COMPLETED).getOrNull())
    }

    @Test
    fun `COMPLETED with value`() {
        val completed = 1_700_000_000_000L
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COMPLETED to completed))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Completed(Instant.ofEpochMilli(completed)),
            output.getProperty<Completed>(Property.COMPLETED).getOrNull()
        )
    }

    @Test
    fun `COMPLETED with invalid value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.COMPLETED to "a"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<Completed>(Property.COMPLETED).getOrNull())
    }
}
