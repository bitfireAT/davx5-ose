/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.util.AndroidTimeUtils.toInstant
import java.time.temporal.Temporal

/**
 * Workaround for https://github.com/ical4j/ical4j/issues/880. Should be removed
 * as soon as the used ical4j version doesn't have that problem anymore.
 */
object TemporalAdapterWorkaround {

    /**
     * Compatibility version of [net.fortuna.ical4j.model.TemporalAdapter.isBefore] that works around
     * https://github.com/ical4j/ical4j/issues/880.
     *
     * Safe to use with any Temporal type that is supported by [toInstant].
     */
    fun isBefore(a: Temporal, b: Temporal): Boolean =
        a.toInstant() < b.toInstant()

    /**
     * Compatibility version of [net.fortuna.ical4j.model.TemporalAdapter.isAfter] that works around
     * https://github.com/ical4j/ical4j/issues/880.
     *
     * Safe to use with any Temporal type that is supported by [toInstant].
     */
    fun isAfter(a: Temporal, b: Temporal): Boolean =
        a.toInstant() > b.toInstant()

}