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
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.icalendar.Css3Color
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class AndroidCalendarProviderTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        private val testAccount = Account(AndroidCalendarProviderTest::class.java.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

        private lateinit var client: ContentProviderClient
        private lateinit var provider: AndroidCalendarProvider
        private lateinit var calendar: AndroidCalendar

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
            provider = AndroidCalendarProvider(testAccount, client)

            calendar = TestCalendar.create(testAccount, client)
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


    @Test
    fun testCreateAndGetCalendar() {
        // create calendar
        val calendar = provider.createAndGetCalendar(
            contentValuesOf(
                Calendars.NAME to "TestCalendar",
                Calendars.CALENDAR_DISPLAY_NAME to "ical4android Test Calendar",
                Calendars.VISIBLE to 0,
                Calendars.SYNC_EVENTS to 0
            )
        )

        // delete calendar
        assertEquals(1, calendar.delete())
    }


    @Test
    fun testProvideCss3Colors() {
        provider.provideCss3ColorIndices()
        assertEquals(Css3Color.entries.size, countColors())
    }

    @Test
    fun testInsertColors_AlreadyThere() {
        provider.provideCss3ColorIndices()
        provider.provideCss3ColorIndices()
        assertEquals(Css3Color.entries.size, countColors())
    }

    @Test
    fun testRemoveCss3Colors() {
        provider.provideCss3ColorIndices()

        // insert an event with that color
        val cal = TestCalendar.create(testAccount, client, withColors = true)
        try {
            // add event with color
            cal.addEvent(Entity(contentValuesOf(
                Events.CALENDAR_ID to cal.id,
                Events.DTSTART to System.currentTimeMillis(),
                Events.DTEND to System.currentTimeMillis() + 1000,
                Events.EVENT_COLOR_KEY to Css3Color.limegreen.name,
                Events.TITLE to "Test event with color"
            )))

            provider.removeColorIndices()
            assertEquals(0, countColors())
        } finally {
            cal.delete()
        }
    }

    private fun countColors(): Int {
        client.query(provider.colorsUri, null, null, null, null)!!.use { cursor ->
            cursor.moveToNext()
            return cursor.count
        }
    }

}