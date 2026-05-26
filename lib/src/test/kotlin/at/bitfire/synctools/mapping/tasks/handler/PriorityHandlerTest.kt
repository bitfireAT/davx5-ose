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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PriorityHandlerTest {

    private val handler = PriorityHandler()

    @Test
    fun `No PRIORITY leaves priority at default (0)`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertEquals(0, task.priority)
    }

    @Test
    fun `PRIORITY is 0 (undefined)`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PRIORITY to 0), task)
        assertEquals(0, task.priority)
    }

    @Test
    fun `PRIORITY is 1 (high)`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PRIORITY to 1), task)
        assertEquals(1, task.priority)
    }

    @Test
    fun `PRIORITY is 5 (medium)`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PRIORITY to 5), task)
        assertEquals(5, task.priority)
    }

    @Test
    fun `PRIORITY is 9 (low)`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PRIORITY to 9), task)
        assertEquals(9, task.priority)
    }

}
