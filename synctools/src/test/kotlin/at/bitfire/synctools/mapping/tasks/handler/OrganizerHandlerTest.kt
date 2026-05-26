/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Organizer
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerHandlerTest {

    private val handler = OrganizerHandler()

    @Test
    fun `No ORGANIZER`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.organizer)
    }

    @Test
    fun `ORGANIZER is email address`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.ORGANIZER to "organizer@example.com"), task)
        assertEquals(Organizer("mailto:organizer@example.com"), task.organizer)
    }

}
