/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import at.bitfire.ical4android.util.TimeApiExtensions.abs
import at.bitfire.ical4android.util.TimeApiExtensions.toDuration
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.JapaneseDate

class TimeApiExtensionsTest {

    @Test
    fun testTemporalAmount_abs_Duration_negative() {
        assertEquals(
            Duration.ofMinutes(1),
            Duration.ofMinutes(-1).abs()
        )
    }

    @Test
    fun testTemporalAmount_abs_Duration_positive() {
        assertEquals(
            Duration.ofDays(1),
            Duration.ofDays(1).abs()
        )
    }

    @Test
    fun testTemporalAmount_abs_Period_negative() {
        assertEquals(
            Period.ofWeeks(1),
            Period.ofWeeks(-1).abs()
        )
    }

    @Test
    fun testTemporalAmount_abs_Period_positive() {
        assertEquals(
            Period.ofDays(1),
            Period.ofDays(1).abs()
        )
    }


    @Test
    fun testTemporalAmount_toDuration() {
        assertEquals(Duration.ofHours(1), Duration.ofHours(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(1), Duration.ofDays(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(1), Period.ofDays(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(7), Period.ofWeeks(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(365), Period.ofYears(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(366), Period.ofYears(1).toDuration(Instant.ofEpochSecond(1577836800)))
    }

    @Test
    fun testTemporalAmount_toRfc5545Duration_Duration() {
        assertEquals("PT0S", Duration.ofDays(0).toRfc5545Duration(Instant.EPOCH))
        assertEquals("P2W", Duration.ofDays(14).toRfc5545Duration(Instant.EPOCH))
        assertEquals("P15D", Duration.ofDays(15).toRfc5545Duration(Instant.EPOCH))
        assertEquals("P16DT1H", Duration.parse("P16DT1H").toRfc5545Duration(Instant.EPOCH))
        assertEquals("P16DT1H4M", Duration.parse("P16DT1H4M").toRfc5545Duration(Instant.EPOCH))
        assertEquals("P2DT1H4M5S", Duration.parse("P2DT1H4M5S").toRfc5545Duration(Instant.EPOCH))
        assertEquals("PT1M20S", Duration.parse("PT80S").toRfc5545Duration(Instant.EPOCH))

        assertEquals("P0D", Period.ofWeeks(0).toRfc5545Duration(Instant.EPOCH))

        val date20200601 = Instant.ofEpochSecond(1590969600L)
        // 2020/06/01 + 1 year   = 2021/06/01 (365 days)
        // 2021/06/01 + 2 months = 2020/08/01 (30 days + 31 days = 61 days)
        // 2020/08/01 + 3 days   = 2020/08/04 (3 days)
        // total: 365 days + 61 days + 3 days = 429 days
        assertEquals("P429D", Period.of(1, 2, 3).toRfc5545Duration(date20200601))
        assertEquals("P2W", Period.ofWeeks(2).toRfc5545Duration(date20200601))
        assertEquals("P2W", Period.ofDays(14).toRfc5545Duration(date20200601))
        assertEquals("P15D", Period.ofDays(15).toRfc5545Duration(date20200601))
        assertEquals("P30D", Period.ofMonths(1).toRfc5545Duration(date20200601))
    }


    @Test
    fun `LocalDate_toLocalDate()`() {
        val localDate = LocalDate.now()

        val result = localDate.toLocalDate()

        assertEquals(localDate, result)
    }

    @Test
    fun `LocalDateTime_toLocalDate()`() {
        val localDateTime = LocalDateTime.of(2026, 3, 17, 0, 0, 0)

        val result = localDateTime.toLocalDate()

        assertEquals(LocalDate.of(2026, 3, 17), result)
    }

    @Test
    fun `OffsetDateTime_toLocalDate()`() {
        val offsetDateTime = OffsetDateTime.of(2026, 3, 17, 0, 0, 0, 0, ZoneOffset.UTC)

        val result = offsetDateTime.toLocalDate()

        assertEquals(LocalDate.of(2026, 3, 17), result)
    }

    @Test
    fun `ZonedDateTime_toLocalDate()`() {
        val zonedDateTime = ZonedDateTime.of(2026, 3, 17, 0, 0, 0, 0, ZoneOffset.UTC)

        val result = zonedDateTime.toLocalDate()

        assertEquals(LocalDate.of(2026, 3, 17), result)
    }

    @Test
    fun `Instant_toLocalDate()`() {
        val instant = Instant.ofEpochSecond(1773754730)

        val result = instant.toLocalDate()

        assertEquals(LocalDate.of(2026, 3, 17), result)
    }

    @Test
    fun `toLocalDate() on unsupported type`() {
        try {
            JapaneseDate.now().toLocalDate()

            fail("Expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("Unsupported Temporal type: java.time.chrono.JapaneseDate", e.message)
        }
    }

}