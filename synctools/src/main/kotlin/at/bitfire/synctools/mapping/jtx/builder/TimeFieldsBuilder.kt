/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.icalendar.due
import at.bitfire.synctools.mapping.jtx.builder.TimeZoneIdMapper.toTimeZoneId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.TZ_ALLDAY
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import java.time.temporal.Temporal
import java.util.logging.Logger

/**
 * Handles the iCalendar properties: DTSTART, DTEND, DUE, DURATION
 */
class TimeFieldsBuilder : JtxObjectEntityBuilder {
    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        if (from is VJournal) {
            buildJournal(from, to)
        } else if (from is VToDo) {
            buildTask(from, to)
        }
    }

    private fun buildJournal(from: VJournal, to: Entity) {
        buildStartDate(from, to)

        ignoreDtEnd(from, to)
        ignoreDue(from, to)
        ignoreDuration(from, to)
    }

    private fun buildTask(from: VToDo, to: Entity) {
        val startTimeZoneId = buildStartDate(from, to)
        val dueTimeZoneId = buildDueDate(from, to)
        cleanUpTimeZones(startTimeZoneId, dueTimeZoneId, to)

        warnIfDueDateAfterStartDate(from)

        buildDuration(from, to)

        ignoreDtEnd(from, to)
    }

    private fun buildStartDate(from: CalendarComponent, to: Entity): String? {
        val start = from.dtStart<Temporal>()?.normalizedDate()
        val timeZoneId = start?.toTimeZoneId()
        if (start != null) {
            to.entityValues.put(JtxContract.JtxICalObject.DTSTART, start.toTimestamp())
            to.entityValues.put(JtxContract.JtxICalObject.DTSTART_TIMEZONE, timeZoneId)
        } else {
            to.entityValues.putNull(JtxContract.JtxICalObject.DTSTART)
            to.entityValues.putNull(JtxContract.JtxICalObject.DTSTART_TIMEZONE)
        }

        return timeZoneId
    }

    private fun buildDueDate(from: CalendarComponent, to: Entity): String? {
        val due = from.due<Temporal>()?.normalizedDate()
        val timeZoneId = due?.toTimeZoneId()
        if (due != null) {
            to.entityValues.put(JtxContract.JtxICalObject.DUE, due.toTimestamp())
            to.entityValues.put(JtxContract.JtxICalObject.DUE_TIMEZONE, timeZoneId)
        } else {
            to.entityValues.putNull(JtxContract.JtxICalObject.DUE)
            to.entityValues.putNull(JtxContract.JtxICalObject.DUE_TIMEZONE)
        }

        return timeZoneId
    }

    private fun buildDuration(task: VToDo, to: Entity) {
        val durationValue = task.duration?.value

        if (durationValue != null && task.dtStart<Temporal>()?.date == null) {
            logger.warning("Found DURATION without DTSTART in VTODO; ignoring DURATION")
            to.entityValues.putNull(JtxContract.JtxICalObject.DURATION)
        } else if (durationValue != null && task.due<Temporal>()?.date != null) {
            logger.warning("Found DURATION and DUE in VTODO; ignoring DURATION")
            to.entityValues.putNull(JtxContract.JtxICalObject.DURATION)
        } else if (durationValue != null) {
            to.entityValues.put(JtxContract.JtxICalObject.DURATION, durationValue)
        } else {
            to.entityValues.putNull(JtxContract.JtxICalObject.DURATION)
        }
    }

    private fun cleanUpTimeZones(startTimeZoneId: String?, dueTimeZoneId: String?, to: Entity) {
        if (startTimeZoneId == TZ_ALLDAY && dueTimeZoneId != null && dueTimeZoneId != TZ_ALLDAY) {
            logger.warning("DTSTART is DATE but DUE is DATE-TIME, rewriting DTSTART to DATE-TIME")
            to.entityValues.put(JtxContract.JtxICalObject.DTSTART_TIMEZONE, dueTimeZoneId)
        } else if (dueTimeZoneId == TZ_ALLDAY && startTimeZoneId != null && startTimeZoneId != TZ_ALLDAY) {
            logger.warning("DTSTART is DATE-TIME but DUE is DATE, rewriting DUE to DATE-TIME")
            to.entityValues.put(JtxContract.JtxICalObject.DUE_TIMEZONE, startTimeZoneId)
        }
    }

    private fun warnIfDueDateAfterStartDate(task: VToDo) {
        val start = task.dtStart<Temporal>()?.normalizedDate()?.toTimestamp()
        val due = task.due<Temporal>()?.normalizedDate()?.toTimestamp()

        // Previously DUE was dropped. Now reduced to a warning.
        // See also: https://github.com/bitfireAT/ical4android/issues/70
        if (start != null && due != null && due < start) {
            logger.warning("Found invalid DUE < DTSTART")
        }
    }

    private fun ignoreDtEnd(from: CalendarComponent, to: Entity) {
        if (from.hasProperty(Property.DTEND)) {
            logger.warning("DTEND must not be used with VJOURNAL, ignoring property.")
        }

        to.entityValues.putNull(JtxContract.JtxICalObject.DTEND)
        to.entityValues.putNull(JtxContract.JtxICalObject.DTEND_TIMEZONE)
    }

    private fun ignoreDue(from: CalendarComponent, to: Entity) {
        if (from.hasProperty(Property.DUE)) {
            logger.warning("DUE must not be used with VJOURNAL, ignoring property.")
        }

        to.entityValues.putNull(JtxContract.JtxICalObject.DUE)
        to.entityValues.putNull(JtxContract.JtxICalObject.DUE_TIMEZONE)
    }

    private fun ignoreDuration(from: CalendarComponent, to: Entity) {
        if (from.hasProperty(Property.DURATION)) {
            logger.warning("DURATION must not be used with VJOURNAL, ignoring property.")
        }

        to.entityValues.putNull(JtxContract.JtxICalObject.DURATION)
    }
}

private fun CalendarComponent.hasProperty(name: String) = getProperty<Property>(name).isPresent
