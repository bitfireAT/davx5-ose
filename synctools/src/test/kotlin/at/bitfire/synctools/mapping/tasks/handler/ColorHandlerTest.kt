/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
class ColorHandlerTest {

    private val handler = ColorHandler()


    @Test
    fun `No COLOR`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Optional.empty<Color>(), task.getProperty<Color>(Color.PROPERTY_NAME))
    }

    @Test
    fun `COLOR set`() {
        val input = Entity(contentValuesOf(Tasks.TASK_COLOR to 0xFF112233.toInt()))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals("midnightblue", task.getRequiredProperty<Color>(Color.PROPERTY_NAME).value)
    }
}
