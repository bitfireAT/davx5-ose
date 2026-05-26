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
class SequenceBuilderTest {

    private val builder = SequenceBuilder()

    @Test
    fun `No SEQUENCE defaults to 0`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.SYNC_VERSION to 0
        ), result.entityValues)
    }

    @Test
    fun `SEQUENCE is 0`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also { it.sequence = 0 },
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.SYNC_VERSION to 0
        ), result.entityValues)
    }

    @Test
    fun `SEQUENCE is 1`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also { it.sequence = 1 },
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.SYNC_VERSION to 1
        ), result.entityValues)
    }

}
