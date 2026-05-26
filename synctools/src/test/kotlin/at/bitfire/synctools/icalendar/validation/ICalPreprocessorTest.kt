/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import at.bitfire.synctools.icalendar.requireDtStart
import com.google.common.io.CharStreams
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.TzId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.io.Writer
import java.time.temporal.Temporal
import java.util.UUID

class ICalPreprocessorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    val processor = ICalPreprocessor()

    @Test
    fun testApplyPreprocessors_appliesStreamProcessors() {
        val preprocessors = processor.streamPreprocessors
        assertTrue(preprocessors.isNotEmpty())
        processor.streamPreprocessors.forEach {
            mockkObject(it)
        }

        processor.applyPreprocessors("")

        // verify that the required stream processors have been called
        verify {
            processor.streamPreprocessors.forEach {
                it.repairLine(any())
            }
        }
    }

    @Test
    fun testPreprocessCalendar_MsTimeZones() {
        javaClass.getResourceAsStream("/events/outlook1.ics").use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val calendar = CalendarBuilder().build(reader)
            val vEvent = calendar.getComponent<VEvent>(Component.VEVENT).get()

            assertEquals(
                "W. Europe Standard Time",
                vEvent.requireDtStart<Temporal>().getRequiredParameter<TzId>(Parameter.TZID).value
            )
            processor.preprocessCalendar(calendar)
            assertEquals(
                "Europe/Vienna",
                vEvent.requireDtStart<Temporal>().getRequiredParameter<TzId>(Parameter.TZID).value
            )
        }
    }

    @Test
    fun testPreprocessStream_joinsLinesCorrectly() {
        val result = processor.preprocessStream(StringReader("BEGIN:VCALENDAR\nBEGIN:VEVENT")).readText()
        assertEquals("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\n", result)
    }

    @Test
    fun testPreprocessStream_runsApplyPreprocessors() {
        val processor = spyk<ICalPreprocessor>()

        // readText MUST be called. Otherwise the sequence is never evaluated
        // there must be at least one line. Otherwise the sequence is empty
        processor.preprocessStream(StringReader("\n")).use { it.readText() }

        // verify that applyPreprocessors has been called
        verify { processor.applyPreprocessors(any()) }
    }

    @Test
    fun testPreprocessStream_LargeFiles() {
        val preprocessor = ICalPreprocessor()
        val reader = VCalendarReaderGenerator(eventCount = 10_000)
        preprocessor.preprocessStream(reader).use { preprocessed ->
            // consume preprocessed stream
            val start = System.currentTimeMillis()
            CharStreams.copy(preprocessed, Writer.nullWriter())
            val end = System.currentTimeMillis()

            // no exception called
            System.err.println("testParse_SuperLargeFiles took ${(end - start) / 1000} seconds")
        }
    }


    /**
     * Reader that generates a number of VEVENTs for testing.
     */
    private class VCalendarReaderGenerator(val eventCount: Int) : Reader() {
        private var stage = 0 // 0 = header, 1 = events, 2 = footer, 3 = done
        private var eventIdx = 0
        private var current: String? = null
        private var pos = 0

        override fun reset() {
            stage = 0
            eventIdx = 0
            current = null
            pos = 0
        }

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            var charsRead = 0
            while (charsRead < len) {
                if (current == null || pos >= current!!.length) {
                    current = when (stage) {
                        0 -> {
                            stage = 1
                            """
                        BEGIN:VCALENDAR
                        PRODID:-//xyz Corp//NONSGML PDA Calendar Version 1.0//EN
                        VERSION:2.0
                        """.trimIndent() + "\n"
                        }
                        1 -> {
                            if (eventIdx < eventCount) {
                                val event = """
                                BEGIN:VEVENT
                                DTSTAMP:19960704T120000Z
                                UID:${UUID.randomUUID()}
                                ORGANIZER:mailto:jsmith@example.com
                                DTSTART:19960918T143000Z
                                DTEND:19960920T220000Z
                                STATUS:CONFIRMED
                                CATEGORIES:CONFERENCE
                                SUMMARY:Event $eventIdx
                                DESCRIPTION:Event $eventIdx description
                                END:VEVENT
                            """.trimIndent() + "\n"
                                eventIdx++
                                event
                            } else {
                                stage = 2
                                null
                            }
                        }
                        2 -> {
                            stage = 3
                            "END:VCALENDAR\n"
                        }
                        else -> return if (charsRead == 0) -1 else charsRead
                    }
                    pos = 0
                    if (current == null) continue // move to next stage
                }
                val charsLeft = current!!.length - pos
                val toRead = minOf(len - charsRead, charsLeft)
                current!!.toCharArray(pos, pos + toRead).copyInto(cbuf, off + charsRead)
                pos += toRead
                charsRead += toRead
            }
            return charsRead
        }

        override fun close() {
            // No resources to release
            current = null
        }
    }

}