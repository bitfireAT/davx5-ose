/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDates
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.mapping.calendar.builder.AndroidRecurrenceMapper.androidRecurrenceRuleString
import at.bitfire.synctools.mapping.calendar.builder.AndroidRecurrenceMapper.androidRecurrenceDatesString
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.temporal.Temporal
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrDefault

class RecurrenceFieldsBuilder: AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        val rRules = from.getProperties<RRule<*>>(Property.RRULE)
        val rDates = from.getProperties<RDate<*>>(Property.RDATE)
        val recurring = rRules.isNotEmpty() || rDates.isNotEmpty()
        if (recurring && from === main) {
            // generate recurrence fields only for recurring main events
            val startDate = from.requireDtStart<Temporal>().normalizedDate()

            // RRULE
            if (rRules.isNotEmpty())
                values.put(Events.RRULE, androidRecurrenceRuleString(rRules))
            else
                values.putNull(Events.RRULE)

            // RDATE (start with null value)
            if (rDates.isNotEmpty()) {
                // ignore RDATEs when there's also an infinite RRULE [https://issuetracker.google.com/issues/216374004]
                val infiniteRrule = rRules.any { rRule ->
                    rRule.recur.count == -1 &&  // no COUNT AND
                    rRule.recur.until == null   // no UNTIL
                }

                if (infiniteRrule) {
                    logger.warning("Android can't handle infinite RRULE + RDATE [https://issuetracker.google.com/issues/216374004]; ignoring RDATE(s)")
                    values.putNull(Events.RDATE)

                } else {
                    val normalizedRDates = rDates.flatMap { rDate ->
                        if (rDate.periods.getOrDefault(emptySet()).isNotEmpty()) {
                            logger.warning("RDATE PERIOD not supported, ignoring")
                            emptyList()
                        } else {
                            rDate.normalizedDates()
                        }
                    }

                    if (normalizedRDates.isNotEmpty()) {
                        // Calendar provider drops DTSTART instance when using RDATE [https://code.google.com/p/android/issues/detail?id=171292]
                        val listWithDtStart = listOf(startDate) + normalizedRDates
                        val recurrenceDates = androidRecurrenceDatesString(listWithDtStart, startDate)

                        values.put(Events.RDATE, recurrenceDates)
                    } else {
                        values.putNull(Events.RDATE)
                    }
                }
            } else {
                values.putNull(Events.RDATE)
            }

            // EXRULE
            val exRules = from.getProperties<ExRule<*>>(Property.EXRULE)
            if (exRules.isNotEmpty())
                values.put(Events.EXRULE, androidRecurrenceRuleString(exRules))
            else
                values.putNull(Events.EXRULE)

            // EXDATE
            val exDates = from.getProperties<ExDate<Temporal>>(Property.EXDATE)
            if (exDates.isNotEmpty()) {
                val normalizedExDates = exDates.flatMap { exDate -> exDate.normalizedDates() }
                values.put(Events.EXDATE, androidRecurrenceDatesString(normalizedExDates, startDate))
            } else
                values.putNull(Events.EXDATE)

        } else {
            values.putNull(Events.RRULE)
            values.putNull(Events.EXRULE)
            values.putNull(Events.RDATE)
            values.putNull(Events.EXDATE)
        }
    }

}