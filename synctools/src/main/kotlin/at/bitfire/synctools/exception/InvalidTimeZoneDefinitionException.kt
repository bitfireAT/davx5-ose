/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.exception

/**
 * Thrown when an iCalendar property responds with an unknown timezone.
 * For example: `DTSTART` with `TZID=Unknown/Timezone` and no matching `VTIMEZONE` definition in the calendar.
 */
class InvalidTimeZoneDefinitionException: InvalidResourceException {

    constructor(message: String): super(message)
    constructor(message: String, ex: Throwable): super(message, ex)

}
