/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import at.bitfire.DefaultTimezoneRule
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.techbee.jtx.JtxContract
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class JtxTimeFieldTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Pacific/Honolulu")

    @Test
    fun `toTemporal with all-day timezone returns LocalDate`() {
        val jtxTimeField = JtxTimeField(
            timestamp = TIMESTAMP,
            timeZone = JtxContract.JtxICalObject.TZ_ALLDAY
        )

        val result = jtxTimeField.toTemporal()

        assertEquals(dateValue("20251015"), result)
    }

    @Test
    fun `toTemporal with UTC offset ID returns Instant`() {
        val jtxTimeField = JtxTimeField(
            timestamp = TIMESTAMP,
            timeZone = ZoneOffset.UTC.id
        )

        val result = jtxTimeField.toTemporal()

        assertEquals(Instant.ofEpochMilli(TIMESTAMP), result)
    }

    @Test
    fun `toTemporal with UTC timezone ID returns Instant`() {
        val jtxTimeField = JtxTimeField(
            timestamp = TIMESTAMP,
            timeZone = "UTC"
        )

        val result = jtxTimeField.toTemporal()

        assertEquals(Instant.ofEpochMilli(TIMESTAMP), result)
    }

    @Test
    fun `toTemporal with floating timezone returns LocalDateTime`() {
        val jtxTimeField = JtxTimeField(
            timestamp = TIMESTAMP,
            timeZone = null
        )

        val result = jtxTimeField.toTemporal()

        assertEquals(LocalDateTime.of(2025, 10, 14, 23, 46, 59), result)
    }

    @Test
    fun `toTemporal with named timezone returns ZonedDateTime`() {
        val jtxTimeField = JtxTimeField(
            timestamp = TIMESTAMP,
            timeZone = "Europe/Vienna"
        )

        val result = jtxTimeField.toTemporal()

        assertEquals(dateTimeValue("20251015T114659", ZoneId.of("Europe/Vienna")), result)
    }

    @Test
    fun `toTemporal with invalid timezone returns Instant`() {
        val jtxTimeField = JtxTimeField(
            timestamp = TIMESTAMP,
            timeZone = "Invalid/Timezone"
        )

        val result = jtxTimeField.toTemporal()

        assertEquals(Instant.ofEpochMilli(TIMESTAMP), result)
    }

    companion object {
        private const val TIMESTAMP = 1760521619000L
    }

}
