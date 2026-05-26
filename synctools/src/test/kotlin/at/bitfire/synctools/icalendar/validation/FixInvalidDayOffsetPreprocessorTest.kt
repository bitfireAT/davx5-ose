/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class FixInvalidDayOffsetPreprocessorTest {
    
    private val processor = FixInvalidDayOffsetPreprocessor()

    /**
     * Calls `processor.fixString` and asserts the result is equal to [expected].
     *
     * @param expected      The expected result
     * @param testValue     The value to test
     * @param parseDuration If `true`, [Duration.parse] is called on the fixed value to make sure it's a valid duration
     */
    private fun assertFixedEquals(expected: String, testValue: String, parseDuration: Boolean = true) {
        // Fix the duration string
        val fixed = processor.repairLine(testValue)

        // Test the duration can now be parsed
        if (parseDuration)
            for (line in fixed.split('\n')) {
                val duration = line.substring(line.indexOf(':') + 1)
                Duration.parse(duration)
            }

        // Assert
        assertEquals(expected, fixed)
    }

    @Test
    fun test_repairLine_NoOccurrence() {
        assertEquals(
            "Some String",
            processor.repairLine("Some String"),
        )
    }

    @Test
    fun test_repairLine_SucceedsAsValueOnCorrectProperties() {
        // By RFC 5545 the only properties allowed to hold DURATION as a VALUE are:
        // DURATION, REFRESH, RELATED, TRIGGER
        assertFixedEquals("DURATION;VALUE=DURATION:P1D", "DURATION;VALUE=DURATION:PT1D")
        assertFixedEquals("REFRESH-INTERVAL;VALUE=DURATION:P1D", "REFRESH-INTERVAL;VALUE=DURATION:PT1D")
        assertFixedEquals("RELATED-TO;VALUE=DURATION:P1D", "RELATED-TO;VALUE=DURATION:PT1D")
        assertFixedEquals("TRIGGER;VALUE=DURATION:P1D", "TRIGGER;VALUE=DURATION:PT1D")
    }

    @Test
    fun test_repairLine_FailsAsValueOnWrongProperty() {
        // The update from RFC 2445 to RFC 5545 disallows using DURATION as a VALUE in FREEBUSY
        assertFixedEquals("FREEBUSY;VALUE=DURATION:PT1D", "FREEBUSY;VALUE=DURATION:PT1D", parseDuration = false)
    }

    @Test
    fun test_repairLine_FailsIfNotAtStartOfLine() {
        assertFixedEquals("xxDURATION;VALUE=DURATION:PT1D", "xxDURATION;VALUE=DURATION:PT1D", parseDuration = false)
    }

    @Test
    fun test_repairLine_DayOffsetFrom_Invalid() {
        assertFixedEquals("DURATION:-P1D", "DURATION:-PT1D")
        assertFixedEquals("TRIGGER:-P2D", "TRIGGER:-PT2D")

        assertFixedEquals("DURATION:-P1D", "DURATION:-P1DT")
        assertFixedEquals("TRIGGER:-P2D", "TRIGGER:-P2DT")
    }

    @Test
    fun test_repairLine_DayOffsetFrom_Valid() {
        assertFixedEquals("DURATION:-PT12H", "DURATION:-PT12H")
        assertFixedEquals("TRIGGER:-PT12H", "TRIGGER:-PT12H")
    }

    @Test
    fun test_repairLine_DayOffsetFromMultiple_Invalid() {
        assertFixedEquals("DURATION:-P1D\nTRIGGER:-P2D", "DURATION:-PT1D\nTRIGGER:-PT2D")
        assertFixedEquals("DURATION:-P1D\nTRIGGER:-P2D", "DURATION:-P1DT\nTRIGGER:-P2DT")
    }

    @Test
    fun test_repairLine_DayOffsetFromMultiple_Valid() {
        assertFixedEquals("DURATION:-PT12H\nTRIGGER:-PT12H", "DURATION:-PT12H\nTRIGGER:-PT12H")
    }

    @Test
    fun test_repairLine_DayOffsetFromMultiple_Mixed() {
        assertFixedEquals("DURATION:-P1D\nDURATION:-PT12H\nTRIGGER:-P2D", "DURATION:-PT1D\nDURATION:-PT12H\nTRIGGER:-PT2D")
        assertFixedEquals("DURATION:-P1D\nDURATION:-PT12H\nTRIGGER:-P2D", "DURATION:-P1DT\nDURATION:-PT12H\nTRIGGER:-P2DT")
    }

    @Test
    fun test_RegexpForProblem_DayOffsetTo_Invalid() {
        val regex = processor.regexpForProblem
        assertTrue(regex.matches("DURATION:PT2D"))
        assertTrue(regex.matches("TRIGGER:PT1D"))
    }

    @Test
    fun test_RegexpForProblem_DayOffsetTo_Valid() {
        val regex = processor.regexpForProblem
        assertFalse(regex.matches("DURATION:-PT12H"))
        assertFalse(regex.matches("TRIGGER:-PT15M"))
    }

}