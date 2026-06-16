/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire

import net.fortuna.ical4j.model.CalendarDateFormat
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.TimeZone
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal

fun dateTimeValue(value: String): Temporal {
    return if (value.endsWith("Z")) {
        TemporalAdapter.parse<Temporal>(value, CalendarDateFormat.UTC_DATE_TIME_FORMAT).temporal
    } else {
        TemporalAdapter.parse<Temporal>(value, CalendarDateFormat.FLOATING_DATE_TIME_FORMAT).temporal
    }
}

fun dateTimeValue(value: String, timeZone: TimeZone): ZonedDateTime {
    return dateTimeValue(value, timeZone.toZoneId())
}

fun dateTimeValue(value: String, zone: ZoneId): ZonedDateTime {
    val temporal = dateTimeValue(value)
    return if (temporal is LocalDateTime) {
        temporal.atZone(zone)
    } else {
        error("Unexpected temporal type: ${temporal::class}")
    }
}

fun dateValue(value: String): LocalDate {
    return TemporalAdapter.parse<LocalDate>(value, CalendarDateFormat.DATE_FORMAT).temporal
}

fun parameterListOf(vararg parameters: Parameter): ParameterList {
    return ParameterList(parameters.toList())
}
