/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Description
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DescriptionHandlerTest {

    private val handler = DescriptionHandler()


    @Test
    fun `No DESCRIPTION`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.description)
    }

    @Test
    fun `DESCRIPTION set`() {
        val input = Entity(contentValuesOf(Tasks.DESCRIPTION to "Task details"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Description("Task details"), task.description)
    }
}
