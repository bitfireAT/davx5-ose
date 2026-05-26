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
class LocationHandlerTest {

    private val handler = LocationHandler()

    @Test
    fun `No LOCATION`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.location)
    }

    @Test
    fun `LOCATION set`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.LOCATION to "Vienna, Austria"), task)
        assertEquals("Vienna, Austria", task.location)
    }

}
