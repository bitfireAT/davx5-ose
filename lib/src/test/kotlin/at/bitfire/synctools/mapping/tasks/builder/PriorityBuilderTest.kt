/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.test.assertContentValuesEqual
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PriorityBuilderTest {

    private val builder = PriorityBuilder()

    @Test
    fun `No PRIORITY`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PRIORITY to 0
        ), result.entityValues)
    }

    @Test
    fun `PRIORITY is 5`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(priority = 5),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PRIORITY to 5
        ), result.entityValues)
    }

}
