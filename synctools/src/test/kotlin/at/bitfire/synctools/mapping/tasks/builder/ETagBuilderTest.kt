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
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.COLUMN_ETAG
import at.bitfire.synctools.test.assertContentValuesEqual
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ETagBuilderTest {

    @Test
    fun `ETag is set`() {
        val result = Entity(ContentValues())
        ETagBuilder(eTag = "some-etag").build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            COLUMN_ETAG to "some-etag"
        ), result.entityValues)
    }

    @Test
    fun `ETag is null`() {
        val result = Entity(ContentValues())
        ETagBuilder(eTag = null).build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            COLUMN_ETAG to null
        ), result.entityValues)
    }

}
