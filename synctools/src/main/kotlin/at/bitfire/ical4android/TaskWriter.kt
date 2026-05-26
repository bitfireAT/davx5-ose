/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.softValidate
import at.bitfire.ical4android.ICalendar.Companion.withUserAgents
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.icalendar.VTimeZoneMinifier
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Comment
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.io.Writer
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

/**
 * Writes a [Task] data class to a stream that contains an iCalendar
 * (VCALENDAR with VTODOs and optional VTIMEZONEs).
 *
 * @param prodId    PRODID to use in iCalendar, which identifies DAVx⁵
 */
class TaskWriter(
    private val prodId: ProdId
) {

    private val logger
        get() = Logger.getLogger(TaskWriter::class.java.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    /**
     * Generates an iCalendar from the provided Task.
     *
     * @param task  task to write
     * @param to    stream that the iCalendar is written to
     */
    fun write(task: Task, to: Writer): Unit = with(task) {
        val ical = Calendar()
        ical += ImmutableVersion.VERSION_2_0
        ical += prodId.withUserAgents(userAgents)

        val vTodo = VToDo(true /* generates DTSTAMP */)
        ical += vTodo

        uid?.let { vTodo += Uid(uid) }
        sequence?.let {
            if (it != 0)
                vTodo += Sequence(it)
        }

        createdAt?.let { vTodo += Created(Instant.ofEpochMilli(it)) }
        lastModified?.let { vTodo += LastModified(Instant.ofEpochMilli(it)) }

        summary?.let { vTodo += Summary(it) }
        location?.let { vTodo += Location(it) }
        geoPosition?.let { vTodo += it }
        description?.let { vTodo += Description(it) }
        color?.let { vTodo += Color(null, Css3Color.nearestMatch(it).name) }
        url?.let {
            try {
                vTodo += Url(URI(it))
            } catch (e: URISyntaxException) {
                logger.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
        organizer?.let { vTodo += it }

        if (priority != Priority.VALUE_UNDEFINED)
            vTodo += Priority(priority)
        classification?.let { vTodo += it }
        status?.let { vTodo += it }

        rRule?.let { vTodo += it }
        rDates.forEach { vTodo += it }
        exDates.forEach { vTodo += it }

        if (categories.isNotEmpty())
            vTodo += Categories(TextList(categories))
        comment?.let { vTodo += Comment(it) }
        vTodo.addAll<VToDo>(relatedTo as Collection<RelatedTo>)
        vTodo.addAll<VToDo>(unknownProperties)

        // remember used time zones
        val usedTimeZones = mutableSetOf<String>()
        due?.let { due ->
            vTodo += due
            due.getTzidOrNull()?.let(usedTimeZones::add)
        }
        duration?.let { vTodo += it }
        dtStart?.let { dtStart ->
            vTodo += dtStart
            dtStart.getTzidOrNull()?.let(usedTimeZones::add)
        }
        completedAt?.let { completedAt ->
            vTodo += completedAt
            completedAt.getTzidOrNull()?.let(usedTimeZones::add)
        }
        percentComplete?.let { vTodo += PercentComplete(it) }

        for (alarm in alarms) {
            vTodo.add<VToDo>(alarm)
        }

        // determine earliest referenced date
        val earliest = arrayOf(
            dtStart?.date,
            due?.date,
            completedAt?.date
        ).filterNotNull().minByOrNull { it.toTimestamp() }


        val timeZoneMinifier = VTimeZoneMinifier()
        // add VTIMEZONE components
        for (tz in usedTimeZones) {
            val vTimeZone = tzRegistry.getTimeZone(tz).vTimeZone
            val minifiedVTimeZone = timeZoneMinifier.minify(vTimeZone, earliest)
            ical += minifiedVTimeZone
        }

        softValidate(ical)
        CalendarOutputter(false).output(ical, to)
    }

    private fun DateProperty<*>.getTzidOrNull(): String? {
        return getParameter<TzId>(Parameter.TZID).getOrNull()?.value
    }
}