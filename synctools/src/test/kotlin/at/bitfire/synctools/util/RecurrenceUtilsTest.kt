/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.transform.recurrence.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.ZoneId
import java.time.temporal.Temporal

class RecurrenceUtilsTest {

    private val tzVienna = ZoneId.of("Europe/Vienna")

    @Test
    fun `alignUntil returns original recur without UNTIL`() {
        val recur = dailyRecur()

        val result = RecurrenceUtils.alignUntil(
            recur = recur,
            startTemporal = dateValue("20250101")
        )

        assertSame(recur, result)
    }

    @Test
    fun `alignUntil returns original recur for DATE UNTIL and DATE DTSTART`() {
        val recur = dailyRecur(dateValue("20251015"))

        val result = RecurrenceUtils.alignUntil(
            recur = recur,
            startTemporal = dateValue("20250101")
        )

        assertSame(recur, result)
    }

    @Test
    fun `alignUntil amends DATE UNTIL with DATE-TIME DTSTART time and timezone`() {
        val result = RecurrenceUtils.alignUntil(
            recur = dailyRecur(dateValue("20251015")),
            startTemporal = dateTimeValue("20250101T010203", tzVienna)
        )

        assertEquals(
            dailyRecur(dateTimeValue("20251014T230203Z")),
            result
        )
    }

    @Test
    fun `alignUntil reduces DATE-TIME UNTIL to DATE for DATE DTSTART`() {
        val result = RecurrenceUtils.alignUntil(
            recur = dailyRecur(dateTimeValue("20251015T153118", tzVienna)),
            startTemporal = dateValue("20250101")
        )

        assertEquals(
            dailyRecur(dateValue("20251015")),
            result
        )
    }

    @Test
    fun `alignUntil converts DATE-TIME UNTIL to UTC for DATE-TIME DTSTART`() {
        val result = RecurrenceUtils.alignUntil(
            recur = dailyRecur(dateTimeValue("20251015T153118", tzVienna)),
            startTemporal = dateTimeValue("20250101T010203Z")
        )

        assertEquals(
            dailyRecur(dateTimeValue("20251015T133118Z")),
            result
        )
    }

    @Test
    fun `alignUntil returns original recur for UTC DATE-TIME UNTIL and DATE-TIME DTSTART`() {
        val recur = dailyRecur(dateTimeValue("20251015T133118Z"))

        val result = RecurrenceUtils.alignUntil(
            recur = recur,
            startTemporal = dateTimeValue("20250101T010203", tzVienna)
        )

        assertSame(recur, result)
    }

    private fun dailyRecur(until: Temporal? = null): Recur<Temporal> {
        val builder = Recur.Builder<Temporal>()
            .frequency(Frequency.DAILY)
        until?.let { builder.until(it) }
        return builder.build()
    }

}
