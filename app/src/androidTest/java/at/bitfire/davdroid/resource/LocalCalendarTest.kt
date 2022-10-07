/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.InitCalendarProviderRule
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.MiscUtils.ContentProviderClientHelper.closeCompat
import at.bitfire.ical4android.util.MiscUtils.UriHelper.asSyncAdapter
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.rules.TestRule

class LocalCalendarTest {

    companion object {
        @JvmField
        @ClassRule
        val initCalendarProviderRule: TestRule = InitCalendarProviderRule.getInstance()

        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun setUpProvider() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            provider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun closeProvider() {
            provider.closeCompat()
        }

    }

    private val account = Account("LocalCalendarTest", ACCOUNT_TYPE_LOCAL)
    private lateinit var calendar: LocalCalendar

    @Before
    fun prepare() {
        val uri = AndroidCalendar.create(account, provider, ContentValues())
        calendar = AndroidCalendar.findByID(account, provider, LocalCalendar.Factory, ContentUris.parseId(uri))
    }

    @After
    fun shutdown() {
        calendar.delete()
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
        val localEvent = LocalEvent(calendar, event, "filename.ics", null, null, LocalResource.FLAG_REMOTELY_PRESENT)
        localEvent.add()
        val eventId = localEvent.id!!

        // set event as dirty
        provider.update(ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId), ContentValues(1).apply {
            put(Events.DIRTY, 1)
        }, null, null)

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is now marked as deleted
        provider.query(
            ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId),
            arrayOf(Events.DELETED), null, null, null
        )!!.use { cursor ->
            cursor.moveToNext()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    // Flaky, Needs single or rec init of CalendarProvider (InitCalendarProviderRule)
    fun testDeleteDirtyEventsWithoutInstances_Recurring_Instances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 3 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=3"))
        }
        val localEvent = LocalEvent(calendar, event, "filename.ics", null, null, LocalResource.FLAG_REMOTELY_PRESENT)
        localEvent.add()
        val eventId = localEvent.id!!

        // set event as dirty
        provider.update(ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId), ContentValues(1).apply {
            put(Events.DIRTY, 1)
        }, null, null)

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is not marked as deleted
        provider.query(
            ContentUris.withAppendedId(Events.CONTENT_URI.asSyncAdapter(account), eventId),
            arrayOf(Events.DELETED), null, null, null
        )!!.use { cursor ->
            cursor.moveToNext()
            assertEquals(0, cursor.getInt(0))
        }
    }

}