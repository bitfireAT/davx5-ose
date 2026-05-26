/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.exception

/**
 * Represents an invalid iCalendar resource.
 */
class InvalidICalendarException: InvalidResourceException {

    constructor(message: String): super(message)
    constructor(message: String, ex: Throwable): super(message, ex)

}