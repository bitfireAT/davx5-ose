/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.Manifest
import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AospTest {

    @JvmField
    @Rule
    val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )!!

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    private val provider by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
    }

    private lateinit var calendarUri: Uri

    @Before
    fun prepare() {
        calendarUri = provider.insert(
            CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, testAccount.name)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, testAccount.type)
                put(CalendarContract.Calendars.NAME, "Test Calendar")
            }
        )!!
    }

    @After
    fun shutdown() {
        provider.delete(calendarUri, null, null)
        provider.close()
    }

    private fun Uri.asSyncAdapter() =
        buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "1")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, testAccount.name)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, testAccount.type)
            .build()


    @Test
    fun testInfiniteRRule() {
        assertNotNull(provider.insert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, ContentUris.parseId(calendarUri))
            put(CalendarContract.Events.DTSTART, 1643192678000)
            put(CalendarContract.Events.DURATION, "P1H")
            put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            put(CalendarContract.Events.TITLE, "Test event with infinite RRULE")
        }))
    }

    @Test(expected = AssertionError::class)
    fun testInfiniteRRulePlusRDate() {
        // see https://issuetracker.google.com/issues/37116691

        assertNotNull(provider.insert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, ContentUris.parseId(calendarUri))
            put(CalendarContract.Events.DTSTART, 1643192678000)
            put(CalendarContract.Events.DURATION, "PT1H")
            put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            put(CalendarContract.Events.RDATE, "20230101T000000Z")
            put(CalendarContract.Events.TITLE, "Test event with infinite RRULE and RDATE")
        }))

        /** FAILS:
            W RecurrenceProcessor: DateException with r=FREQ=YEARLY;WKST=MO rangeStart=135697573414 rangeEnd=9223372036854775807
            W CalendarProvider2: Could not calculate last date.
            W CalendarProvider2: com.android.calendarcommon2.DateException: No range end provided for a recurrence that has no UNTIL or COUNT.
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.expand(RecurrenceProcessor.java:766)
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.expand(RecurrenceProcessor.java:661)
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.getLastOccurence(RecurrenceProcessor.java:130)
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.getLastOccurence(RecurrenceProcessor.java:61)
         */
    }

}