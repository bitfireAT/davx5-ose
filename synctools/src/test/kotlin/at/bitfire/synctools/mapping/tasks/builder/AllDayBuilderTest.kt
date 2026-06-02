/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.DefaultTimezoneRule
import at.bitfire.ical4android.Task
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class AllDayBuilderTest {

    @get:Rule
    val defaultTimezone = DefaultTimezoneRule("Europe/Berlin")

    private val builder = AllDayBuilder()

    @Test
    fun `old No DTSTART and no DUE - treated as all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 1,
            Tasks.TZ to null
        ), result.entityValues)
    }

    @Test
    fun `old DTSTART is DATE - all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(dtStart = DtStart(LocalDate.of(2025, 1, 15))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 1,
            Tasks.TZ to null
        ), result.entityValues)
    }

    @Test
    fun `old DTSTART is DATE-TIME - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(dtStart = DtStart(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to "Etc/UTC"
        ), result.entityValues)
    }

    @Test
    fun `old DUE is DATE - all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(due = Due(LocalDate.of(2025, 1, 15))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 1,
            Tasks.TZ to null
        ), result.entityValues)
    }

    @Test
    fun `old DUE is DATE-TIME - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(due = Due(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to "Etc/UTC"
        ), result.entityValues)
    }

    @Test
    fun `old DTSTART is DATE-TIME with named timezone - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(dtStart = DtStart(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, defaultTimezone.defaultZoneId))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

    @Test
    fun `old DTSTART is floating DATE-TIME - not all-day, uses system default timezone`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(dtStart = DtStart(LocalDateTime.of(2025, 1, 15, 10, 0, 0))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

    @Test
    fun `old DUE is DATE-TIME with named timezone (no DTSTART) - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(due = Due(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, defaultTimezone.defaultZoneId))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

    @Test
    fun `old DUE is floating DATE-TIME (no DTSTART) - not all-day, uses system default timezone`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(due = Due(LocalDateTime.of(2025, 1, 15, 10, 0, 0))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

    @Test
    fun `No DTSTART and no DUE - treated as all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 1,
            Tasks.TZ to null
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE - all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(DtStart(LocalDate.of(2025, 1, 15))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 1,
            Tasks.TZ to null
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE-TIME - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(DtStart(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to "Etc/UTC"
        ), result.entityValues)
    }

    @Test
    fun `DUE is DATE - all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Due(LocalDate.of(2025, 1, 15))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 1,
            Tasks.TZ to null
        ), result.entityValues)
    }

    @Test
    fun `DUE is DATE-TIME - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Due(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to "Etc/UTC"
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE-TIME with named timezone - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(DtStart(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, defaultTimezone.defaultZoneId))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is floating DATE-TIME - not all-day, uses system default timezone`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(DtStart(LocalDateTime.of(2025, 1, 15, 10, 0, 0))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

    @Test
    fun `DUE is DATE-TIME with named timezone (no DTSTART) - not all-day`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Due(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, defaultTimezone.defaultZoneId))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

    @Test
    fun `DUE is floating DATE-TIME (no DTSTART) - not all-day, uses system default timezone`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Due(LocalDateTime.of(2025, 1, 15, 10, 0, 0))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.IS_ALLDAY to 0,
            Tasks.TZ to defaultTimezone.defaultZoneId.id
        ), result.entityValues)
    }

}
