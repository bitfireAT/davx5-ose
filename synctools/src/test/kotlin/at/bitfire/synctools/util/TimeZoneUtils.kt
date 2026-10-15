/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry

/**
 * Tries to get the time zone with id [tzId] from the registry.
 * If it's not found, throw an exception.
 * 
 * @param tzId The id of the timezone to search for.
 *
 * @throws IllegalStateException If the timezone was not found.
 */
fun TimeZoneRegistry.getTimeZoneOrThrow(tzId: String): TimeZone {
    return getTimeZone(tzId) ?: error("TimeZone $tzId not found in registry")
}
