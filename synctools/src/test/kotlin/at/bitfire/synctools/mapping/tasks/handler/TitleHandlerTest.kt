/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Summary
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleHandlerTest {

    private val handler = TitleHandler()

    @Test
    fun `legacy No title`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.summary)
    }

    @Test
    fun `legacy Title set`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.TITLE to "Test Task"), task)
        assertEquals("Test Task", task.summary)
    }

    @Test
    fun `No title`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.summary)
    }

    @Test
    fun `Title set`() {
        val input = Entity(contentValuesOf(Tasks.TITLE to "Test Task"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Summary("Test Task"), task.summary)
    }

}
