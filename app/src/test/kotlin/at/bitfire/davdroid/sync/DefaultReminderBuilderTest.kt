/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.test.assertEventAndExceptionsEqual
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)          // TODO
class DefaultReminderBuilderTest {

    val builder = DefaultReminderBuilder(minBefore = 15)

    @Test
    fun `add() adds to main event and exceptions`() {
        val event = EventAndExceptions(
            main = Entity(ContentValues()),
            exceptions = listOf(
                Entity(ContentValues())
            )
        )
        builder.add(to = event)
        assertEventAndExceptionsEqual(
            EventAndExceptions(
                main = Entity(contentValuesOf(
                    Reminders.MINUTES to 15,
                    Reminders.METHOD to Reminders.METHOD_ALERT
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Reminders.MINUTES to 15,
                        Reminders.METHOD to Reminders.METHOD_ALERT
                    ))
                )
            ),
            event
        )
    }

    @Test
    fun `addToEvent() doesn't add to all-day event`() {
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1
        ))
        builder.addToEvent(entity)
        assertFalse(entity.subValues.any { it.uri == Reminders.CONTENT_URI })
    }

}