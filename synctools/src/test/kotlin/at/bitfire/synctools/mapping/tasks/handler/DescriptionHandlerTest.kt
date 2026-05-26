/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
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
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.description)
    }

    @Test
    fun `DESCRIPTION set`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.DESCRIPTION to "Task details"), task)
        assertEquals("Task details", task.description)
    }

}
