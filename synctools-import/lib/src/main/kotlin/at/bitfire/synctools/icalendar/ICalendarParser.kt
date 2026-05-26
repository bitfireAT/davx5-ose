/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.validation.ICalPreprocessor
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarParserFactory
import net.fortuna.ical4j.data.ContentHandlerContext
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import java.io.Reader
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.WillNotClose

/**
 * Custom iCalendar parser that applies error correction using [ICalPreprocessor].
 *
 * @param preprocessor  pre-processor to use
 */
class ICalendarParser(
    private val preprocessor: ICalPreprocessor = ICalPreprocessor()
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Parses the given iCalendar as lenient as possible and applies some error correction:
     *
     * 1. The input stream from is preprocessed with [ICalPreprocessor.preprocessStream].
     * 2. The parsed calendar is preprocessed with [ICalPreprocessor.preprocessCalendar].
     *
     * @param reader        where the iCalendar is read from
     *
     * @throws InvalidICalendarException   when the resource is can't be parsed
     */
    fun parse(@WillNotClose reader: Reader): Calendar {
        // preprocess stream to work around problems that prevent parsing and thus can't be fixed later
        val preprocessed = preprocessor.preprocessStream(reader)

        // parse stream, ignoring invalid properties (if possible)
        val calendar: Calendar
        try {
            calendar = CalendarBuilder(
                /* parser = */ CalendarParserFactory.getInstance().get(),
                /* contentHandlerContext = */ ContentHandlerContext().withSuppressInvalidProperties(true),
                /* tzRegistry = */ TimeZoneRegistryFactory.getInstance().createRegistry()
            ).build(preprocessed)
        } catch(e: ParserException) {
            throw InvalidICalendarException("Couldn't parse iCalendar", e)
        } catch(e: IllegalArgumentException) {
            throw InvalidICalendarException("iCalendar contains invalid value", e)
        }

        // Pre-process calendar for increased compatibility (fixes some common errors)
        try {
            preprocessor.preprocessCalendar(calendar)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't pre-process iCalendar", e)
        }

        return calendar
    }

}