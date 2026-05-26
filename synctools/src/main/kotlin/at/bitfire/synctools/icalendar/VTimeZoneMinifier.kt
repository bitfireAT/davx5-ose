/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.component.Daylight
import net.fortuna.ical4j.model.component.Observance
import net.fortuna.ical4j.model.component.Standard
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.validate.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

class VTimeZoneMinifier {

    private val logger
        get() = Logger.getLogger(VTimeZoneMinifier::class.java.name)

    /**
     * Minifies a VTIMEZONE so that only these observances are kept:
     *
     *   - the last STANDARD observance matching [startTemporal], and
     *   - the last DAYLIGHT observance matching [startTemporal], and
     *   - observances beginning after [startTemporal]
     *
     * Additionally, all properties other than observances and TZID are dropped.
     *
     * @param originalTz    time zone definition to minify
     * @param startTemporal start date for components (usually DTSTART); *null* if unknown
     * @return              minified time zone definition
     */
    fun minify(originalTz: VTimeZone, startTemporal: Temporal?): VTimeZone {
        // Make sure we have the earliest date available as ZonedDateTime.
        if (startTemporal == null)
            return originalTz
        val start = asZonedDateTime(
            startTemporal,
            zoneId = try {
                ZoneId.of(originalTz.timeZoneId.value)
            } catch (_: Exception) {
                ZoneId.systemDefault()
            }
        ) ?: return originalTz

        // list of observances that we want to keep (those at/after start)
        val keep = mutableSetOf<Observance>()

        // find latest matching STANDARD/DAYLIGHT observances
        var latestDaylight: Pair<Temporal, Observance>? = null
        var latestStandard: Pair<Temporal, Observance>? = null
        for (observance in originalTz.observances) {
            val latest = observance.getLatestOnset(start)

            if (latest == null)         // observance begins after "start", keep in any case
                keep += observance
            else
                when (observance) {
                    is Standard ->
                        if (latestStandard == null || TemporalAdapter.isAfter(latest, latestStandard.first))
                            latestStandard = Pair(latest, observance)
                    is Daylight ->
                        if (latestDaylight == null || TemporalAdapter.isAfter(latest, latestDaylight.first))
                            latestDaylight = Pair(latest, observance)
                }
        }

        // keep latest STANDARD observance
        latestStandard?.second?.let { keep += it }

        // Check latest DAYLIGHT for whether it can apply in the future. Otherwise, DST is not
        // used in this time zone anymore and the DAYLIGHT component can be dropped completely.
        latestDaylight?.second?.let { daylight ->
            // check whether start time is in DST
            if (latestStandard != null) {
                val latestStandardOnset = latestStandard.second.getLatestOnset(start)
                val latestDaylightOnset = daylight.getLatestOnset(start)
                if (latestStandardOnset != null && latestDaylightOnset != null && latestDaylightOnset > latestStandardOnset) {
                    // we're currently in DST
                    keep += daylight
                    return@let
                }
            }

            // Observance data is using LocalDateTime. Drop time zone information for comparisons.
            val startLocal = start.toLocalDateTime()

            // check RRULEs
            for (rRule in daylight.getProperties<RRule<Temporal>>(Property.RRULE)) {
                val nextDstOnset = rRule.recur.getNextDate(daylight.startDate.date, startLocal)
                if (nextDstOnset != null) {
                    // there will be a DST onset in the future -> keep DAYLIGHT
                    keep += daylight
                    return@let
                }
            }
            // no RRULE, check whether there's an RDATE in the future
            for (rDate in daylight.getProperties<RDate<Temporal>>(Property.RDATE)) {
                if (rDate.dates.any { !TemporalAdapter.isBefore(it, startLocal) }) {
                    // RDATE in the future
                    keep += daylight
                    return@let
                }
            }
        }

        // construct minified time zone that only contains the ID and relevant observances
        val relevantProperties = propertyListOf(originalTz.timeZoneId)
        val relevantObservances = ComponentList(keep.toList())
        val newTz = VTimeZone(relevantProperties, relevantObservances)

        // validate minified timezone
        try {
            newTz.validate()
        } catch (e: ValidationException) {
            // This should never happen!
            logger.log(Level.WARNING, "Minified timezone is invalid, using original one", e)
        }

        // use original time zone if we couldn't calculate a minified one
        return newTz
    }

    private fun asZonedDateTime(temporal: Temporal, zoneId: ZoneId = ZoneId.systemDefault()): ZonedDateTime? =
        when (temporal) {
            is LocalDate -> temporal.atStartOfDay().atZone(zoneId)
            is LocalDateTime -> temporal.atZone(zoneId)
            is OffsetDateTime -> temporal.atZoneSameInstant(zoneId)
            is Instant -> temporal.atZone(zoneId)
            is ZonedDateTime -> temporal
            else -> null
        }

}