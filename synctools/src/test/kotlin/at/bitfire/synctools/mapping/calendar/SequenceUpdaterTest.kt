/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceUpdaterTest {

    private val sequenceUpdater = SequenceUpdater()

    @Test
    fun `SEQUENCE is increased for new events`() {
        // In case of newly created events, it doesn't matter whether they're group-scheduled or not.
        val main = Entity(
            ContentValues(
                /* SEQUENCE column has never been set yet and thus is null */
            )
        )
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column remains null for mapping ...
        assertNull(main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but SEQUENCE shall be set to 0 after upload
        assertEquals(0, result)
    }

    @Test
    fun `SEQUENCE is increased for group-scheduled events when we're ORGANIZER`() {
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
        assertEquals(2, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        assertEquals(2, result)
    }

    @Test
    fun `SEQUENCE is not increased for group-scheduled events when we're not ORGANIZER`() {
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
        assertEquals(1, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but don't increase after upload.
        assertNull(result)
    }

    @Test
    fun `SEQUENCE is not increased for non-group-scheduled events`() {
        val main = Entity(
            contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to 1
            )
        )
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column is not touched and remains 1 (will be mapped to no SEQUENCE property) ...
        assertEquals(1, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... and is not increased after upload.
        assertNull(result)
    }

}