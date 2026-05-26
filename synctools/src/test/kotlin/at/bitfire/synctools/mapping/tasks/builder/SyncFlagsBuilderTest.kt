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
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.COLUMN_FLAGS
import at.bitfire.synctools.test.assertContentValuesEqual
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncFlagsBuilderTest {

    @Test
    fun `Flags set to 123`() {
        val result = Entity(ContentValues())
        SyncFlagsBuilder(123).build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            COLUMN_FLAGS to 123
        ), result.entityValues)
    }

}
