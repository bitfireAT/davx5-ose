/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.LegacyAndroidCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.techbee.jtx.JtxContract.asSyncAdapter
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
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
class LocalEventTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

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
    fun testPrepareForUpload_NoUid() {
        // create event
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event without uid"
        }

        val legacyCalendar = LegacyAndroidCalendar(calendar.androidCalendar)
        legacyCalendar.add(event = event, syncId = "filename.ics", flags = LocalResource.FLAG_REMOTELY_PRESENT)
        val localEvent = calendar.findByName("filename.ics")!!

        // prepare for upload - this should generate a new random uuid, returned as filename
        val fileNameWithSuffix = localEvent.prepareForUpload()
        val fileName = fileNameWithSuffix.removeSuffix(".ics")

        // throws an exception if fileName is not an UUID
        UUID.fromString(fileName)

        // UID in calendar storage should be the same as file name
        client.query(
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
        val legacyCalendar = LegacyAndroidCalendar(calendar.androidCalendar)
        legacyCalendar.add(event = event, syncId = "filename.ics", flags = LocalResource.FLAG_REMOTELY_PRESENT)
        val localEvent = calendar.findByName("filename.ics")!!

        // prepare for upload - this should use the UID for the file name
        val fileNameWithSuffix = localEvent.prepareForUpload()
        val fileName = fileNameWithSuffix.removeSuffix(".ics")

        assertEquals(event.uid, fileName)

        // UID in calendar storage should still be set, too
        client.query(
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
        val legacyCalendar = LegacyAndroidCalendar(calendar.androidCalendar)
        legacyCalendar.add(event = event, syncId = "filename.ics", flags = LocalResource.FLAG_REMOTELY_PRESENT)
        val localEvent = calendar.findByName("filename.ics")!!

        // prepare for upload - this should generate a new random uuid, returned as filename
        val fileNameWithSuffix = localEvent.prepareForUpload()
        val fileName = fileNameWithSuffix.removeSuffix(".ics")

        // throws an exception if fileName is not an UUID
        UUID.fromString(fileName)

        // UID in calendar storage shouldn't have been changed
        client.query(
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
        val legacyCalendar = LegacyAndroidCalendar(calendar.androidCalendar)
        legacyCalendar.add(event = event, syncId = "filename.ics", flags = LocalResource.FLAG_REMOTELY_PRESENT)
        val localEvent = calendar.findByName("filename.ics")!!
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
    fun testDeleteDirtyEventsWithoutInstances_Recurring_Instances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 3 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=3"))
        }
        val legacyCalendar = LegacyAndroidCalendar(calendar.androidCalendar)
        legacyCalendar.add(event = event, syncId = "filename.ics", flags = LocalResource.FLAG_REMOTELY_PRESENT)
        val localEvent = calendar.findByName("filename.ics")!!
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