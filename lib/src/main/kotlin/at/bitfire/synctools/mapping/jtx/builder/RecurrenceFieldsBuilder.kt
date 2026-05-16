/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDates
import at.bitfire.synctools.icalendar.recurrenceId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.RRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Logger

class RecurrenceFieldsBuilder : JtxEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        if (from === main) {
            buildMainItem(main, to)
        } else {
            buildExceptionItem(from, to)
        }
    }

    private fun buildMainItem(main: CalendarComponent, to: Entity) {
        to.entityValues.putNull(JtxContract.JtxICalObject.RECURID)
        to.entityValues.putNull(JtxContract.JtxICalObject.RECURID_TIMEZONE)

        buildRRule(main, to)
        buildRDate(main, to)
        buildExDate(main, to)
    }

    private fun buildRRule(main: CalendarComponent, to: Entity) {
        val rRules = main.getProperties<RRule<*>>(Property.RRULE)
        if (rRules.isEmpty()) {
            to.entityValues.putNull(JtxContract.JtxICalObject.RRULE)
            return
        }

        // Note: All but the last RRULE property are ignored.
        val rrule = rRules.last().value
        to.entityValues.put(JtxContract.JtxICalObject.RRULE, rrule)
    }

    private fun buildRDate(main: CalendarComponent, to: Entity) {
        // Note: RDATE properties with a PERIOD value are currently not supported and ignored.
        buildDateList(main, to, Property.RDATE, JtxContract.JtxICalObject.RDATE)
    }

    private fun buildExDate(main: CalendarComponent, to: Entity) {
        buildDateList(main, to, Property.EXDATE, JtxContract.JtxICalObject.EXDATE)
    }

    private fun buildDateList(
        main: CalendarComponent,
        to: Entity,
        propertyName: String,
        columnName: String
    ) {
        val dateListProperties = main.getProperties<DateListProperty<*>>(propertyName)
        if (dateListProperties.isEmpty()) {
            to.entityValues.putNull(columnName)
            return
        }

        val timestampListString = dateListProperties
            .flatMap { it.normalizedDates() }
            .map { it.toTimestamp() }
            .joinToString(separator = ",")
            .takeIf { it.isNotEmpty() }

        if (timestampListString == null) {
            to.entityValues.putNull(columnName)
        } else {
            to.entityValues.put(columnName, timestampListString)
        }
    }

    private fun buildExceptionItem(exception: CalendarComponent, to: Entity) {
        buildRecurrenceId(exception, to)

        to.entityValues.putNull(JtxContract.JtxICalObject.RRULE)
        to.entityValues.putNull(JtxContract.JtxICalObject.RDATE)
        to.entityValues.putNull(JtxContract.JtxICalObject.EXDATE)
    }

    private fun buildRecurrenceId(exception: CalendarComponent, to: Entity) {
        val recurrenceId = exception.recurrenceId
        if (recurrenceId == null) {
            to.entityValues.putNull(JtxContract.JtxICalObject.RECURID)
            to.entityValues.putNull(JtxContract.JtxICalObject.RECURID_TIMEZONE)
            return
        }

        to.entityValues.put(JtxContract.JtxICalObject.RECURID, recurrenceId.value)
        val timeZoneId = recurrenceId.normalizedDate().getTimeZoneId()
        to.entityValues.put(JtxContract.JtxICalObject.RECURID_TIMEZONE, timeZoneId)
    }

    private fun Temporal.getTimeZoneId(): String? = when (this) {
        is ZonedDateTime -> {
            this.zone.id
        }
        is Instant -> {
            ZoneOffset.UTC.id
        }
        is LocalDateTime -> {
            // Timezone unknown => floating time
            null
        }
        is LocalDate -> {
            // Without time, it is considered all-day
            JtxContract.JtxICalObject.TZ_ALLDAY
        }
        else -> {
            logger.warning("Ignoring unsupported temporal type: ${this::class}")
            null
        }
    }
}
