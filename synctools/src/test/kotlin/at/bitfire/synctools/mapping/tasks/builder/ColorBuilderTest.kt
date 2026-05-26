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
class ColorBuilderTest {

    private val builder = ColorBuilder()

    @Test
    fun `No COLOR`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.TASK_COLOR to null
        ), result.entityValues)
    }

    @Test
    fun `COLOR is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(color = 0xFF112233.toInt()),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.TASK_COLOR to 0xFF112233.toInt()
        ), result.entityValues)
    }

}
