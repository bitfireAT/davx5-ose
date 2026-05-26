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
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UidBuilderTest {

    private val builder = UidBuilder()

    @Test
    fun `No UID`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.UID_2445 to null
        ), result.entityValues)
    }

    @Test
    fun `UID is set`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(Uid("some-uid")))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.UID_2445 to "some-uid"
        ), result.entityValues)
    }

}