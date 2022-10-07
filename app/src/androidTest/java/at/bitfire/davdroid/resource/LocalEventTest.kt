/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.InitCalendarProviderRule
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.MiscUtils.ContentProviderClientHelper.closeCompat
import at.techbee.jtx.JtxContract.asSyncAdapter
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestRule
import java.util.*

class LocalEventTest {

    companion object {

        @JvmField
        @ClassRule
        val initCalendarProviderRule: TestRule = InitCalendarProviderRule.getInstance()

        private val account = Account("LocalCalendarTest", ACCOUNT_TYPE_LOCAL)

        private lateinit var provider: ContentProviderClient
        private lateinit var calendar: LocalCalendar

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            provider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            provider.closeCompat()
        }
    }

    @Before
    fun createCalendar() {
        val uri = AndroidCalendar.create(account, provider, ContentValues())
        calendar = AndroidCalendar.findByID(account, provider, LocalCalendar.Factory, ContentUris.parseId(uri))
    }

    @After
    fun removeCalendar() {
        calendar.delete()
    }


    @Test
    fun testNumDirectInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(1, LocalEvent.numDirectInstances(provider, account, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(5, LocalEvent.numDirectInstances(provider, account, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event without end"
            rRules.add(RRule("FREQ=DAILY"))
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertNull(LocalEvent.numDirectInstances(provider, account, localEvent.id!!))
    }

    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumDirectInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 53 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 is not supported by Android <11 Calendar Storage
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, LocalEvent.numDirectInstances(provider, account, localEvent.id!!))
        else
            assertNull(LocalEvent.numDirectInstances(provider, account, localEvent.id!!))
    }

    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumDirectInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 2 years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()
        val number = LocalEvent.numDirectInstances(provider, account, localEvent.id!!)

        // Some android versions (i.e. <=Q and S) return 365*2 instances (wrong, 365*2+1 => correct),
        // but we are satisfied with either result for now
        assertTrue(number == 365*2 || number == 365*2+1)
    }

    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumDirectInstances_RecurringWithExdate() {
        val event = Event().apply {
            dtStart = DtStart(Date("20220120T010203Z"))
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
            exDates.add(ExDate(DateList("20220121T010203Z", Value.DATE_TIME)))
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(4, LocalEvent.numDirectInstances(provider, account, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_RecurringWithExceptions() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220122T010203Z")
                dtStart = DtStart("20220122T130203Z")
                summary = "Exception on 3rd day"
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220124T010203Z")
                dtStart = DtStart("20220122T160203Z")
                summary = "Exception on 5th day"
            })
        }
        val localEvent = LocalEvent(calendar, event, "filename.ics", null, null, 0)
        localEvent.add()

        assertEquals(5-2, LocalEvent.numDirectInstances(provider, account, localEvent.id!!))
    }


    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(1, LocalEvent.numInstances(provider, account, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(5, LocalEvent.numInstances(provider, account, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with infinite instances"
            rRules.add(RRule("FREQ=YEARLY"))
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertNull(LocalEvent.numInstances(provider, account, localEvent.id!!))
    }

    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over 22 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 not supported by Android <11 Calendar Storage
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, LocalEvent.numInstances(provider, account, localEvent.id!!))
        else
            assertNull(LocalEvent.numInstances(provider, account, localEvent.id!!))
    }

    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over two years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                365*2       // Android <10: does not include UNTIL (incorrect!)
            else
                365*2 + 1,  // Android ≥10: includes UNTIL (correct)
            LocalEvent.numInstances(provider, account, localEvent.id!!))
    }

    @Test
    fun testNumInstances_RecurringWithExceptions() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 6 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=6"))
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220122T010203Z")
                dtStart = DtStart("20220122T130203Z")
                summary = "Exception on 3rd day"
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220124T010203Z")
                dtStart = DtStart("20220122T160203Z")
                summary = "Exception on 5th day"
            })
        }
        val localEvent = LocalEvent(calendar, event, "filename.ics", null, null, 0)
        val uri = localEvent.add()

        calendar.findById(localEvent.id!!)

        assertEquals(6, LocalEvent.numInstances(provider, account, localEvent.id!!))
    }


    @Test
    fun testMarkEventAsDeleted() {
        // Create event
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "A fine event"
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        // Delete event
        LocalEvent.markAsDeleted(provider, account, localEvent.id!!)

        // Get the status of whether the event is deleted
        provider.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, localEvent.id!!).asSyncAdapter(account),
            arrayOf(Events.DELETED),
            null,
            null, null
        )!!.use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }


    @Test
    fun testPrepareForUpload_NoUid() {
        // create event
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event without uid"
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add()    // save it to calendar storage

        // prepare for upload - this should generate a new random uuid, returned as filename
        val fileNameWithSuffix = localEvent.prepareForUpload()
        val fileName = fileNameWithSuffix.removeSuffix(".ics")

        // throws an exception if fileName is not an UUID
        UUID.fromString(fileName)

        // UID in calendar storage should be the same as file name
        provider.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, localEvent.id!!).asSyncAdapter(account),
            arrayOf(Events.UID_2445), null, null, null
        )!!.use { cursor ->
            cursor.moveToFirst()
            assertEquals(fileName, cursor.getString(0))
        }
    }

    @Test
    fun testPrepareForUpload_NormalUid() {
        // create event
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with normal uid"
            uid = "some-event@hostname.tld"     // old UID format, UUID would be new format
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add() // save it to calendar storage

        // prepare for upload - this should use the UID for the file name
        val fileNameWithSuffix = localEvent.prepareForUpload()
        val fileName = fileNameWithSuffix.removeSuffix(".ics")

        assertEquals(event.uid, fileName)

        // UID in calendar storage should still be set, too
        provider.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, localEvent.id!!).asSyncAdapter(account),
            arrayOf(Events.UID_2445), null, null, null
        )!!.use { cursor ->
            cursor.moveToFirst()
            assertEquals(fileName, cursor.getString(0))
        }
    }

    @Test
    fun testPrepareForUpload_UidHasDangerousChars() {
        // create event
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with funny uid"
            uid = "https://www.example.com/events/asdfewfe-cxyb-ewrws-sadfrwerxyvser-asdfxye-"
        }
        val localEvent = LocalEvent(calendar, event, null, null, null, 0)
        localEvent.add() // save it to calendar storage

        // prepare for upload - this should generate a new random uuid, returned as filename
        val fileNameWithSuffix = localEvent.prepareForUpload()
        val fileName = fileNameWithSuffix.removeSuffix(".ics")

        // throws an exception if fileName is not an UUID
        UUID.fromString(fileName)

        // UID in calendar storage shouldn't have been changed
        provider.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, localEvent.id!!).asSyncAdapter(account),
            arrayOf(Events.UID_2445), null, null, null
        )!!.use { cursor ->
            cursor.moveToFirst()
            assertEquals(event.uid, cursor.getString(0))
        }
    }


    @Test
    fun testDeleteDirtyEventsWithoutInstances_NoInstances_Exdate() {
        // TODO
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
