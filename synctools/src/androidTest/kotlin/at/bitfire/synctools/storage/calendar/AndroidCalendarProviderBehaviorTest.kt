/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.test.assertContentValuesEqual
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.time.Duration
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Tests some Android calendar provider behavior that is not well-documented.
 *
 * Note: to show verbose calendar provider logs, enable the log TAGs (see respective
 * calendar provider classes) in adb. The most important ones are:
 *
 * ```
 * adb shell setprop log.tag.CalendarProvider2 VERBOSE
 * adb shell setprop log.tag.CalendarCache VERBOSE
 * adb shell setprop log.tag.CalInstances VERBOSE
 * adb shell setprop log.tag.EventRecur VERBOSE
 * adb shell setprop log.tag.RecurrenceProcessor VERBOSE
 * adb shell setprop log.tag.RecurrenceSet VERBOSE
 * ```
 */
class AndroidCalendarProviderBehaviorTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        private val testAccount = Account(AndroidCalendarProviderBehaviorTest::class.java.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

        private lateinit var client: ContentProviderClient
        private lateinit var calendar: AndroidCalendar
        private lateinit var recurringCalendar: AndroidRecurringCalendar

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

            calendar = TestCalendar.create(testAccount, client)
            recurringCalendar = AndroidRecurringCalendar(calendar)
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            calendar.delete()
            client.close()
        }

    }

    @After
    fun cleanUp() {
        // Clean up events after every test
        calendar.deleteAllEvents()
    }

    private val testStartTime = TestCalendar.instantNowAligned()
    private val testStartMillis = testStartTime.toEpochMilli()


    /**
     * To make sure that's not a problem to insert an event with DTEND = DTSTART.
     */
    @Test
    fun testInsertEventWithDtEndEqualsDtStart() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to testStartMillis,
            Events.DTEND to testStartMillis,
            Events.TITLE to "Event with DTSTART = DTEND"
        )
        val id = calendar.addEvent(Entity(values))

        // Google Calendar 2025.44.1-827414499-release correctly shows this event [2025/11/29]

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }

    /**
     * To make sure that's not a problem to insert an (invalid/useless) RRULE with UNTIL before the event's DTSTART.
     */
    @Test
    fun testInsertEventWithRRuleUntilBeforeDtStart() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with useless RRULE",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251002T000000Z"
        )
        val id = calendar.addEvent(Entity(values))

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }

    /**
     * To verify that it's a problem to insert a recurring all-day event with a duration of zero seconds.
     * See:
     *
     * - https://github.com/bitfireAT/davx5-ose/issues/1823
     * - https://github.com/bitfireAT/synctools/issues/144
     */
    @Test(expected = LocalStorageException::class)
    fun testInsertRecurringAllDayEventWithDurationZeroSeconds() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ALL_DAY to 1,
            Events.DTSTART to 1763510400000,    // Wed Nov 19 2025 00:00:00 GMT+0000
            Events.DURATION to "PT0S",
            Events.TITLE to "Recurring all-day event with zero seconds duration",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251122"
        )
        calendar.addEvent(Entity(values))
    }

    /**
     * To make sure that it's not a problem to insert a recurring all-day event with a duration of zero days.
     */
    @Test
    fun testInsertRecurringAllDayEventWithDurationZeroDays() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ALL_DAY to 1,
            Events.DTSTART to 1763510400000,    // Wed Nov 19 2025 00:00:00 GMT+0000
            Events.DURATION to "P0D",
            Events.TITLE to "Recurring all-day event with zero seconds duration",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251122"
        )
        val id = calendar.addEvent(Entity(values))

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }

    /**
     * To make sure that it's not a problem to insert a recurring event with a duration of zero seconds.
     */
    @Test
    fun testInsertRecurringNonAllDayEventWithDurationZeroSeconds() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.DURATION to "PT0S",
            Events.TITLE to "Recurring non-all-day event with zero seconds duration",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251002T000000Z"
        )
        val id = calendar.addEvent(Entity(values))

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }


    @Test
    fun testInstancesExpansionNeedsSyncEvents_syncEventsNotSet() {
        // Set SYNC_EVENTS to 0 to disable instance expansion
        calendar.update(contentValuesOf(CalendarContract.Calendars.SYNC_EVENTS to 0))

        try {
            val id = calendar.addEvent(
                Entity(
                    contentValuesOf(
                        Events.CALENDAR_ID to calendar.id,
                        Events.DTSTART to testStartMillis,
                        Events.DURATION to "PT1H",
                        Events.TITLE to "Event with 5 instances (SYNC_EVENTS=0)",
                        Events.RRULE to "FREQ=DAILY;COUNT=5"
                    )
                )
            )

            // When SYNC_EVENTS=0, numInstances should return 0
            val numInstances = calendar.numInstances(id, checkSyncEvents = false)
            assertEquals(0, numInstances)
        } finally {
            calendar.update(contentValuesOf(CalendarContract.Calendars.SYNC_EVENTS to 1))
        }
    }

    @Test
    fun testInstancesExpansionNeedsSyncEvents_syncEventsSet() {
        // Ensure SYNC_EVENTS is set to 1 to enable instance expansion
        assertEquals("Test calendars are expected to have SYNC_EVENTS=1",
            1, calendar.values.getAsInteger(CalendarContract.Calendars.SYNC_EVENTS))

        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to testStartMillis,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with 5 instances (SYNC_EVENTS=1)",
            Events.RRULE to "FREQ=DAILY;COUNT=5"
        )))
        
        // When SYNC_EVENTS=1, numInstances should return 5
        val numInstances = calendar.numInstances(id, checkSyncEvents = false)
        assertEquals(5, numInstances)
    }

    @Test
    fun testInstancesExpansionMatchesMillisecondExceptions_alignedToSecond() {
        val testStartTimeAligned = testStartTime.truncatedTo(ChronoUnit.SECONDS)
        val id = recurringCalendar.addEventAndExceptions(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events._SYNC_ID to UUID.randomUUID().toString(),
                Events.DTSTART to testStartTimeAligned.toEpochMilli(),
                Events.DURATION to "PT1H",
                Events.TITLE to "Event with 5 instances, one cancelled",
                Events.RRULE to "FREQ=DAILY;COUNT=5"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to (testStartTimeAligned + Duration.ofDays(4)).toEpochMilli(),
                    Events.DTSTART to (testStartTimeAligned + Duration.ofDays(4) + Duration.ofHours(1)).toEpochMilli(), // one hour later
                    Events.DTEND to (testStartTimeAligned + Duration.ofDays(4) + Duration.ofHours(2)).toEpochMilli(),
                    Events.TITLE to "Exception on 5th day",
                    Events.STATUS to Events.STATUS_CANCELED
                ))
            )
        ))
        // Works on every API level because it doesn't set milliseconds
        assertEquals(5 - /* one canceled */ 1, calendar.numInstances(id))
    }

    @Test
    fun testInstancesExpansionMatchesMillisecondExceptions_notAlignedToSecond() {
        val testStartTimeUnaligned = testStartTime.with(ChronoField.MILLI_OF_SECOND, 123)
        val id = recurringCalendar.addEventAndExceptions(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events._SYNC_ID to UUID.randomUUID().toString(),
                Events.DTSTART to testStartTimeUnaligned.toEpochMilli(),
                Events.DURATION to "PT1H",
                Events.TITLE to "Event with 5 instances, one cancelled",
                Events.RRULE to "FREQ=DAILY;COUNT=5"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to (testStartTimeUnaligned + Duration.ofDays(4)).toEpochMilli(),
                    Events.DTSTART to (testStartTimeUnaligned + Duration.ofDays(4) + Duration.ofHours(1)).toEpochMilli(), // one hour later
                    Events.DTEND to (testStartTimeUnaligned + Duration.ofDays(4) + Duration.ofHours(2)).toEpochMilli(),
                    Events.TITLE to "Exception on 5th day",
                    Events.STATUS to Events.STATUS_CANCELED
                ))
            )
        ))
        // Works only on newer API levels because that support milliseconds
        val expectedInstances =
            if (AndroidCalendarProvider.matchesExceptionsWithMilliseconds)
                5 - /* one canceled */ 1
            else
                5 // ORIGINAL_INSTANCE_TIME doesn't match DTSTART with millisecond resolution
        assertEquals(expectedInstances, calendar.numInstances(id))
    }


    /**
     * Reported as https://issuetracker.google.com/issues/446730408.
     */
    @Test(expected = NullPointerException::class)
    fun testUpdateEventStatusFromNonNullToNull() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to System.currentTimeMillis(),
            Events.DTEND to System.currentTimeMillis() + 3600000,
            Events.TITLE to "Some Event (Status tentative)",
            Events.STATUS to Events.STATUS_TENTATIVE
        )))

        calendar.updateEventRow(id, contentValuesOf(
            Events.STATUS to null,      // updating status to null causes NullPointerException
            Events.TITLE to "Some Event (Status null)"
        ))
    }

    @Test
    fun testUpdateEventStatusFromNullToNotPresent() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to System.currentTimeMillis(),
            Events.DTEND to System.currentTimeMillis() + 3600000,
            Events.TITLE to "Some Event (Status tentative)",
            Events.STATUS to null
        )))

        // No problem because STATUS is not explicitly set.
        calendar.updateEventRow(id, contentValuesOf(
            //Events.STATUS to null,
            Events.TITLE to "Some Event (Status null)"
        ))
    }

    /**
     * Reported as https://issuetracker.google.com/issues/446730408.
     */
    @Test(expected = NullPointerException::class)
    fun testUpdateEventStatusFromNullToNull() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to System.currentTimeMillis(),
            Events.DTEND to System.currentTimeMillis() + 3600000,
            Events.TITLE to "Some Event (Status tentative)",
            Events.STATUS to null
        )))

        calendar.updateEventRow(id, contentValuesOf(
            Events.STATUS to null,      // updating status to null causes NullPointerException
            Events.TITLE to "Some Event (Status null)"
        ))
    }

}