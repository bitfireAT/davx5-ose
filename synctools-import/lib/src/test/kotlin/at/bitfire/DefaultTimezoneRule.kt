/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.time.ZoneId
import java.util.TimeZone

/**
 * A JUnit TestRule that temporarily sets the default timezone for the duration of a test.
 *
 * This rule is useful for testing code that depends on the default timezone, ensuring consistent
 * and predictable behavior regardless of the system's default timezone. The original default
 * timezone is restored after the test completes, even if the test fails.
 *
 * @param defaultTzId The ID of the timezone to set as the default during the test.
 */
class DefaultTimezoneRule(
    defaultTzId: String
): TestRule {

    /** The [TimeZone] corresponding to the default timezone ID provided to the rule. */
    val defaultTimeZone: TimeZone = TimeZone.getTimeZone(defaultTzId)

    /** The [ZoneId] corresponding to the default timezone ID provided to the rule. */
    val defaultZoneId: ZoneId = ZoneId.of(defaultTzId)

    override fun apply(
        base: Statement,
        description: Description
    ): Statement = object: Statement() {

        override fun evaluate() {
            val originalDefaultTz = TimeZone.getDefault()
            try {
                TimeZone.setDefault(defaultTimeZone)
                base.evaluate()
            } finally {
                TimeZone.setDefault(originalDefaultTz)
            }
        }

    }

}