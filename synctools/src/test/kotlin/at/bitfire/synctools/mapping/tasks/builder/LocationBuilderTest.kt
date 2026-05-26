/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationBuilderTest {

    private val builder = LocationBuilder()

    @Test
    fun `No LOCATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.LOCATION))
        assertNull(result.entityValues.get(Tasks.LOCATION))
    }

    @Test
    fun `LOCATION is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(location = ""),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.LOCATION))
        assertNull(result.entityValues.get(Tasks.LOCATION))
    }

    @Test
    fun `LOCATION is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(location = "Task Location"),
            to = result
        )
        assertEquals("Task Location", result.entityValues.getAsString(Tasks.LOCATION))
    }

}
