/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusHandlerTest {

    private val handler = StatusHandler()

    @Test
    fun `No STATUS`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.status)
    }

    @Test
    fun `STATUS with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.STATUS to "NEEDS-ACTION"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Status("NEEDS-ACTION"), output.status)
    }
}
