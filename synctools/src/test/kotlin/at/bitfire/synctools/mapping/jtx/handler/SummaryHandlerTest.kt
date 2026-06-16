/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SummaryHandlerTest {

    private val handler = SummaryHandler()

    @Test
    fun `No SUMMARY`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.summary)
    }

    @Test
    fun `SUMMARY with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.SUMMARY to "My Task"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Summary("My Task"), output.summary)
    }
}
