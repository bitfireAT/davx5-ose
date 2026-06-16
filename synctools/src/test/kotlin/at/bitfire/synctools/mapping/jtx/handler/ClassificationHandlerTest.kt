/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Clazz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassificationHandlerTest {

    private val handler = ClassificationHandler()

    @Test
    fun `No CLASSIFICATION`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.classification)
    }

    @Test
    fun `CLASSIFICATION with value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.CLASSIFICATION to "PUBLIC"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Clazz("PUBLIC"), output.classification)
    }
}
