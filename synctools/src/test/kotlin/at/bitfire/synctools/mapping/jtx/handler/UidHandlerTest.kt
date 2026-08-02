/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class UidHandlerTest {

    private val handler = UidHandler()

    @Test
    fun `No UID`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.uid.getOrNull())
    }

    @Test
    fun `UID with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.UID to "test-uid-123"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Uid("test-uid-123"), output.uid.getOrNull())
    }

    @Test
    fun `exception should use UID from main jtx object`() {
        val from = Entity(ContentValues())
        val main = Entity(contentValuesOf(JtxContract.JtxICalObject.UID to "test-uid-123"))
        val output = VToDo()

        handler.process(from = from, main = main, to = output)

        assertEquals(Uid("test-uid-123"), output.uid.getOrNull())
    }
}
