/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.impl.TestTaskList
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.dmfs.tasks.contract.TaskContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DmfsTaskProcessorTest(
    providerName: TaskProvider.ProviderName
) : DmfsStyleProvidersTaskTest(providerName) {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!
    private val tzDefault = tzRegistry.getTimeZone(ZoneId.systemDefault().id)!!

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)

    private lateinit var taskListUri: Uri
    private var taskList: DmfsTaskList? = null
    private lateinit var processor: DmfsTaskProcessor

    @Before
    override fun prepare() {
        super.prepare()

        taskList = TestTaskList.create(testAccount, provider)
        assertNotNull("Couldn't find/create test task list", taskList)

        taskListUri = ContentUris.withAppendedId(provider.taskListsUri(), taskList!!.id)
        processor = DmfsTaskProcessor(taskList!!)
    }

    @After
    override fun shutdown() {
        taskList?.delete()
        super.shutdown()
    }

    // populateTask tests

    @Test
    fun testPopulateTask_BasicProperties() {
        val values = ContentValues().apply {
            put(TaskContract.Tasks._UID, "test-uid-123")
            put(TaskContract.Tasks.SYNC_VERSION, 5)
            put(TaskContract.Tasks.TITLE, "Test Task")
            put(TaskContract.Tasks.LOCATION, "Test Location")
            put(TaskContract.Tasks.DESCRIPTION, "Test Description")
            put(TaskContract.Tasks.URL, "https://example.com")
            put(TaskContract.Tasks.TASK_COLOR, 0x123456)
            put(TaskContract.Tasks.PRIORITY, 3)
        }

        val task = Task()
        processor.populateTask(values, task)

        assertEquals("test-uid-123", task.uid)
        assertEquals(5, task.sequence)
        assertEquals("Test Task", task.summary)
        assertEquals("Test Location", task.location)
        assertEquals("Test Description", task.description)
        assertEquals("https://example.com", task.url)
        assertEquals(0x123456, task.color)
        assertEquals(3, task.priority)
    }

    @Test
    fun testPopulateTask_GeoPosition() {
        val values = ContentValues().apply {
            put(TaskContract.Tasks.GEO, "16.159601,47.913563")
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.geoPosition)
        assertEquals(47.913563.toBigDecimal(), task.geoPosition!!.latitude)
        assertEquals(16.159601.toBigDecimal(), task.geoPosition!!.longitude)
    }

    @Test
    fun testPopulateTask_GeoPosition_Invalid() {
        val values = ContentValues().apply {
            put(TaskContract.Tasks.GEO, "invalid-geo-data")
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNull(task.geoPosition)
    }

    @Test
    fun testPopulateTask_Organizer() {
        val values = ContentValues().apply {
            put(TaskContract.Tasks.ORGANIZER, "organizer@example.com")
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.organizer)
        assertEquals("mailto:organizer@example.com", task.organizer!!.value)
    }

    @Test
    fun testPopulateTask_Classification() {
        // Test PUBLIC
        var values = ContentValues().apply {
            put(TaskContract.Tasks.CLASSIFICATION, TaskContract.Tasks.CLASSIFICATION_PUBLIC)
        }
        var task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.classification)
        assertEquals(ImmutableClazz.VALUE_PUBLIC, task.classification!!.value)

        // Test PRIVATE
        values = ContentValues().apply {
            put(TaskContract.Tasks.CLASSIFICATION, TaskContract.Tasks.CLASSIFICATION_PRIVATE)
        }
        task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.classification)
        assertEquals(ImmutableClazz.VALUE_PRIVATE, task.classification!!.value)

        // Test CONFIDENTIAL
        values = ContentValues().apply {
            put(TaskContract.Tasks.CLASSIFICATION, TaskContract.Tasks.CLASSIFICATION_CONFIDENTIAL)
        }
        task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.classification)
        assertEquals(ImmutableClazz.VALUE_CONFIDENTIAL, task.classification!!.value)

        // Test default (unknown)
        values = ContentValues().apply {
            put(TaskContract.Tasks.CLASSIFICATION, 999)
        }
        task = Task()
        processor.populateTask(values, task)
        assertNull(task.classification)
    }

    @Test
    fun testPopulateTask_Status() {
        // Test NEEDS_ACTION
        var values = ContentValues().apply {
            put(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_NEEDS_ACTION)
        }
        var task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.status)
        assertEquals(ImmutableStatus.VALUE_NEEDS_ACTION, task.status!!.value)

        // Test COMPLETED
        values = ContentValues().apply {
            put(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_COMPLETED)
        }
        task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.status)
        assertEquals(ImmutableStatus.VALUE_COMPLETED, task.status!!.value)

        // Test IN_PROCESS
        values = ContentValues().apply {
            put(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_IN_PROCESS)
        }
        task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.status)
        assertEquals(ImmutableStatus.VALUE_IN_PROCESS, task.status!!.value)

        // Test CANCELLED
        values = ContentValues().apply {
            put(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_CANCELLED)
        }
        task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.status)
        assertEquals(ImmutableStatus.VALUE_CANCELLED, task.status!!.value)

        // Test default
        values = ContentValues()
        task = Task()
        processor.populateTask(values, task)
        assertNotNull(task.status)
        assertEquals(ImmutableStatus.VALUE_NEEDS_ACTION, task.status!!.value)
    }

    @Test
    fun testPopulateTask_CompletedAndPercentComplete() {
        val now = Instant.now()
        val values = ContentValues().apply {
            put(TaskContract.Tasks.COMPLETED, now.toEpochMilli())
            put(TaskContract.Tasks.PERCENT_COMPLETE, 75)
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.completedAt)
        assertEquals(now.toEpochMilli(), task.completedAt!!.date.toEpochMilli())
        assertEquals(75, task.percentComplete)
    }

    @Test
    fun testPopulateTask_DtStart_AllDay() {
        val date = LocalDate.of(2020, 7, 3)
        val values = ContentValues().apply {
            put(TaskContract.Tasks.DTSTART, date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(TaskContract.Tasks.IS_ALLDAY, 1)
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.dtStart)
        assertTrue(task.dtStart!!.date is LocalDate)
        assertEquals(date, task.dtStart!!.date as LocalDate)
    }

    @Test
    fun testPopulateTask_DtStart_WithTimezone() {
        val instant = ZonedDateTime.of(
            LocalDate.of(2020, 7, 3),
            LocalTime.of(15, 57, 22),
            tzVienna.toZoneId()
        ).toInstant()
        
        val values = ContentValues().apply {
            put(TaskContract.Tasks.DTSTART, instant.toEpochMilli())
            put(TaskContract.Tasks.TZ, tzVienna.id)
            put(TaskContract.Tasks.IS_ALLDAY, 0)
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.dtStart)
        assertTrue(task.dtStart!!.date is ZonedDateTime)
        assertEquals(instant, (task.dtStart!!.date as ZonedDateTime).toInstant())
    }

    @Test
    fun testPopulateTask_Due_AllDay() {
        val date = LocalDate.of(2020, 7, 3)
        val values = ContentValues().apply {
            put(TaskContract.Tasks.DUE, date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(TaskContract.Tasks.IS_ALLDAY, 1)
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.due)
        assertTrue(task.due!!.date is LocalDate)
        assertEquals(date, task.due!!.date as LocalDate)
    }

    @Test
    fun testPopulateTask_Due_WithTimezone() {
        val instant = ZonedDateTime.of(
            LocalDate.of(2020, 7, 3),
            LocalTime.of(15, 57, 22),
            tzVienna.toZoneId()
        ).toInstant()
        
        val values = ContentValues().apply {
            put(TaskContract.Tasks.DUE, instant.toEpochMilli())
            put(TaskContract.Tasks.TZ, tzVienna.id)
            put(TaskContract.Tasks.IS_ALLDAY, 0)
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.due)
        assertTrue(task.due!!.date is ZonedDateTime)
        assertEquals(instant, (task.due!!.date as ZonedDateTime).toInstant())
    }

    @Test
    fun testPopulateTask_Duration() {
        val values = ContentValues().apply {
            put(TaskContract.Tasks.DURATION, "P1DT2H30M")
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.duration)
        assertEquals("P1DT2H30M", task.duration!!.duration.toRfc5545Duration(Instant.now()))
    }

    @Test
    fun testPopulateTask_RRule() {
        val values = ContentValues().apply {
            put(TaskContract.Tasks.RRULE, "FREQ=DAILY;COUNT=10")
        }

        val task = Task()
        processor.populateTask(values, task)

        assertNotNull(task.rRule)
        assertEquals("FREQ=DAILY;COUNT=10", task.rRule!!.value)
    }

}
