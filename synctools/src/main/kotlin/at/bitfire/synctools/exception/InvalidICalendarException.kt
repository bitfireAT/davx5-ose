/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.exception

/**
 * Represents an invalid iCalendar resource.
 */
class InvalidICalendarException: InvalidResourceException {

    constructor(message: String): super(message)
    constructor(message: String, ex: Throwable): super(message, ex)

}