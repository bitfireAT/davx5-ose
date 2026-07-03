/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.LastModified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class LastModifiedHandlerTest {

    private val handler = LastModifiedHandler()

    @Test
    fun `No LAST_MODIFIED`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.lastModified)
    }

    @Test
    fun `LAST_MODIFIED stores as UTC instant`() {
        val epochMillis = 1779105600000L  // 2026-05-18T12:00:00Z
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.LAST_MODIFIED to epochMillis))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(LastModified(Instant.ofEpochMilli(epochMillis)), output.lastModified)
    }
}
