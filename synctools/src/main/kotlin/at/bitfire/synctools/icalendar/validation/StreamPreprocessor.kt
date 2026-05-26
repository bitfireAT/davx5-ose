/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

/**
 * This is a pre-processor for iCalendar lines that can detect and repair errors which
 * cannot be repaired on a higher level (because parsing alone would cause syntax
 * or other unrecoverable errors).
 */
interface StreamPreprocessor {

    /**
     * Validates and potentially repairs an iCalendar string.
     *
     * @param line  full line of an iCalendar lines to validate / fix (without line break)
     *
     * @return the potentially fixed version of [line] (without line break)
     */
    fun repairLine(line: String): String

}