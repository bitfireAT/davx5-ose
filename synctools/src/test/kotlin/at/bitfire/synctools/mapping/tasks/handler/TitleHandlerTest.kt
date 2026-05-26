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
class TitleHandlerTest {

    private val handler = TitleHandler()

    @Test
    fun `No title`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.summary)
    }

    @Test
    fun `Title set`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.TITLE to "Test Task"), task)
        assertEquals("Test Task", task.summary)
    }

}
