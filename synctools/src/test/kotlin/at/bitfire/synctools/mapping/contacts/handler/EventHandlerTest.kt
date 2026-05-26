/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Event
import at.bitfire.synctools.mapping.contacts.Contact
import ezvcard.util.PartialDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class EventHandlerTest {

    // Tested date formats are as provided by Android AOSP Contacts App
    // https://android.googlesource.com/platform/packages/apps/Contacts/+/refs/tags/android-13.0.0_r49/src/com/android/contacts/util/CommonDateUtils.java

    @Test
    fun testStartDate_Empty() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            putNull(Event.START_DATE)
        }, contact)
        assertNull(contact.anniversary)
        assertNull(contact.birthDay)
        assertTrue(contact.customDates.isEmpty())
    }

    @Test
    fun testStartDate_FULL_DATE_FORMAT() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "1984-08-20")
        }, contact)
        assertEquals(
            LocalDate.of(1984, 8,  20),
            contact.customDates[0].property.date
        )
    }

    @Test
    fun testStartDate_DATE_AND_TIME_FORMAT() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "1953-10-15T23:10:12.345Z")
        }, contact)
        assertEquals(
            OffsetDateTime.of(1953, 10,  15, 23, 10, 12, 345_000_000, ZoneOffset.UTC),
            contact.customDates[0].property.date
        )
    }

    @Test
    fun testStartDate_NO_YEAR_DATE_FORMAT() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "--08-20")
        }, contact)
        assertEquals(PartialDate.parse("--0820"), contact.customDates[0].property.partialDate)
    }

    @Test
    fun testStartDate_NO_YEAR_DATE_AND_TIME_FORMAT() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "--08-20T23:10:12.345Z")
        }, contact)
        // Note that nanoseconds are stripped in PartialDate
        assertEquals(
            PartialDate.builder().month(8).date(20).hour(23).minute(10).second(12).offset(ZoneOffset.UTC).build(),
            contact.customDates[0].property.partialDate
        )
    }


    @Test
    fun testType_Anniversary() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "--08-20")
            put(Event.TYPE, Event.TYPE_ANNIVERSARY)
        }, contact)
        assertNotNull(contact.anniversary)
        assertNull(contact.birthDay)
        assertTrue(contact.customDates.isEmpty())
    }

    @Test
    fun testType_Birthday() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "--08-20")
            put(Event.TYPE, Event.TYPE_BIRTHDAY)
        }, contact)
        assertNull(contact.anniversary)
        assertNotNull(contact.birthDay)
        assertTrue(contact.customDates.isEmpty())
    }

    @Test
    fun testType_Custom_Label() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "--08-20")
            put(Event.TYPE, Event.TYPE_CUSTOM)
            put(Event.LABEL, "Label 1")
        }, contact)
        assertNull(contact.anniversary)
        assertNull(contact.birthDay)
        assertFalse(contact.customDates.isEmpty())
        assertEquals("Label 1", contact.customDates[0].label)
    }

    @Test
    fun testType_Other() {
        val contact = Contact()
        EventHandler.handle(ContentValues().apply {
            put(Event.START_DATE, "--08-20")
            put(Event.TYPE, Event.TYPE_OTHER)
        }, contact)
        assertNull(contact.anniversary)
        assertNull(contact.birthDay)
        assertFalse(contact.customDates.isEmpty())
    }


    // test parser methods

    @Test
    fun test_parseFullDate_ISO_DATE_AND_TIME_FORMAT_DateTime() {
        assertEquals(
            OffsetDateTime.of(1953, 10,  15, 23, 10, 0, 0, ZoneOffset.UTC),
            EventHandler.parseFullDate("1953-10-15T23:10:00Z")
        )
    }

    @Test
    fun test_parseFullDate_FULL_DATE_FORMAT_Date() {
        assertEquals(
            LocalDate.of(1953, 10,  15),
            EventHandler.parseFullDate("1953-10-15")
        )
    }


    @Test
    fun test_parsePartialDate_NO_YEAR_DATE_FORMAT() {
        assertEquals(
            PartialDate.builder().month(10).date(15).build(),
            EventHandler.parsePartialDate("--10-15")
        )
    }

    @Test
    fun test_parsePartialDate_NO_YEAR_DATE_AND_TIME_FORMAT() {
        // Partial date does not support nanoseconds, so they will be removed
        assertEquals(
            PartialDate.builder().month(8).date(20).hour(23).minute(10).second(12).offset(ZoneOffset.UTC).build(),
            EventHandler.parsePartialDate("--08-20T23:10:12.345Z")
        )
    }

}