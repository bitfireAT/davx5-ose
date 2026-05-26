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
class PercentCompleteHandlerTest {

    private val handler = PercentCompleteHandler()

    @Test
    fun `No PERCENT_COMPLETE leaves percentComplete null`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.percentComplete)
    }

    @Test
    fun `PERCENT_COMPLETE 0 is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PERCENT_COMPLETE to 0), task)
        assertEquals(0, task.percentComplete)
    }

    @Test
    fun `PERCENT_COMPLETE 100 is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.PERCENT_COMPLETE to 100), task)
        assertEquals(100, task.percentComplete)
    }

}
