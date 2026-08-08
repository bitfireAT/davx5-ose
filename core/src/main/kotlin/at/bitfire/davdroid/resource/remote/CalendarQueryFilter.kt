/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import java.time.Instant

/**
 * Restricts the members returned by [CalDavCollection.listFilteredMembers], translated into a
 * `calendar-query` REPORT (RFC 4791 7.8) — one REPORT per entry of [components], concatenated.
 */
data class CalendarQueryFilter(
    /** iCalendar component names to query (for instance `VEVENT`, `VTODO`) */
    val components: List<String>,

    /** if given, only components ending after this instant are returned */
    val timeRangeStart: Instant? = null,

    /** if given, only components starting before this instant are returned */
    val timeRangeEnd: Instant? = null
)
