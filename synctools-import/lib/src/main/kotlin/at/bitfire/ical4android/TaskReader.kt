/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Comment
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import java.io.IOException
import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Logger

/**
 * Generates a single or list of [Task] from an iCalendar in a [Reader] source.
 */
class TaskReader {

    private val logger
        get() = Logger.getLogger(TaskReader::class.java.name)

    /**
     * Parses an iCalendar resource and extracts the VTODOs.
     *
     * @param reader where the iCalendar is taken from
     *
     * @return list of filled [Task] data objects (may have size 0)
     *
     * @throws InvalidICalendarException when the iCalendar can't be parsed
     * @throws IOException on I/O errors
     */
    fun readTasks(reader: Reader): List<Task> {
        val ical = ICalendarParser().parse(reader)
        val vToDos = ical.getComponents<VToDo>(Component.VTODO)
        return vToDos.map { fromVToDo(it) }
    }

    private fun fromVToDo(todo: VToDo): Task {
        val t = Task()

        if (todo.uid.isPresent)
            t.uid = todo.uid.get().value
        else {
            logger.warning("Received VTODO without UID, generating new one")
            t.generateUID()
        }

        // sequence must only be null for locally created, not-yet-synchronized events
        t.sequence = 0

        for (prop in todo.propertyList.all)
            when (prop) {
                is Sequence -> t.sequence = prop.sequenceNo
                is Created -> t.createdAt = prop.date.toTimestamp()
                is LastModified -> t.lastModified = prop.date.toTimestamp()
                is Summary -> t.summary = prop.value
                is Location -> t.location = prop.value
                is Geo -> t.geoPosition = prop
                is Description -> t.description = prop.value
                is Color -> t.color = Css3Color.fromString(prop.value)?.argb
                is Url -> t.url = prop.value
                is Organizer -> t.organizer = prop
                is Priority -> t.priority = prop.level
                is Clazz -> t.classification = prop
                is Status -> t.status = prop
                is Due<*> -> { t.due = prop }
                is Duration -> t.duration = prop
                is DtStart<*> -> { t.dtStart = prop }
                is Completed -> { t.completedAt = prop }
                is PercentComplete -> t.percentComplete = prop.percentage
                is RRule<*> -> t.rRule = prop
                is RDate<*> -> t.rDates += prop
                is ExDate<*> -> t.exDates += prop
                is Categories -> t.categories.addAll(prop.categories.texts)
                is Comment -> t.comment = prop.value
                is RelatedTo -> t.relatedTo.add(prop)
                is Uid, is ProdId, is DtStamp -> { /* don't save these as unknown properties */ }
                else -> t.unknownProperties += prop
            }

        t.alarms.addAll(todo.alarms)

        // There seem to be many invalid tasks out there because of some defect clients, do some validation.
        val startDate = t.dtStart?.normalizedDate()
        val dueDate = t.due?.normalizedDate()

        if (startDate != null && dueDate != null) {
            if (startDate is LocalDate && DateUtils.isDateTime(dueDate)) {
                logger.warning("DTSTART is DATE but DUE is DATE-TIME, rewriting DTSTART to DATE-TIME")
                t.dtStart = DtStart(startDate.toDateTime(dueDate))
            } else if (DateUtils.isDateTime(startDate) && dueDate is LocalDate) {
                logger.warning("DTSTART is DATE-TIME but DUE is DATE, rewriting DUE to DATE-TIME")
                t.due = Due(dueDate.toDateTime(startDate))
            }

            val newStartDate = t.dtStart!!.date
            val newDueDate = t.due!!.date
            if (TemporalAdapter.isAfter(newStartDate, newDueDate)) {
                logger.warning("Found invalid DUE <= DTSTART; dropping DTSTART")
                t.dtStart = null
            }
        }

        if (t.duration != null && t.dtStart == null) {
            logger.warning("Found DURATION without DTSTART; ignoring")
            t.duration = null
        }

        return t
    }

    /**
     * Converts this [LocalDate] to a date-time [Temporal] of the same type as `referenceDateTime`.
     */
    private fun LocalDate.toDateTime(referenceDateTime: Temporal): Temporal {
        return when (referenceDateTime) {
            is LocalDateTime -> atStartOfDay()
            is ZonedDateTime -> atStartOfDay(referenceDateTime.zone)
            is OffsetDateTime -> OffsetDateTime.of(atStartOfDay(), referenceDateTime.offset)
            is Instant -> atStartOfDay(ZoneOffset.UTC).toInstant()
            else -> error("Unsupported Temporal type: ${this::class.qualifiedName}")
        }
    }

}