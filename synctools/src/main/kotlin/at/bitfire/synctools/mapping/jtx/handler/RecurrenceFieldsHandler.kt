/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.TZ_ALLDAY
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Handler for recurrence-related fields of a jtx Board content provider data row.
 */
class RecurrenceFieldsHandler : JtxFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        if (from === main) {
            processMain(from, to)
        } else {
            processException(from, to)
        }
    }

    private fun processMain(from: Entity, to: CalendarComponent) {
        val values = from.entityValues
        val dtstartTimezone = values.getAsString(JtxContract.JtxICalObject.DTSTART_TIMEZONE)

        values.getAsString(JtxContract.JtxICalObject.RRULE)?.takeIf { it.isNotBlank() }?.let { rruleString ->
            try {
                to += RRule<Temporal>(rruleString)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RRULE, ignoring", e)
            }
        }

        values.getAsString(JtxContract.JtxICalObject.RDATE)?.takeIf { it.isNotBlank() }?.let { rdateString ->
            try {
                val timestamps = JtxContract.getLongListFromString(rdateString)
                if (timestamps.isNotEmpty()) {
                    to += timestampsToProperty(timestamps, dtstartTimezone) { RDate(it) }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RDATE, ignoring", e)
            }
        }

        values.getAsString(JtxContract.JtxICalObject.EXDATE)?.takeIf { it.isNotBlank() }?.let { exdateString ->
            try {
                val timestamps = JtxContract.getLongListFromString(exdateString)
                if (timestamps.isNotEmpty()) {
                    to += timestampsToProperty(timestamps, dtstartTimezone) { ExDate(it) }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse EXDATE, ignoring", e)
            }
        }
    }

    private fun processException(from: Entity, to: CalendarComponent) {
        val values = from.entityValues
        val recurid = values.getAsString(JtxContract.JtxICalObject.RECURID) ?: return
        val recuridTimezone = values.getAsString(JtxContract.JtxICalObject.RECURID_TIMEZONE)

        try {
            to += if (recuridTimezone == TZ_ALLDAY || recuridTimezone.isNullOrEmpty() || recuridTimezone == ZoneOffset.UTC.id) {
                RecurrenceId<Temporal>(recurid)
            } else {
                RecurrenceId<Temporal>(ParameterList(listOf(TzId(recuridTimezone))), recurid)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse RECURID, ignoring", e)
        }
    }

    private fun <T: DateListProperty<*>> timestampsToProperty(
        timestamps: List<Long>,
        dtstartTimezone: String?,
        factory: (DateList<*>) -> T
    ): T {
        val dateList = timestampsToDateList(timestamps, dtstartTimezone)
        val property = factory(dateList)
        when (val first = dateList.dates.firstOrNull()) {
            is ZonedDateTime -> property.add<T>(TzId(first.zone.id))
            is LocalDate -> property.add<T>(Value.DATE)
        }
        return property
    }

    private fun timestampsToDateList(timestamps: List<Long>, dtstartTimezone: String?): DateList<*> =
        when {
            dtstartTimezone == TZ_ALLDAY ->
                DateList(timestamps.map {
                    LocalDate.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
                })
            dtstartTimezone == ZoneOffset.UTC.id ->
                DateList(timestamps.map {
                    Instant.ofEpochMilli(it)
                })
            dtstartTimezone.isNullOrEmpty() ->
                DateList(timestamps.map {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                })
            else -> {
                val zone = try {
                    ZoneId.of(dtstartTimezone)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Invalid DTSTART_TIMEZONE '$dtstartTimezone', falling back to UTC", e)
                    null
                }
                if (zone != null) {
                    DateList(timestamps.map {
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone)
                    })
                } else {
                    DateList(timestamps.map {
                        Instant.ofEpochMilli(it)
                    })
                }
            }
        }
}
