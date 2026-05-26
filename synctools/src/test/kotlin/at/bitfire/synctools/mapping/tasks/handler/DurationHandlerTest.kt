/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.property.Duration
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DurationHandlerTest {

    private val handler = DurationHandler()

    @Test
    fun `No DURATION leaves duration null`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.duration)
    }

    @Test
    fun `DURATION PT1H is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.DURATION to "PT1H"), task)
        assertEquals(Duration(AndroidTimeUtils.parseDuration("PT1H")), task.duration)
    }

    @Test
    fun `DURATION P1D is mapped correctly`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.DURATION to "P1D"), task)
        assertEquals(Duration(AndroidTimeUtils.parseDuration("P1D")), task.duration)
    }

}
