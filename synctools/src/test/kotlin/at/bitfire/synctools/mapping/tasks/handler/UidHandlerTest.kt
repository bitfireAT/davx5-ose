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
class UidHandlerTest {

    private val handler = UidHandler()

    @Test
    fun `No UID`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.uid)
    }

    @Test
    fun `UID set`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks._UID to "test-uid-123"), task)
        assertEquals("test-uid-123", task.uid)
    }

}
