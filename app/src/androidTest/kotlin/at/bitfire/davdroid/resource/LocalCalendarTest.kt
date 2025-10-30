/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.storage.calendar.EventsContract
import at.bitfire.synctools.test.InitCalendarProviderRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import javax.inject.Inject

@HiltAndroidTest
class LocalCalendarTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val initCalendarProviderRule: TestRule = InitCalendarProviderRule.initialize()

    @Inject
    lateinit var localCalendarFactory: LocalCalendar.Factory

    private val account = Account("LocalCalendarTest", ACCOUNT_TYPE_LOCAL)
    private lateinit var androidCalendar: AndroidCalendar
    private lateinit var client: ContentProviderClient
    private lateinit var calendar: LocalCalendar

    @Before
    fun setUp() {
        hiltRule.inject()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        val provider = AndroidCalendarProvider(account, client)
        androidCalendar = provider.createAndGetCalendar(ContentValues())
        calendar = localCalendarFactory.create(androidCalendar)
    }

    @After
    fun tearDown() {
        androidCalendar.delete()
        client.closeCompat()
    }


    @Test
    fun testDeleteDirtyEventsWithoutInstances_NoInstances() {
        // create recurring event with only deleted/cancelled instances
        val now = System.currentTimeMillis()
        val id = calendar.add(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events._SYNC_ID to "event-without-instances",
                Events.CALENDAR_ID to calendar.androidCalendar.id,
                Events.ALL_DAY to 0,
                Events.DTSTART to now,
                Events.RRULE to "FREQ=DAILY;COUNT=3",
                Events.DIRTY to 1
            )),
            exceptions = listOf(
                Entity(contentValuesOf(     // first instance: cancelled
                    Events.CALENDAR_ID to calendar.androidCalendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now + 86400000,
                    Events.STATUS to Events.STATUS_CANCELED
                )),
                Entity(contentValuesOf(     // second instance: cancelled
                    Events.CALENDAR_ID to calendar.androidCalendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now + 86400000,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now + 86400000,
                    Events.STATUS to Events.STATUS_CANCELED
                )),
                Entity(contentValuesOf(     // third and last instance: cancelled
                    Events.CALENDAR_ID to calendar.androidCalendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now + 2*86400000,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now + 2*86400000,
                    Events.STATUS to Events.STATUS_CANCELED
                ))
            )
        ))

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is now marked as deleted
        val result = calendar.androidCalendar.getEventRow(id)!!
        assertEquals(1, result.getAsInteger(Events.DELETED))
    }

    @Test
    fun testDeleteDirtyEventsWithoutInstances_OneInstanceRemaining() {
        // create recurring event with only deleted/cancelled instances
        val now = System.currentTimeMillis()
        val id = calendar.add(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events._SYNC_ID to "event-with-instances",
                Events.CALENDAR_ID to calendar.androidCalendar.id,
                Events.ALL_DAY to 0,
                Events.DTSTART to now,
                Events.RRULE to "FREQ=DAILY;COUNT=2",
                Events.DIRTY to 1
            )),
            exceptions = listOf(
                Entity(contentValuesOf(     // first instance: cancelled
                    Events.CALENDAR_ID to calendar.androidCalendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now + 86400000,
                    Events.STATUS to Events.STATUS_CANCELED
                ))
                // however second instance is NOT cancelled
            )
        ))

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is still marked as dirty, but not as deleted
        val result = calendar.androidCalendar.getEventRow(id)!!
        assertEquals(1, result.getAsInteger(Events.DIRTY))
        assertEquals(0, result.getAsInteger(Events.DELETED))
    }

    /**
     * Verifies that [LocalCalendar.removeNotDirtyMarked] works as expected.
     * @param contentValues values to set on the event. Required:
     * - [Events._ID]
     * - [Events.DIRTY]
     */
    private fun testRemoveNotDirtyMarked(contentValues: ContentValues) {
        val entity = Entity(
            contentValuesOf(
                Events.CALENDAR_ID to androidCalendar.id,
                Events.DTSTART to System.currentTimeMillis(),
                Events.DTEND to System.currentTimeMillis(),
                Events.TITLE to "Some Event",
                EventsContract.COLUMN_FLAGS to 123
            ).apply { putAll(contentValues) }
        )
        val id = androidCalendar.addEvent(entity)

        calendar.removeNotDirtyMarked(123)

        assertNull(androidCalendar.getEvent(id))
    }

    @Test
    fun testRemoveNotDirtyMarked_IdLargerThanIntMaxValue() = testRemoveNotDirtyMarked(
        contentValuesOf(Events._ID to Int.MAX_VALUE.toLong() + 10, Events.DIRTY to 0)
    )

    @Test
    fun testRemoveNotDirtyMarked_DirtyIs0() = testRemoveNotDirtyMarked(
        contentValuesOf(Events._ID to 1, Events.DIRTY to 0)
    )

    @Test
    fun testRemoveNotDirtyMarked_DirtyNull() = testRemoveNotDirtyMarked(
        contentValuesOf(Events._ID to 1, Events.DIRTY to null)
    )

    /**
     * Verifies that [LocalCalendar.markNotDirty] works as expected.
     * @param contentValues values to set on the event. Required:
     * - [Events.DIRTY]
     */
    private fun testMarkNotDirty(contentValues: ContentValues) {
        val id = androidCalendar.addEvent(Entity(
            contentValuesOf(
                Events.CALENDAR_ID to androidCalendar.id,
                Events._ID to 1,
                Events.DTSTART to System.currentTimeMillis(),
                Events.DTEND to System.currentTimeMillis(),
                Events.TITLE to "Some Event",
                EventsContract.COLUMN_FLAGS to 123
            ).apply { putAll(contentValues) }
        ))

        val updated = calendar.markNotDirty(321)
        assertEquals(1, updated)
        assertEquals(321, androidCalendar.getEvent(id)?.entityValues?.getAsInteger(EventsContract.COLUMN_FLAGS))
    }

    @Test
    fun test_markNotDirty_DirtyIs0() = testMarkNotDirty(
        contentValuesOf(
            Events.DIRTY to 0
        )
    )

    @Test
    fun test_markNotDirty_DirtyIsNull() = testMarkNotDirty(
        contentValuesOf(
            Events.DIRTY to null
        )
    )

}