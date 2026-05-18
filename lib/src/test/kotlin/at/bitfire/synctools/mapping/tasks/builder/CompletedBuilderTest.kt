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
import net.fortuna.ical4j.model.property.Completed
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CompletedBuilderTest {

    private val builder = CompletedBuilder()

    @Test
    fun `No COMPLETED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.COMPLETED to null,
            Tasks.COMPLETED_IS_ALLDAY to 0
        ), result.entityValues)
    }

    @Test
    fun `COMPLETED is set`() {
        val instant = Instant.ofEpochMilli(1_000_000L)
        val result = Entity(ContentValues())
        builder.build(
            from = Task(completedAt = Completed(instant)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.COMPLETED to 1_000_000L,
            Tasks.COMPLETED_IS_ALLDAY to 0
        ), result.entityValues)
    }

}
