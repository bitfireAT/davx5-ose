/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.tasks.provider.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecurrenceExpanderTest {

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun task(
        taskId: Long = 1,
        dtstart: String? = "2026-01-01T09:00:00Z",
        dtstartTz: String? = "UTC",
        due: String? = null,
        duration: String? = null,
        isAllDay: Boolean = false,
        rrule: String? = null,
        rdate: String? = null,
        exdate: String? = null
    ) = RecurringTaskSource(
        taskId = taskId,
        dtstart = dtstart?.let(::millis),
        dtstartTz = dtstartTz,
        due = due?.let(::millis),
        duration = duration,
        isAllDay = isAllDay,
        rrule = rrule,
        rdate = rdate,
        exdate = exdate
    )


    @Test
    fun `non-recurring task returns exactly one instance`() {
        val main = task(due = "2026-01-01T10:00:00Z")

        val result = RecurrenceExpander.expand(main, emptyList())

        val instance = result.single()
        assertEquals(1L, instance.taskId)
        assertEquals(millis("2026-01-01T09:00:00Z"), instance.instanceStart)
        assertEquals(millis("2026-01-01T10:00:00Z"), instance.instanceDue)
    }

    @Test
    fun `non-recurring task computes due from duration`() {
        val main = task(duration = "PT1H")

        val instance = RecurrenceExpander.expand(main, emptyList()).single()

        assertEquals(millis("2026-01-01T10:00:00Z"), instance.instanceDue)
    }

    @Test
    fun `task without dtstart returns single instance with null dates`() {
        val main = task(dtstart = null, rrule = "FREQ=DAILY;COUNT=5")

        val instance = RecurrenceExpander.expand(main, emptyList()).single()

        assertNull(instance.instanceStart)
        assertNull(instance.instanceDue)
    }

    @Test
    fun `daily RRULE with COUNT expands to N instances`() {
        val main = task(due = "2026-01-01T10:00:00Z", rrule = "FREQ=DAILY;COUNT=5")

        val result = RecurrenceExpander.expand(main, emptyList())

        assertEquals(5, result.size)
        assertEquals(millis("2026-01-01T09:00:00Z"), result[0].instanceStart)
        assertEquals(millis("2026-01-05T09:00:00Z"), result[4].instanceStart)
        // due offset (1h) must carry over to every occurrence
        assertEquals(millis("2026-01-05T10:00:00Z"), result[4].instanceDue)
    }

    @Test
    fun `RRULE with UNTIL stops at the bound`() {
        val main = task(rrule = "FREQ=DAILY;UNTIL=20260103T090000Z")

        val result = RecurrenceExpander.expand(main, emptyList())

        assertEquals(3, result.size)
        assertEquals(millis("2026-01-03T09:00:00Z"), result.last().instanceStart)
    }

    @Test
    fun `EXDATE removes a generated occurrence`() {
        val main = task(rrule = "FREQ=DAILY;COUNT=5", exdate = "20260103T090000Z")

        val result = RecurrenceExpander.expand(main, emptyList())

        assertEquals(4, result.size)
        assertTrue(result.none { it.instanceStart == millis("2026-01-03T09:00:00Z") })
    }

    @Test
    fun `RDATE-only task (no RRULE) expands to seed plus RDATEs`() {
        val main = task(rdate = "20260105T090000Z,20260110T090000Z")

        val result = RecurrenceExpander.expand(main, emptyList())

        assertEquals(3, result.size)
        assertEquals(
            listOf("2026-01-01T09:00:00Z", "2026-01-05T09:00:00Z", "2026-01-10T09:00:00Z").map(::millis),
            result.map { it.instanceStart }
        )
    }

    @Test
    fun `unbounded RRULE is capped at MAX_INSTANCES`() {
        val main = task(rrule = "FREQ=DAILY")

        val result = RecurrenceExpander.expand(main, emptyList())

        assertEquals(RecurrenceExpander.MAX_INSTANCES, result.size)
    }

    @Test
    fun `RECURRENCE-ID exception overrides the matching occurrence`() {
        val main = task(rrule = "FREQ=DAILY;COUNT=3")
        val overriddenAnchor = millis("2026-01-02T09:00:00Z")
        val exception = RecurrenceExceptionSource(
            taskId = 99,
            originalInstanceTime = overriddenAnchor,
            dtstart = millis("2026-01-02T14:00:00Z"), // moved to the afternoon
            dtstartTz = "UTC",
            due = null,
            duration = null,
            isAllDay = false
        )

        val result = RecurrenceExpander.expand(main, listOf(exception))

        assertEquals(3, result.size)
        val overridden = result[1]
        assertEquals(99L, overridden.taskId)
        assertEquals(millis("2026-01-02T14:00:00Z"), overridden.instanceStart)
        // the other two occurrences are still the main task's own
        assertEquals(1L, result[0].taskId)
        assertEquals(1L, result[2].taskId)
    }

    @Test
    fun `orphaned exception not matching any anchor still gets its own instance`() {
        val main = task(rrule = "FREQ=DAILY;COUNT=2")
        val orphan = RecurrenceExceptionSource(
            taskId = 99,
            originalInstanceTime = millis("2026-06-01T09:00:00Z"), // nowhere near the RRULE's range
            dtstart = millis("2026-06-01T09:00:00Z"),
            dtstartTz = "UTC",
            due = null,
            duration = null,
            isAllDay = false
        )

        val result = RecurrenceExpander.expand(main, listOf(orphan))

        assertEquals(3, result.size)
        assertTrue(result.any { it.taskId == 99L })
    }

    @Test
    fun `all-day task expands using DATE arithmetic`() {
        val main = task(
            dtstart = "2026-01-01T00:00:00Z", dtstartTz = null, isAllDay = true,
            due = "2026-01-02T00:00:00Z", rrule = "FREQ=WEEKLY;COUNT=2"
        )

        val result = RecurrenceExpander.expand(main, emptyList())

        assertEquals(2, result.size)
        assertEquals(
            LocalDate.parse("2026-01-08").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            result[1].instanceStart
        )
        // due offset (1 day) preserved
        assertEquals(
            LocalDate.parse("2026-01-09").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            result[1].instanceDue
        )
        // all-day sorting values equal the raw (already zone-agnostic) values
        assertEquals(result[1].instanceStart, result[1].instanceStartSorting)
    }

    @Test
    fun `sorting values are zone-normalised for timed occurrences`() {
        // 09:00 in Tokyo (UTC+9) is 00:00 UTC - the sorting value should reflect "09:00" wall-clock,
        // not the true UTC instant, so it's comparable against a 09:00-local task in another zone
        val main = task(dtstart = "2026-01-01T00:00:00Z", dtstartTz = "Asia/Tokyo")

        val instance = RecurrenceExpander.expand(main, emptyList()).single()

        assertEquals(millis("2026-01-01T00:00:00Z"), instance.instanceStart)
        assertEquals(millis("2026-01-01T09:00:00Z"), instance.instanceStartSorting)
    }

}
