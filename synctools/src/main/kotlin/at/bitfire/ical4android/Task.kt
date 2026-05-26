/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import androidx.annotation.IntRange
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.util.AndroidTimeUtils.toInstant
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Status
import java.time.Instant
import java.util.LinkedList

/**
 * Data class representing a task
 *
 * - as it is extracted from an iCalendar or
 * - as it should be generated into an iCalendar.
 */
data class Task(
    var createdAt: Long? = null,
    var lastModified: Long? = null,

    var summary: String? = null,
    var location: String? = null,
    var geoPosition: Geo? = null,
    var description: String? = null,
    var color: Int? = null,
    var url: String? = null,
    var organizer: Organizer? = null,

    @IntRange(from = 0, to = 9)
    var priority: Int = Priority.VALUE_UNDEFINED,

    var classification: Clazz? = null,
    var status: Status? = null,

    var dtStart: DtStart<*>? = null,
    var due: Due<*>? = null,
    var duration: Duration? = null,
    var completedAt: Completed? = null,

    @IntRange(from = 0, to = 100)
    var percentComplete: Int? = null,

    var rRule: RRule<*>? = null,
    val rDates: LinkedList<RDate<*>> = LinkedList(),
    val exDates: LinkedList<ExDate<*>> = LinkedList(),

    val categories: LinkedList<String> = LinkedList(),
    var comment: String? = null,
    var relatedTo: LinkedList<RelatedTo> = LinkedList(),
    val unknownProperties: LinkedList<Property> = LinkedList(),

    val alarms: LinkedList<VAlarm> = LinkedList(),
) : ICalendar() {

    fun isAllDay(): Boolean {
        return dtStart?.let { DateUtils.isDate(it) }
            ?: due?.let { DateUtils.isDate(it) }
            ?: true
    }

    /**
     * The "end date" of this task.
     *
     * Returns…
     * - [due] if present, otherwise…
     * - [Due] instance containing the end date as [Instant] calculated from [dtStart] and
     *   [duration] if both present, otherwise…
     * - `null`.
     */
    val end: Due<*>?
        get() {
            if (due != null) {
                return due
            }

            val start = dtStart?.date?.toInstant()
            val duration = duration?.duration
            if (start != null && duration != null) {
                val end = start + duration
                return Due(end)
            }

            return null
        }
}
