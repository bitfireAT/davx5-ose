/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarIdBuilderTest {

    private val builder = CalendarIdBuilder(1753)

    @Test
    fun `CALENDAR_ID set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.CALENDAR_ID to 1753
        ), result.entityValues)
    }

}