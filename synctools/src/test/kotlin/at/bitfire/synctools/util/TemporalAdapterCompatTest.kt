/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TemporalAdapterCompatTest {

    @Test
    fun `Compare equal Instant and ZonedDateTime`() {
        val zdt = ZonedDateTime.of(2026, 5, 28, 18, 4, 22, 0, ZoneOffset.UTC)
        val instant = zdt.toInstant()
        
        // Both are equal, so neither isBefore nor isAfter should be true
        assertFalse(TemporalAdapterCompat.isBefore(zdt, instant))
        assertFalse(TemporalAdapterCompat.isBefore(instant, zdt))
        assertFalse(TemporalAdapterCompat.isAfter(zdt, instant))
        assertFalse(TemporalAdapterCompat.isAfter(instant, zdt))
    }

    @Test
    fun `Compare Instant that is before ZonedDateTime`() {
        val instant = Instant.parse("2026-05-28T18:00:00Z")
        val zdt = ZonedDateTime.of(2026, 5, 28, 18, 4, 22, 0, ZoneOffset.UTC)
        
        assertTrue(TemporalAdapterCompat.isBefore(instant, zdt))
        assertFalse(TemporalAdapterCompat.isAfter(instant, zdt))
        assertTrue(TemporalAdapterCompat.isAfter(zdt, instant))
        assertFalse(TemporalAdapterCompat.isBefore(zdt, instant))
    }

    @Test
    fun `Compare ZonedDateTime that is before Instant`() {
        val zdt = ZonedDateTime.of(2026, 5, 28, 18, 0, 0, 0, ZoneOffset.UTC)
        val instant = Instant.parse("2026-05-28T18:04:22Z")
        
        assertTrue(TemporalAdapterCompat.isBefore(zdt, instant))
        assertFalse(TemporalAdapterCompat.isAfter(zdt, instant))
        assertTrue(TemporalAdapterCompat.isAfter(instant, zdt))
        assertFalse(TemporalAdapterCompat.isBefore(instant, zdt))
    }

}
