/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import at.bitfire.synctools.util.AndroidTimeUtils.isUtcTzId
import at.techbee.jtx.JtxContract
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal

/**
 * Converts a jtx timestamp and timezone column pair into the matching iCalendar [Temporal] type.
 *
 * @param timestamp  epoch milliseconds from a jtx time column
 * @param timeZone   value of the matching jtx timezone column:
 *                   [JtxContract.JtxICalObject.TZ_ALLDAY] for DATE values,
 *                   `null` or empty for floating DATE-TIME values,
 *                   or a timezone ID for DATE-TIME values with timezone
 */
internal class JtxTimeField(
    private val timestamp: Long,
    private val timeZone: String?
) {

    /**
     * Converts the stored value according to jtx timezone semantics.
     *
     * Invalid timezone IDs are interpreted as UTC, as documented by [JtxContract.JtxICalObject.DTSTART_TIMEZONE].
     */
    fun toTemporal(): Temporal {
        val instant = Instant.ofEpochMilli(timestamp)

        return when {
            timeZone == JtxContract.JtxICalObject.TZ_ALLDAY ->
                LocalDate.ofInstant(instant, ZoneOffset.UTC)

            timeZone == ZoneOffset.UTC.id || isUtcTzId(timeZone) ->
                instant

            timeZone.isNullOrEmpty() ->
                LocalDateTime.ofInstant(instant, ZoneId.systemDefault())

            else ->
                zoneIdOrNull(timeZone)
                    ?.let { zoneId -> ZonedDateTime.ofInstant(instant, zoneId) }
                    ?: instant
        }
    }

    private fun zoneIdOrNull(tzId: String): ZoneId? =
        try {
            ZoneId.of(tzId)
        } catch (_: DateTimeException) {
            null
        }

}
