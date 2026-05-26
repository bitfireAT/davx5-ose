/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentUris
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.impl.TestTaskList
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.XProperty
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class DmfsTaskTest(
    providerName: TaskProvider.ProviderName
): DmfsStyleProvidersTaskTest(providerName) {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)

    private lateinit var taskListUri: Uri
    private var taskList: DmfsTaskList? = null

    @Before
    override fun prepare() {
        super.prepare()

        taskList = TestTaskList.create(testAccount, provider)
        assertNotNull("Couldn't find/create test task list", taskList)

        taskListUri = ContentUris.withAppendedId(provider.taskListsUri(), taskList!!.id)
    }

    @After
    override fun shutdown() {
        taskList?.delete()
        super.shutdown()
    }


    // tests

    @Test
    fun testConstructor_ContentValues() {
        val dmfsTask = DmfsTask(
            taskList!!, contentValuesOf(
                Tasks._ID to 123,
                Tasks._SYNC_ID to "some-ical.ics",
                DmfsTask.COLUMN_ETAG to "some-etag",
                DmfsTask.COLUMN_FLAGS to 45
            )
        )
        assertEquals(123L, dmfsTask.id)
        assertEquals("some-ical.ics", dmfsTask.syncId)
        assertEquals("some-etag", dmfsTask.eTag)
        assertEquals(45, dmfsTask.flags)
    }

    @Test
    fun testAddTask() {
        // build and write event to calendar provider
        val task = Task()
        task.uid = "sample1@testAddEvent"
        task.summary = "Sample event"
        task.description = "Sample event with date/time"
        task.location = "Sample location"
        task.dtStart = DtStart(ZonedDateTime.of(
            2015, 5, 1,
            12, 0, 0, 0,
            tzVienna.toZoneId()
        ))
        task.due = Due(ZonedDateTime.of(
            2015, 5, 1,
            14, 0, 0, 0,
            tzVienna.toZoneId()
        ))
        task.organizer = Organizer("mailto:organizer@example.com")
        assertFalse(task.isAllDay())

        // extended properties
        task.categories.addAll(arrayOf("Cat1", "Cat2"))
        task.comment = "A comment"

        task.relatedTo.add(
            RelatedTo("most-fields2@example.com")
                .add(RelType.SIBLING)
        )

        task.unknownProperties += XProperty("X-UNKNOWN-PROP", "Unknown Value")

        // add to task list
        val uri = DmfsTask(taskList!!, task, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0).add()
        assertNotNull("Couldn't add task", uri)

        // read and parse event from calendar provider
        val testTask = taskList!!.getLegacyTask(ContentUris.parseId(uri))
        try {
            assertNotNull("Inserted task is not here", testTask)
            val task2 = testTask?.task
            assertNotNull("Inserted task is empty", task2)

            // compare with original event
            assertEquals(task.summary, task2!!.summary)
            assertEquals(task.description, task2.description)
            assertEquals(task.location, task2.location)
            assertEquals(task.dtStart, task2.dtStart)

            assertEquals(task.categories, task2.categories)
            assertEquals(task.comment, task2.comment)
            assertEquals(task.relatedTo, task2.relatedTo)
            assertEquals(task.unknownProperties, task2.unknownProperties)
        } finally {
            testTask?.delete()
        }
    }

    @Test(expected = LocalStorageException::class)
    fun testAddTaskWithInvalidDue() {
        val task = Task()
        task.uid = "invalidDUE@ical4android.tests"
        task.summary = "Task with invalid DUE"
        task.dtStart = DtStart(LocalDate.of(2015, 1, 2))

        task.due = Due(LocalDate.of(2015, 1, 1))
        DmfsTask(taskList!!, task, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0).add()
    }

    @Test
    fun testAddTaskWithManyAlarms() {
        val task = Task()
        task.uid = "TaskWithManyAlarms"
        task.summary = "Task with many alarms"
        task.dtStart = DtStart(LocalDate.of(2015, 1, 2))

        for (i in 1..1050)
            task.alarms += VAlarm(java.time.Duration.ofMinutes(i.toLong()))

        val uri = DmfsTask(taskList!!, task, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0).add()
        val task2 = taskList!!.getTask(ContentUris.parseId(uri))
        assertEquals(1050, task2?.task?.alarms?.size)
    }

    @Test
    fun testUpdateTask() {
        // add test event without reminder
        val task = Task()
        task.uid = "sample1@testAddEvent"
        task.summary = "Sample event"
        task.description = "Sample event with date/time"
        task.location = "Sample location"
        task.dtStart = DtStart(ZonedDateTime.of(
            2015, 5, 1,
            12, 0, 0, 0,
            tzVienna.toZoneId()
        ))
        assertFalse(task.isAllDay())
        val uri = DmfsTask(taskList!!, task, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0).add()
        assertNotNull(uri)

        val testTask = taskList!!.getTask(ContentUris.parseId(uri))
        try {
            // update test event in calendar
            val task2 = testTask?.task!!
            task2.summary = "Updated event"                     // change value
            task.location = null                                // remove value
            task2.duration = Duration(java.time.Duration.ofMinutes(10))     // add value
            testTask.update(task2)

            // read again and verify result
            val updatedTask = taskList!!.getTask(ContentUris.parseId(uri))?.task!!
            assertEquals(task2.summary, updatedTask.summary)
            assertEquals(task2.location, updatedTask.location)
            assertEquals(task2.dtStart, updatedTask.dtStart)
            assertEquals(task2.duration!!.value, updatedTask.duration!!.value)
        } finally {
            testTask?.delete()
        }
    }

}