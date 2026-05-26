/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.DefaultTimezoneRule
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils.androidTimezoneId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.StringReader
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal
import java.time.temporal.UnsupportedTemporalTypeException
import java.time.zone.ZoneRulesException
import java.util.Optional

class AndroidTimeUtilsTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Europe/Vienna")
    
    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    val tzBerlin: TimeZone = tzRegistry.getTimeZone("Europe/Berlin")!!
    val tzToronto: TimeZone = tzRegistry.getTimeZone("America/Toronto")!!

    val exDateGenerator: ((DateList<*>) -> ExDate<*>) = { dateList ->
        ExDate(dateList)
    }


    // androidStringToRecurrenceSets

    @Test
    fun testAndroidStringToRecurrenceSets_UtcTimes() {
        // list of UTC times
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "20150101T103010Z,20150702T103020Z",
            allDay = false,
            generator = exDateGenerator,
        )!!

        assertEquals(Optional.empty<TzId>(), exDate.getParameter<TzId>(Parameter.TZID))
        assertEquals(2, exDate.dates.size)
        assertEquals(Instant.parse("2015-01-01T10:30:10Z"), exDate.dates[0])
        assertEquals(Instant.parse("2015-07-02T10:30:20Z"), exDate.dates[1])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_ZonedTimes() {
        // list of time zone times
        val tzid = tzToronto.id

        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "$tzid;20150103T113030,20150704T113040",
            allDay = false,
            generator = exDateGenerator,
        )!!

        assertEquals(tzid, exDate.getParameter<TzId>(Parameter.TZID).get().value)
        assertEquals(2, exDate.dates.size)
        assertEquals(dateTimeValue("20150103T113030", tzToronto), exDate.dates[0])
        assertEquals(dateTimeValue("20150704T113040", tzToronto), exDate.dates[1])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_with_explicit_UTC_timezone() {
        val dbStr = "UTC;20150103T113030,20150704T113040"

        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            dbStr,
            allDay = false,
            generator = exDateGenerator,
        )!!

        assertTrue(exDate.getParameter<TzId>(Parameter.TZID).isEmpty)
        assertEquals(2, exDate.dates.size)
        assertEquals(dateTimeValue("20150103T113030Z"), exDate.dates[0])
        assertEquals(dateTimeValue("20150704T113040Z"), exDate.dates[1])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_Dates() {
        // list of dates
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "20150101T103010Z,20150702T103020Z",
            allDay = true,
            generator = exDateGenerator,
        )!!

        assertEquals(Optional.empty<TzId>(), exDate.getParameter<TzId>(Parameter.TZID))
        assertEquals(Value.DATE, exDate.getParameter<Value>(Parameter.VALUE).get())
        assertEquals(2, exDate.dates.size)
        assertEquals(dateValue("20150101"), exDate.dates[0])
        assertEquals(dateValue("20150702"), exDate.dates[1])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_ExcludePositive() {
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "${tzToronto.id};20150103T113030,20150105T113030",
            allDay = false,
            exclude = dateTimeValue("20150103T113030", tzToronto).toInstant(),
            generator = exDateGenerator,
        )!!

        assertEquals(1, exDate.dates.size)
        assertEquals(dateTimeValue("20150105T113030", tzToronto), exDate.dates[0])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_throws_DateTimeParseException() {
        try {
            AndroidTimeUtils.androidStringToRecurrenceSet(
                "20150103T113030",
                allDay = false,
                generator = exDateGenerator
            )
        } catch (e: IllegalStateException) {
            assertEquals("Floating DATE-TIME is not supported: 20150103T113030", e.message)
        }
    }

    @Test(expected = DateTimeException::class)
    fun testAndroidStringToRecurrenceSets_throws_DateTimeException() {
        AndroidTimeUtils.androidStringToRecurrenceSet(
            "+25;20150103T113030",
            allDay = false,
            generator = exDateGenerator
        )
    }

    @Test(expected = ZoneRulesException::class)
    fun testAndroidStringToRecurrenceSets_throws_ZoneRulesException() {
        AndroidTimeUtils.androidStringToRecurrenceSet(
            "Europe/ABC;20150103T113030",
            allDay = false,
            generator = exDateGenerator
        )
    }

    @Test
    fun testAndroidStringToRecurrenceSets_emptyInput() {
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "",
            allDay = false,
            generator = { error("Should not be called") },
        )
        assertNull(exDate)
    }

    @Test
    fun testAndroidStringToRecurrenceSets_Exclude() {
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "${tzToronto.id};20150103T113030",
            allDay = false,
            exclude = dateTimeValue("20150103T113030", tzToronto).toInstant(),
            generator = exDateGenerator,
        )
        assertNull(exDate)
    }


    // recurrenceSetsToOpenTasksString

    @Test
    fun testRecurrenceSetsToOpenTasksString_UtcTimes() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, ZoneOffset.UTC)
        )))
        assertEquals("20150101T060000Z,20150702T060000Z", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_ZonedTimes() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 6, 0, 0, 0, tzToronto.toZoneId()),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, tzToronto.toZoneId())
        )))
        assertEquals("20150101T120000,20150702T120000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_MixedTimes() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 1, 0, 0, 0, tzToronto.toZoneId()),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, tzToronto.toZoneId())
        )))
        assertEquals("20150101T070000,20150702T120000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_TimesAlthougAllDay() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 6, 0, 0, 0, tzToronto.toZoneId()),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, tzToronto.toZoneId())
        )))
        assertEquals("20150101,20150702", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, null))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_Dates() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(LocalDate.of(2015, 1, 1), LocalDate.of(2015, 7, 2))))
        assertEquals("20150101,20150702", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, null))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_DatesAlthoughTimeZone() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(LocalDate.of(2015, 1, 1), LocalDate.of(2015, 7, 2))))
        assertEquals("20150101T000000,20150702T000000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }


    @Test
    fun testParseDuration() {
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("3600S"))
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("P3600S"))
        assertEquals(Duration.parse("+PT3600S"), AndroidTimeUtils.parseDuration("+P3600S"))
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("PT3600S"))
        assertEquals(Duration.parse("+PT3600S"), AndroidTimeUtils.parseDuration("+PT3600S"))
        assertEquals(java.time.Period.parse("P10D"), AndroidTimeUtils.parseDuration("P1W3D"))
        assertEquals(java.time.Period.parse("P1D"), AndroidTimeUtils.parseDuration("1DT"))
        assertEquals(Duration.parse("P14DT3600S"), AndroidTimeUtils.parseDuration("P2W3600S"))
        assertEquals(Duration.parse("-P3DT4H5M6S"), AndroidTimeUtils.parseDuration("-P3D4H5M6S"))
        assertEquals(Duration.parse("PT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H"))
        assertEquals(Duration.parse("P4DT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H4D"))
        assertEquals(Duration.parse("P11DT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H4D1W"))
        assertEquals(Duration.parse("PT1H0M10S"), AndroidTimeUtils.parseDuration("1H10S"))
    }


    @Test
    fun `toTimestamp on LocalDate should use start of UTC day`() {
        val date = LocalDate.of(2026, 3, 12)

        val timestamp = date.toTimestamp()

        assertEquals(1773273600000L, timestamp)
    }

    @Test
    fun `toTimestamp on LocalDateTime should use system default time zone`() {
        val date = LocalDateTime.of(2026, 3, 12, 12, 34, 56)

        val timestamp = date.toTimestamp()

        assertEquals(1773315296000L, timestamp)
    }

    @Test
    fun `toTimestamp on OffsetDateTime`() {
        val date = OffsetDateTime.of(2026, 3, 12, 12, 0, 0, 0, ZoneOffset.ofHours(3))

        val timestamp = date.toTimestamp()

        assertEquals(1773306000000L, timestamp)
    }

    @Test
    fun `toTimestamp on ZonedDateTime`() {
        val date = ZonedDateTime.of(2026, 3, 12, 12, 0, 0, 0, ZoneId.of("Europe/Helsinki"))

        val timestamp = date.toTimestamp()

        assertEquals(1773309600000L, timestamp)
    }

    @Test
    fun `toTimestamp on Instant`() {
        val inputTimestamp = 1773273600000L
        val date = Instant.ofEpochMilli(inputTimestamp)

        val timestamp = date.toTimestamp()

        assertEquals(inputTimestamp, timestamp)
    }

    @Test
    fun `toTimestamp on unsupported type`() {
        try {
            JapaneseDate.now().toTimestamp()

            fail("Expected exception")
        } catch (e: UnsupportedTemporalTypeException) {
            assertEquals("Can't convert java.time.chrono.JapaneseDate to Instant", e.message)
        }
    }


    @Test
    fun `androidTimezoneId on LocalDate`() {
        val date = LocalDate.now()

        val timezoneId = date.androidTimezoneId()

        assertEquals("UTC", timezoneId)
    }

    @Test
    fun `androidTimezoneId on LocalDateTime`() {
        val date = LocalDateTime.now()

        val timezoneId = date.androidTimezoneId()

        assertEquals(tzRule.defaultZoneId.id, timezoneId)
    }

    @Test
    fun `androidTimezoneId on ZonedDateTime`() {
        val date = LocalDateTime.now().atZone(ZoneId.of("Europe/Dublin"))

        val timezoneId = date.androidTimezoneId()

        assertEquals("Europe/Dublin", timezoneId)
    }

    @Test
    fun `androidTimezoneId on Instant`() {
        val date = Instant.now()

        val timezoneId = date.androidTimezoneId()

        assertEquals("UTC", timezoneId)
    }

    @Test
    fun `androidTimezoneId on OffsetDateTime`() {
        try {
            OffsetDateTime.now().androidTimezoneId()

            fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("date-time which is neither floating nor UTC must be a ZonedDateTime", e.message)
        }
    }

    @Test
    fun `androidTimezoneId on ZonedDateTime from ical4j`() {
        val cal = CalendarBuilder().build(StringReader(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Etc/ABC
            BEGIN:STANDARD
            TZNAME:-03
            TZOFFSETFROM:-0300
            TZOFFSETTO:-0300
            DTSTART:19700101T000000
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            SUMMARY:Test Timezones
            DTSTART;TZID=Etc/ABC:20250828T130000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        ))
        val vEvent = cal.getComponent<VEvent>(Component.VEVENT).get()
        val date = vEvent.requireDtStart<Temporal>().date

        try {
            date.androidTimezoneId()

            fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals(
                "ical4j ZoneIds are not supported. Call DatePropertyTzMapper.normalizedDate() " +
                        "before passing a date to this function.",
                e.message
            )
        }
    }

}