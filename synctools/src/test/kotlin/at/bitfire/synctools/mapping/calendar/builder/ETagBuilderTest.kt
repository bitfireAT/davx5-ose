/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ETagBuilderTest {

    private val builder = ETagBuilder(eTag = "ETag", scheduleTag = "ScheduleTag")

    @Test
    fun `Main event sets ETag`() {
        val result = Entity(ContentValues())
        val event = VEvent()
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            EventsContract.COLUMN_ETAG to "ETag",
            EventsContract.COLUMN_SCHEDULE_TAG to "ScheduleTag"
        ), result.entityValues)
    }

    @Test
    fun `Exception doesn't set ETag`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            EventsContract.COLUMN_ETAG to null,
            EventsContract.COLUMN_SCHEDULE_TAG to null
        ), result.entityValues)
    }

}