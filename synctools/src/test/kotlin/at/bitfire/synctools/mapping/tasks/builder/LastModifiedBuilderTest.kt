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
class LastModifiedBuilderTest {

    private val builder = LastModifiedBuilder()

    @Test
    fun `No LAST_MODIFIED stores null`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.LAST_MODIFIED to null
        ), result.entityValues)
    }

    @Test
    fun `LAST_MODIFIED is stored`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(lastModified = 1593771404L),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.LAST_MODIFIED to 1593771404L
        ), result.entityValues)
    }

}
