/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.builder

import at.techbee.jtx.JtxContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Logger

object TimeZoneIdMapper {
    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun Temporal.toTimeZoneId(): String? {
        return when (this) {
            is ZonedDateTime -> {
                this.zone.id
            }
            is Instant -> {
                ZoneOffset.UTC.id
            }
            is LocalDateTime -> {
                // Timezone unknown => floating time
                null
            }
            is LocalDate -> {
                // Without time, it is considered all-day
                JtxContract.JtxICalObject.TZ_ALLDAY
            }
            else -> {
                logger.warning("Ignoring unsupported temporal type: ${this::class}")
                null
            }
        }
    }
}
