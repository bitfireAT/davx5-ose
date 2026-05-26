/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.util.AndroidTimeUtils
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class StartTimeHandlerTest {

    private val tzVienna = ZoneId.of("Europe/Vienna")

    private val handler = StartTimeHandler()

    @Test
    fun `All-day event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697600000L,   // 21/06/2020
            Events.EVENT_TIMEZONE to AndroidTimeUtils.TZID_UTC
        ))
        handler.process(entity, entity, result)
        val localDate = LocalDate.of(2020, 6, 21)
        assertEquals(DtStart(localDate), result.dtStart<LocalDate>())
    }

    @Test
    fun `Non-all-day event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733600000L,   // 21/06/2020 12:00 +0200
            Events.EVENT_TIMEZONE to "Europe/Vienna"
        ))
        handler.process(entity, entity, result)
        val viennaDateTime = ZonedDateTime.of(2020, 6, 21, 12, 0, 0, 0, tzVienna)
        assertEquals(DtStart(viennaDateTime), result.dtStart<ZonedDateTime>())
    }

    @Test
    fun `Non-all-day event with UTC timezone`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733600000L,   // 21/06/2020 10:00 +0000
            Events.EVENT_TIMEZONE to "UTC"
        ))

        handler.process(entity, entity, result)

        assertEquals(DtStart(dateTimeValue("20200621T100000Z")), result.dtStart<Instant>())
    }

    @Test(expected = InvalidLocalResourceException::class)
    fun `No start time`() {
        val entity = Entity(ContentValues())
        handler.process(entity, entity, VEvent())
    }

}