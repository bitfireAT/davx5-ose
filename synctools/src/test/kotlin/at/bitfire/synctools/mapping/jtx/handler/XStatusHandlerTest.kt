/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class XStatusHandlerTest {

    private val handler = XStatusHandler()

    @Test
    fun `No EXTENDED_STATUS`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.getProperty<XProperty>(JtxContract.JtxICalObject.EXTENDED_STATUS).getOrNull())
    }

    @Test
    fun `EXTENDED_STATUS with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.EXTENDED_STATUS to "IN-PROCESS"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            "IN-PROCESS",
            output.getProperty<XProperty>(JtxContract.JtxICalObject.EXTENDED_STATUS).getOrNull()?.value
        )
    }
}
