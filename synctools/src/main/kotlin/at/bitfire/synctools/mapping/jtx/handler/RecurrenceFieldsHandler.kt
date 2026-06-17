/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.util.AndroidTimeUtils.isUtcTzId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.RecurrenceUtils
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads recurrence fields from a jtx Board [ContentValues] row and populates the given [CalendarComponent].
 *
 * Handles [JtxContract.JtxICalObject.RRULE], [JtxContract.JtxICalObject.RDATE], and
 * [JtxContract.JtxICalObject.EXDATE]. Unlike the Calendar provider, jtx providers store at most one RRULE
 * per object, so no RRULE separator logic is needed.
 *
 * **timezone format:** RDATE/EXDATE values are stored as timestamp lists with the timezone stored
 * separately in [JtxContract.JtxICalObject.DTSTART_TIMEZONE]. These timestamps are converted to the
 * appropriate temporal type before they are added to the iCalendar component.
 *
 * **UNTIL alignment:** RFC 5545 §3.3.10 requires the RRULE `UNTIL` value to be in UTC when
 * DTSTART is a DATE-TIME, and to be a bare DATE when DTSTART is a DATE. [RecurrenceUtils.alignUntil] enforces
 * this, and rules whose UNTIL falls on or before DTSTART are silently dropped.
 *
 * Parse errors in any individual field are caught and logged as warnings so that a single
 * malformed value never prevents the rest of the jtx object from being read.
 */
class RecurrenceFieldsHandler : JtxObjectEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Reads [JtxContract.JtxICalObject.RRULE], [JtxContract.JtxICalObject.RDATE], and
     * [JtxContract.JtxICalObject.EXDATE] from [from] and populates [to].
     *
     * @param from  provider row for a single jtx object
     * @param to    iCalendar component to populate
     */
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        val values = from.entityValues

        if (from === main) {
            processMain(values, to)
        } else {
            processException(values, to)
        }
    }

    private fun processException(values: ContentValues, to: CalendarComponent) {
        val recurId = values.getAsString(JtxContract.JtxICalObject.RECURID) ?: return

        val parameters = when (val timeZone = values.getAsString(JtxContract.JtxICalObject.RECURID_TIMEZONE)) {
            JtxContract.JtxICalObject.TZ_ALLDAY -> ParameterList(listOf(Value.DATE))
            null, "", ZoneOffset.UTC.id -> ParameterList()
            else -> if (isUtcTzId(timeZone))
                ParameterList()
            else
                ParameterList(listOf(TzId(timeZone)))
        }

        to += RecurrenceId<Temporal>(parameters, recurId)
    }

    private fun processMain(values: ContentValues, to: CalendarComponent) {
        // process RRULE field
        val rRule = rRule(values)

        // process RDATE field
        val rDate = rDate(values)
        val recurring = rRule != null || rDate != null

        // Check that we are recurring
        if (!recurring) {
            return
        }
        rRule?.let { to += it }
        rDate?.let { to += it }

        // process EXDATE field
        exDate(values)?.let { to += it }
    }

    private fun rRule(values: ContentValues): RRule<Temporal>? =
        values.getAsString(JtxContract.JtxICalObject.RRULE)?.let { rRule ->
            try {
                var rule = RRule<Temporal>(rRule)
                val startTemporal = values.getAsLong(JtxContract.JtxICalObject.DTSTART)
                    ?.let { timestamp ->
                        JtxTimeField(
                            timestamp = timestamp,
                            timeZone = values.getAsString(JtxContract.JtxICalObject.DTSTART_TIMEZONE)
                        ).toTemporal()
                    }

                // align RRULE UNTIL to DTSTART, if needed
                if (startTemporal != null) {
                    rule = RRule(RecurrenceUtils.alignUntil(rule.recur, startTemporal))

                    // skip if UNTIL is before object's DTSTART
                    val tsStart = values.getAsLong(JtxContract.JtxICalObject.DTSTART)!!
                    val tsUntil = rule.recur.until?.toTimestamp()
                    if (tsUntil != null && tsUntil <= tsStart) {
                        logger.warning("Ignoring $rule because UNTIL ($tsUntil) is not after DTSTART ($tsStart)")
                        return@let null
                    }
                }

                rule
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RRULE field, ignoring", e)
                null
            }
        }

    private fun rDate(values: ContentValues): RDate<*>? =
        values.getAsString(JtxContract.JtxICalObject.RDATE)?.let { rDate ->
            try {
                val timestamps = JtxContract.getLongListFromString(rDate)
                if (timestamps.isEmpty())
                    return@let null

                val timeZone = values.getAsString(JtxContract.JtxICalObject.DTSTART_TIMEZONE)
                val dates = timestamps.map {
                    JtxTimeField(
                        timestamp = it,
                        timeZone = timeZone
                    ).toTemporal()
                }
                val dateList = DateList(dates)

                RDate(dateListPropertyParameters(dates.first()), dateList)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RDATE field, ignoring", e)
                null
            }
        }

    private fun exDate(values: ContentValues): ExDate<*>? =
        values.getAsString(JtxContract.JtxICalObject.EXDATE)?.let { exDate ->
            try {
                val timestamps = JtxContract.getLongListFromString(exDate)
                if (timestamps.isEmpty())
                    return@let null

                val timeZone = values.getAsString(JtxContract.JtxICalObject.DTSTART_TIMEZONE)
                val dates = timestamps.map {
                    JtxTimeField(
                        timestamp = it,
                        timeZone = timeZone
                    ).toTemporal()
                }
                val dateList = DateList(dates)

                ExDate(dateListPropertyParameters(dates.first()), dateList)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse EXDATE field, ignoring", e)
                null
            }
        }

    private fun dateListPropertyParameters(firstDate: Temporal): ParameterList =
        when (firstDate) {
            is LocalDate ->
                ParameterList(listOf(Value.DATE))

            is ZonedDateTime ->
                ParameterList(listOf(TzId(firstDate.zone.id))) // implicit VALUE=DATE-TIME

            else ->
                ParameterList() // implicit VALUE=DATE-TIME
        }

}
