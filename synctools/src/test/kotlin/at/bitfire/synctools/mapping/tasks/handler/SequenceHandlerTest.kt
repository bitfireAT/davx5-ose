/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
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
class SequenceHandlerTest {

    private val handler = SequenceHandler()

    @Test
    fun `No sequence`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.sequence)
    }

    @Test
    fun `Sequence is 0`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.SYNC_VERSION to 0), task)
        assertEquals(0, task.sequence)
    }

    @Test
    fun `Sequence is positive`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.SYNC_VERSION to 3), task)
        assertEquals(3, task.sequence)
    }

}
