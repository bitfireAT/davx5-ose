/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import at.bitfire.synctools.util.TimeApiExtensions.toLocalDate
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import java.time.Instant
import java.time.temporal.Temporal

/**
 * Converts a task timestamp (epoch milliseconds) together with its timezone and all-day flag
 * into the appropriate [Temporal] type for use in iCalendar properties.
 *
 * Analogous to [at.bitfire.synctools.mapping.calendar.handler.AndroidTimeField] for calendar events.
 *
 * @param timestamp  epoch milliseconds (value of [org.dmfs.tasks.contract.TaskContract.Tasks.DTSTART] or [org.dmfs.tasks.contract.TaskContract.Tasks.DUE])
 * @param tzId       value of [org.dmfs.tasks.contract.TaskContract.Tasks.TZ]:
 *                   `null` for all-day tasks storage; timezone ID (e.g. `"UTC"`, `"Europe/Berlin"`)
 *                   for non-all-day tasks.
 * @param allDay     whether [org.dmfs.tasks.contract.TaskContract.Tasks.IS_ALLDAY] is non-zero
 */
class TaskTimeField(
    private val timestamp: Long,
    private val tzId: String?,
    private val allDay: Boolean,
) {

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    /**
     * Converts the stored timestamp to the correct [Temporal] representation:
     * - `allDay = true`  → [java.time.LocalDate] (interpreted at UTC midnight)
     * - `allDay = false`, no/unknown timezone → [Instant] (UTC)
     * - `allDay = false`, known timezone → [java.time.ZonedDateTime]
     */
    fun toTemporal(): Temporal {
        val instant = Instant.ofEpochMilli(timestamp)

        if (allDay)
            return instant.toLocalDate()

        val tz = tzId?.let { tzRegistry.getTimeZone(it) }

        return if (tz == null)
            instant
        else
            instant.atZone(tz.toZoneId())
    }

}
