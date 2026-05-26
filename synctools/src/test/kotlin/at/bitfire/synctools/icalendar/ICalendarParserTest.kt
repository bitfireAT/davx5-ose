/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.validation.ICalPreprocessor
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class ICalendarParserTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    lateinit var preprocessor: ICalPreprocessor

    @InjectMockKs
    lateinit var parser: ICalendarParser

    @Before
    fun setUp() {
        val reader = slot<Reader>()
        every { preprocessor.preprocessStream(capture(reader)) } answers { reader.captured }
    }


    @Test
    fun testParse_AppliesPreProcessing() {
        val reader = StringReader(
            "BEGIN:VCALENDAR\r\n" +
            "BEGIN:VEVENT\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        )
        val cal = parser.parse(reader)

        verify(exactly = 1) {
            // verify preprocessing was applied to stream
            preprocessor.preprocessStream(any())

            // verify preprocessing was applied to resulting calendar
            preprocessor.preprocessCalendar(cal)
        }
    }

    @Test
    fun testParse_SuppressesInvalidProperties() {
        val reader = StringReader(
            "BEGIN:VCALENDAR\r\n" +
                    "BEGIN:VEVENT\r\n" +
                    "DTSTAMP:invalid\r\n" +
                    "END:VEVENT\r\n" +
                    "END:VCALENDAR\r\n"
        )
        parser.parse(reader)
        // no exception called
    }

    @Test(expected = InvalidICalendarException::class)
    fun testParse_ThrowsExceptionOnInvalidInput() {
        val reader = StringReader("invalid")
        parser.parse(reader)
    }

}