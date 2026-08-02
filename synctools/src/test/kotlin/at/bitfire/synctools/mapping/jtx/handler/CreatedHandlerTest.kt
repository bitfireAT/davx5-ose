/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Created
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CreatedHandlerTest {

    private val handler = CreatedHandler()

    @Test
    fun `No CREATED`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.created)
    }

    @Test
    fun `CREATED stores as UTC instant`() {
        val epochMillis = 1779105600000L  // 2026-05-18T12:00:00Z
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.CREATED to epochMillis))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Created(Instant.ofEpochMilli(epochMillis)), output.created)
    }
}
