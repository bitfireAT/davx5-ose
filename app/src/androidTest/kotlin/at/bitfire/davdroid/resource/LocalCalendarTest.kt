/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.test.InitCalendarProviderRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var client: ContentProviderClient
    private lateinit var calendar: LocalCalendar

    @Before
    fun setUp() {
        hiltRule.inject()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        val provider = AndroidCalendarProvider(account, client)
        calendar = localCalendarFactory.create(provider.createAndGetCalendar(ContentValues()))
    }

    @After
    fun tearDown() {
        calendar.androidCalendar.delete()
        client.closeCompat()
    }


    @Test
    fun testDeleteDirtyEventsWithoutInstances_NoInstances_CancelledExceptions() {
        // create recurring event with only deleted/cancelled instances
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 3 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=3"))
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220120T010203Z")
                dtStart = DtStart("20220120T010203Z")
                summary = "Cancelled exception on 1st day"
                status = Status.VEVENT_CANCELLED
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220121T010203Z")
                dtStart = DtStart("20220121T010203Z")
                summary = "Cancelled exception on 2nd day"
                status = Status.VEVENT_CANCELLED
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220122T010203Z")
                dtStart = DtStart("20220122T010203Z")
                summary = "Cancelled exception on 3rd day"
                status = Status.VEVENT_CANCELLED
            })
        }
        val localEvent = AndroidEvent(calendar.androidCalendar, event, "filename.ics", null, null, LocalResource.FLAG_REMOTELY_PRESENT)
        localEvent.add()
        val eventId = localEvent.id!!

        // set event as dirty
        client.update(ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId), ContentValues(1).apply {
            put(Events.DIRTY, 1)
        }, null, null)

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is now marked as deleted
        client.query(
            ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId),
            arrayOf(Events.DELETED), null, null, null
        )!!.use { cursor ->
            cursor.moveToNext()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    // Needs InitCalendarProviderRule
    fun testDeleteDirtyEventsWithoutInstances_Recurring_Instances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 3 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=3"))
        }
        val localEvent = AndroidEvent(calendar.androidCalendar, event, "filename.ics", null, null, LocalResource.FLAG_REMOTELY_PRESENT)
        localEvent.add()
        val eventId = localEvent.id!!

        // set event as dirty
        client.update(ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId), ContentValues(1).apply {
            put(Events.DIRTY, 1)
        }, null, null)

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is not marked as deleted
        client.query(
            ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId),
            arrayOf(Events.DELETED), null, null, null
        )!!.use { cursor ->
            cursor.moveToNext()
            assertEquals(0, cursor.getInt(0))
        }
    }

}