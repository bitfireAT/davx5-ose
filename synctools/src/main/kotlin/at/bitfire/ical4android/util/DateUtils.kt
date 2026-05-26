/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.property.DateProperty
import java.time.temporal.Temporal

/**
 * Date/time utilities
 *
 * Before this object is accessed the first time, the accessing thread's contextClassLoader
 * must be set to an Android Context.classLoader!
 */
object DateUtils {

    // time zones

    /**
     * Determines whether a given date represents a DATE value.
     * @param date date property to check
     * @return *true* if the date is a DATE value; *false* otherwise (for instance, when the argument is a DATE-TIME value or null)
     */
    fun isDate(date: DateProperty<*>?): Boolean =
        date != null && !TemporalAdapter.isDateTimePrecision(date.date)

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE-TIME value; *false* otherwise (for instance, when the argument is a DATE value or null)
     */
    fun isDateTime(date: DateProperty<*>?): Boolean =
        date != null && TemporalAdapter.isDateTimePrecision(date.date)

    /**
     * Determines whether a given [Temporal] represents a DATE value.
     */
    fun isDate(date: Temporal?): Boolean =
        date != null && !TemporalAdapter.isDateTimePrecision(date)

    /**
     * Determines whether a given [Temporal] represents a DATE-TIME value.
     */
    fun isDateTime(date: Temporal?): Boolean =
        date != null && TemporalAdapter.isDateTimePrecision(date)

}