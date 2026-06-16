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
import net.fortuna.ical4j.model.property.PercentComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class PercentCompleteHandlerTest {

    private val handler = PercentCompleteHandler()

    @Test
    fun `No PERCENT-COMPLETE`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<PercentComplete>(Property.PERCENT_COMPLETE).getOrNull())
    }

    @Test
    fun `Zero PERCENT-COMPLETE`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PERCENT to 0))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<PercentComplete>(Property.PERCENT_COMPLETE).getOrNull())
    }

    @Test
    fun `PERCENT-COMPLETE with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PERCENT to 42))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            PercentComplete(42),
            output.getProperty<PercentComplete>(Property.PERCENT_COMPLETE).getOrNull()
        )
    }

    @Test
    fun `PERCENT-COMPLETE with invalid value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PERCENT to "a"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<PercentComplete>(Property.PERCENT_COMPLETE).getOrNull())
    }

    @Test
    fun `PERCENT-COMPLETE is never added to VJOURNAL`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.PERCENT to 42))
        val output = VJournal()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<PercentComplete>(Property.PERCENT_COMPLETE).getOrNull())
    }
}
