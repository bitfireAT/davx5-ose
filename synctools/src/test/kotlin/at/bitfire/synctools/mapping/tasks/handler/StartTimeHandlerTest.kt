/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.DtStart
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

@RunWith(RobolectricTestRunner::class)
class StartTimeHandlerTest {

    private val handler = StartTimeHandler()

    @Test
    fun `No DTSTART leaves dtStart null`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.dtStart)
    }

    @Test
    fun `All-day start time`() {
        val task = Task()
        handler.process(contentValuesOf(
            Tasks.DTSTART to 1592697600000L,    // 2020-06-21 00:00:00 UTC
            Tasks.IS_ALLDAY to 1,
        ), task)
        assertEquals(DtStart(LocalDate.of(2020, 6, 21)), task.dtStart)
    }

    @Test
    fun `Non-all-day start time with timezone`() {
        val task = Task()
        handler.process(contentValuesOf(
            Tasks.DTSTART to 1592733600000L,    // 2020-06-21 10:00:00 UTC = 12:00:00 Europe/Vienna
            Tasks.TZ to "Europe/Vienna",
        ), task)
        val expected = ZonedDateTime.of(2020, 6, 21, 12, 0, 0, 0, ZoneId.of("Europe/Vienna"))
        assertEquals(DtStart(expected), task.dtStart)
    }

    @Test
    fun `Non-all-day start time without timezone (UTC Instant)`() {
        val task = Task()
        handler.process(contentValuesOf(
            Tasks.DTSTART to 1592733600000L,    // 2020-06-21 10:00:00 UTC
        ), task)
        assertEquals(DtStart(Instant.ofEpochMilli(1592733600000L)), task.dtStart)
    }

}
