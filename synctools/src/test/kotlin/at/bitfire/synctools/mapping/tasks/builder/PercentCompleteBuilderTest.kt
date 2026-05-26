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
class PercentCompleteBuilderTest {

    private val builder = PercentCompleteBuilder()

    @Test
    fun `No PERCENT-COMPLETE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PERCENT_COMPLETE to null
        ), result.entityValues)
    }

    @Test
    fun `PERCENT-COMPLETE is 50`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(percentComplete = 50),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PERCENT_COMPLETE to 50
        ), result.entityValues)
    }

    @Test
    fun `PERCENT-COMPLETE is 100`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(percentComplete = 100),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PERCENT_COMPLETE to 100
        ), result.entityValues)
    }

}
