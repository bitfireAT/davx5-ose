/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.due
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Due
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class DueHandlerTest {

    private val handler = DueHandler()

    @Test
    fun `legacy No DUE leaves due null`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.due)
    }

    @Test
    fun `legacy All-day due date`() {
        val task = Task()
        handler.process(contentValuesOf(
            Tasks.DUE to 1592697600000L,        // 2020-06-21 00:00:00 UTC
            Tasks.IS_ALLDAY to 1,
        ), task)
        assertEquals(Due(LocalDate.of(2020, 6, 21)), task.due)
    }

    @Test
    fun `legacy Non-all-day due with timezone`() {
        val task = Task()
        handler.process(contentValuesOf(
            Tasks.DUE to 1592733600000L,        // 2020-06-21 10:00:00 UTC = 12:00:00 Europe/Vienna
            Tasks.TZ to "Europe/Vienna",
        ), task)
        val expected = ZonedDateTime.of(2020, 6, 21, 12, 0, 0, 0, ZoneId.of("Europe/Vienna"))
        assertEquals(Due(expected), task.due)
    }

    @Test
    fun `legacy Non-all-day due without timezone (UTC Instant)`() {
        val task = Task()
        handler.process(contentValuesOf(
            Tasks.DUE to 1592733600000L,        // 2020-06-21 10:00:00 UTC
        ), task)
        assertEquals(Due(Instant.ofEpochMilli(1592733600000L)), task.due)
    }

    @Test
    fun `No DUE leaves due null`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.due<Temporal>())
    }

    @Test
    fun `All-day due date`() {
        val input = Entity(
            contentValuesOf(
                Tasks.DUE to 1592697600000L,        // 2020-06-21 00:00:00 UTC
                Tasks.IS_ALLDAY to 1,
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Due(LocalDate.of(2020, 6, 21)), task.due<LocalDate>())
    }

    @Test
    fun `Non-all-day due with timezone`() {
        val input = Entity(
            contentValuesOf(
                Tasks.DUE to 1592733600000L,        // 2020-06-21 10:00:00 UTC = 12:00:00 Europe/Vienna
                Tasks.TZ to "Europe/Vienna",
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        val expected = ZonedDateTime.of(2020, 6, 21, 12, 0, 0, 0, ZoneId.of("Europe/Vienna"))
        assertEquals(Due(expected), task.due<ZonedDateTime>())
    }

    @Test
    fun `Non-all-day due without timezone (UTC Instant)`() {
        val input = Entity(
            contentValuesOf(
                Tasks.DUE to 1592733600000L,        // 2020-06-21 10:00:00 UTC
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Due(Instant.ofEpochMilli(1592733600000L)), task.due<Instant>())
    }
}
