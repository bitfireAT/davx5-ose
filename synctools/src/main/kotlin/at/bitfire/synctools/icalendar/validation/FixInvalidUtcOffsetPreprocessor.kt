/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import androidx.annotation.VisibleForTesting
import java.util.logging.Level
import java.util.logging.Logger


/**
 * Some servers modify UTC offsets in TZOFFSET(FROM,TO) like "+005730" to an invalid "+5730".
 *
 * Rewrites values of all TZOFFSETFROM and TZOFFSETTO properties which match [regexpForProblem]
 * so that an hour value of 00 is inserted.
 */
class FixInvalidUtcOffsetPreprocessor: StreamPreprocessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    @VisibleForTesting
    internal val regexpForProblem = Regex("^(TZOFFSET(FROM|TO):[+\\-]?)((18|19|[2-6]\\d)\\d\\d)$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    override fun repairLine(line: String) =
        line.replace(regexpForProblem) {
            logger.log(Level.FINE, "Applying Synology WebDAV fix to invalid utc-offset", it.value)
            "${it.groupValues[1]}00${it.groupValues[3]}"
        }

}