/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.property.DateProperty
import java.time.temporal.Temporal

object DateUtils {

    fun isDate(date: DateProperty<*>?): Boolean =
        date != null && !TemporalAdapter.isDateTimePrecision(date.date)

    fun isDateTime(date: DateProperty<*>?): Boolean =
        date != null && TemporalAdapter.isDateTimePrecision(date.date)

    fun isDate(date: Temporal?): Boolean =
        date != null && !TemporalAdapter.isDateTimePrecision(date)

    fun isDateTime(date: Temporal?): Boolean =
        date != null && TemporalAdapter.isDateTimePrecision(date)

}
