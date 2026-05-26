/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceUpdaterTest {

    private val sequenceUpdater = SequenceUpdater()

    @Test
    fun testIncreaseSequence_NewEvent() {
        // In case of newly created events, it doesn't matter whether they're group-scheduled or not.
        val main = Entity(
            ContentValues(
                /* SEQUENCE column has never been set yet and thus is null */
            )
        )
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column remains null for mapping ...
        Assert.assertNull(main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but SEQUENCE shall be set to 0 after upload
        Assert.assertEquals(0, result)
    }

    @Test
    fun testIncreaseSequence_GroupScheduledEvent_AsOrganizer() {
        val main = Entity(
            contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to 1,
                CalendarContract.Events.IS_ORGANIZER to 1
            )
        ).apply {
            addSubValue(
                CalendarContract.Attendees.CONTENT_URI, contentValuesOf(
                    CalendarContract.Attendees.ATTENDEE_EMAIL to "test@example.com"
                )
            )
        }
        val result = sequenceUpdater.increaseSequence(main)
        Assert.assertEquals(2, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        Assert.assertEquals(2, result)
    }

    @Test
    fun testIncreaseSequence_GroupScheduledEvent_NotAsOrganizer() {
        val main = Entity(
            contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to 1,
                CalendarContract.Events.IS_ORGANIZER to 0
            )
        ).apply {
            addSubValue(
                CalendarContract.Attendees.CONTENT_URI, contentValuesOf(
                    CalendarContract.Attendees.ATTENDEE_EMAIL to "test@example.com"
                )
            )
        }
        val result = sequenceUpdater.increaseSequence(main)
        // SEQUENCE column remains 1 for mapping, ...
        Assert.assertEquals(1, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but don't increase after upload.
        Assert.assertNull(result)
    }

    @Test
    fun testIncreaseSequence_NonGroupScheduledEvent_WithoutSequence() {
        val main = Entity(
            contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to 0
            )
        )
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column remains 0 for mapping (will be mapped to no SEQUENCE property), ...
        Assert.assertEquals(0, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but don't increase after upload.
        Assert.assertNull(result)
    }

    @Test
    fun testIncreaseSequence_NonGroupScheduledEvent_WithSequence() {
        val main = Entity(
            contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to 1
            )
        )
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column is increased to 2 for mapping, ...
        Assert.assertEquals(2, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... and increased after upload.
        Assert.assertEquals(2, result)
    }

}