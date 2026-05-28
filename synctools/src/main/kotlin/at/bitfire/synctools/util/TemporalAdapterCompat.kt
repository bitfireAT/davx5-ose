/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.util.AndroidTimeUtils.toInstant
import java.time.temporal.Temporal

object TemporalAdapterCompat {

    /**
     * Compatibility version of [net.fortuna.ical4j.model.TemporalAdapter.isBefore] that works around
     * https://github.com/ical4j/ical4j/issues/880.
     * Safe to use with any Temporal types including Instant and ZonedDateTime.
     */
    fun isBefore(a: Temporal, b: Temporal): Boolean {
        return a.toInstant() < b.toInstant()
    }

    /**
     * Compatibility version of [net.fortuna.ical4j.model.TemporalAdapter.isAfter] that works around
     * https://github.com/ical4j/ical4j/issues/880.
     * Safe to use with any Temporal types including Instant and ZonedDateTime.
     */
    fun isAfter(a: Temporal, b: Temporal): Boolean {
        return a.toInstant() > b.toInstant()
    }

}