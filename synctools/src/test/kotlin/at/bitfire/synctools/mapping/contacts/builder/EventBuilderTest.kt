/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import at.bitfire.synctools.vcard.property.XAbDate
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.util.PartialDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class EventBuilderTest {

    @Test
    fun testEmpty() {
        EventBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testStartDate_Date_Instant() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            anniversary = Anniversary(Instant.ofEpochSecond(1683924316))
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("2023-05-12T20:45:16.000Z", result[0].values[CommonDataKinds.Event.START_DATE])
        }
    }

    @Test
    fun testStartDate_Date_LocalDate() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            anniversary = Anniversary(
                LocalDate.of(1984, 8, 20)
            )
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("1984-08-20", result[0].values[CommonDataKinds.Event.START_DATE])
        }
    }

    @Test
    fun testStartDate_Date_LocalDateTime() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            anniversary = Anniversary(
                LocalDateTime.of(1984, 8, 20, 12, 30, 51)
            )
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("1984-08-20T12:30:51.000Z", result[0].values[CommonDataKinds.Event.START_DATE])
        }
    }

    @Test
    fun testStartDate_DateTime_WithOffset_OffsetDateTime() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            birthDay = Birthday(
                OffsetDateTime.of(1984, 7, 20, 0, 0, 0, 0, ZoneOffset.ofHours(1))
            )
        }, false).build().also { result ->
            assertEquals("1984-07-19T23:00:00.000Z", result[0].values[CommonDataKinds.Event.START_DATE])
        }
    }


    @Test
    fun testStartDate_PartialDate_NoYear() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            anniversary = Anniversary(PartialDate.builder()
                .date(20)
                .month(8)
                .build())
        }, true).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("--08-20", result[0].values[CommonDataKinds.Event.START_DATE])
        }
    }

    @Test
    fun testStartDate_PartialDate_NoYear_ButHour() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            anniversary = Anniversary(PartialDate.builder()
                .date(20)
                .month(8)
                .hour(14)
                .build())
        }, true).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("--08-20T14:00:00.000Z", result[0].values[CommonDataKinds.Event.START_DATE])
        }
    }


    @Test
    fun testLabel() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            customDates += LabeledProperty(XAbDate(PartialDate.builder()
                .date(20)
                .month(8)
                .build()), "Custom Event")
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Event.TYPE_CUSTOM, result[0].values[CommonDataKinds.Event.TYPE])
            assertEquals("Custom Event", result[0].values[CommonDataKinds.Event.LABEL])
        }
    }


    @Test
    fun testMimeType() {
        val c = Contact().apply {
            anniversary = Anniversary(
                LocalDate.of(1984, /* zero-based */ 7, 20)
            )
        }
        EventBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(CommonDataKinds.Event.CONTENT_ITEM_TYPE, result[0].values[CommonDataKinds.Event.MIMETYPE])
        }
    }


    @Test
    fun testType_Anniversary() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            anniversary = Anniversary(
                LocalDate.of(1984, /* zero-based */ 7, 20)
            )
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Event.TYPE_ANNIVERSARY, result[0].values[CommonDataKinds.Event.TYPE])
        }
    }

    @Test
    fun testType_Birthday() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            birthDay = Birthday(
                LocalDate.of(1984, /* zero-based */ 7, 20)
            )
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Event.TYPE_BIRTHDAY, result[0].values[CommonDataKinds.Event.TYPE])
        }
    }

    @Test
    fun testType_Other() {
        EventBuilder(Uri.EMPTY, null, Contact().apply {
            customDates += LabeledProperty(XAbDate(PartialDate.builder()
                .date(20)
                .month(8)
                .build()))
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Event.TYPE_OTHER, result[0].values[CommonDataKinds.Event.TYPE])
        }
    }

}