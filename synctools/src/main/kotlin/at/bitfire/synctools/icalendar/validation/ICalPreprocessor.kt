/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import androidx.annotation.VisibleForTesting
import com.google.common.io.CharSource
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.transform.compliance.DateListPropertyRule
import net.fortuna.ical4j.transform.compliance.DatePropertyRule
import net.fortuna.ical4j.transform.compliance.Rfc5545PropertyRule
import java.io.BufferedReader
import java.io.Reader
import java.util.logging.Logger
import javax.annotation.WillNotClose

/**
 * Applies some rules to increase compatibility of parsed (incoming) iCalendars:
 *
 *   - [DatePropertyRule] and [DateListPropertyRule] to rename Outlook-specific TZID parameters
 * (like "W. Europe Standard Time" to an Android-friendly name like "Europe/Vienna")
 */
class ICalPreprocessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val propertyRules = arrayOf(
        DatePropertyRule(),         // These two rules also replace VTIMEZONEs of the iCalendar ...
        DateListPropertyRule()      // ... by the ical4j VTIMEZONE with the same TZID!
    )

    @VisibleForTesting
    internal val streamPreprocessors = arrayOf(
        FixInvalidUtcOffsetPreprocessor(),  // fix things like TZOFFSET(FROM,TO):+5730
        FixInvalidDayOffsetPreprocessor()   // fix things like DURATION:PT2D
    )

    /**
     * Applies [streamPreprocessors] to a given iCalendar [line].
     *
     * @param line original line (taken from an iCalendar)
     * @return the potentially repaired iCalendar line
     */
    @VisibleForTesting
    fun applyPreprocessors(line: String): String {
        var newLine = line
        for (preprocessor in streamPreprocessors)
            newLine = preprocessor.repairLine(newLine)
        return newLine
    }

    /**
     * Applies [streamPreprocessors] to a given [Reader] that reads an iCalendar object
     * in order to repair some things that must be fixed before parsing.
     *
     * The original reader content is processed line by line to avoid loading
     * the whole content into memory at once.
     *
     * This method works in a streaming way, so **[original] must not be closed before
     * the result of this method is consumed** like that:
     *
     * ~~~
     * someSource.reader().use { original ->
     *   val repaired = preprocessStream(original)
     *   // closing original here would render repaired unusable, too
     *   parse(repaired)
     * } // use will close original
     * ~~~
     *
     * @param original  original iCalendar object (must be closed by caller _after_ consuming the result of this method)
     * @return potentially repaired iCalendar object (doesn't need to be closed separately)
     */
    fun preprocessStream(@WillNotClose original: Reader): Reader {
        val repairedLines = BufferedReader(original)
            .lineSequence()
            .map { line ->      // BufferedReader provides line without line break
                val fixed = applyPreprocessors(line)
                CharSource.wrap(fixed + "\r\n")     // iCalendar uses CR+LF
            }
            .asIterable()
        return CharSource.concat(repairedLines).openStream()
    }


    /**
     * Applies the set of rules (see class definition) to a given calendar object.
     *
     * @param calendar the calendar object that is going to be modified
     */
    fun preprocessCalendar(calendar: Calendar) {
        for (component in calendar.componentList.all)
            for (property in component.propertyList.all)
                applyRules(property)
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyRules(property: Property) {
        propertyRules
            .filter { rule -> rule.supportedType.isAssignableFrom(property::class.java) }
            .forEach { rule ->
                val beforeStr = property.toString()
                (rule as Rfc5545PropertyRule<Property>).apply(property)
                val afterStr = property.toString()
                if (beforeStr != afterStr)
                    logger.info("${rule.javaClass.name}: $beforeStr -> $afterStr")
            }
    }

}