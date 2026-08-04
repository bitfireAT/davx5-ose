/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Entity
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.tasks.contract.TaskAlarms
import at.bitfire.tasks.contract.TaskLists
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DavTaskListTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAccount = Account(DavTaskListTest::class.java.name, "at.bitfire.synctools.test.davtasks.account")

    private lateinit var client: ContentProviderClient
    private lateinit var listProvider: DavTaskListProvider

    @Before
    fun setUp() {
        client = context.contentResolver.acquireContentProviderClient(DAV_TASKS_TEST_AUTHORITY)!!
        listProvider = DavTaskListProvider(testAccount, client, DAV_TASKS_TEST_AUTHORITY)
    }

    @After
    fun tearDown() {
        listProvider.findTaskLists().forEach { it.delete() }
        client.close()
    }

    private fun createTaskList() = listProvider.createAndGetTaskList(
        contentValuesOf(
            TaskLists._SYNC_ID to "1",
            TaskLists.LIST_NAME to "Test List",
            TaskLists.SUPPORTED_COMPONENTS to TaskLists.Component.VTODO
        )
    )


    @Test
    fun testAddTask_minimal() {
        val taskList = createTaskList()
        try {
            val id = taskList.addTask(Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._UID to "uid-minimal",
                Tasks.SUMMARY to "Minimal Task"
            )))

            val result = taskList.getTask(id)
            assertNotNull(result)
            assertEquals("Minimal Task", result!!.entityValues.getAsString(Tasks.SUMMARY))
            assertEquals("uid-minimal", result.entityValues.getAsString(Tasks._UID))
            assertTrue(result.subValues.isEmpty())
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testAddTask_withProperty() {
        val taskList = createTaskList()
        try {
            val entity = Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._UID to "uid-with-category",
                Tasks.SUMMARY to "Task with category"
            )).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(asSyncAdapter = false),
                    contentValuesOf(
                        TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY,
                        TaskProperties.DATA1 to "Home"
                    )
                )
            }
            val id = taskList.addTask(entity)

            val result = taskList.getTask(id)!!
            assertEquals(1, result.subValues.size)
            val row = result.subValues.first()
            assertEquals(TaskProperties.contentUri(DAV_TASKS_TEST_AUTHORITY), row.uri)
            assertEquals(TaskProperties.MIMETYPE_CATEGORY, row.values.getAsString(TaskProperties.MIMETYPE))
            assertEquals("Home", row.values.getAsString(TaskProperties.DATA1))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testAddTask_withAlarm() {
        val taskList = createTaskList()
        try {
            val entity = Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._UID to "uid-with-alarm",
                Tasks.SUMMARY to "Task with alarm"
            )).apply {
                addSubValue(
                    taskList.tasksAlarmsUri(asSyncAdapter = false),
                    contentValuesOf(
                        TaskAlarms.ACTION to TaskAlarms.Action.DISPLAY,
                        TaskAlarms.TRIGGER_RELATIVE to "-PT15M"
                    )
                )
            }
            val id = taskList.addTask(entity)

            val result = taskList.getTask(id)!!
            assertEquals(1, result.subValues.size)
            val row = result.subValues.first()
            assertEquals(TaskAlarms.contentUri(DAV_TASKS_TEST_AUTHORITY), row.uri)
            assertEquals(TaskAlarms.Action.DISPLAY, row.values.getAsString(TaskAlarms.ACTION))
            assertEquals("-PT15M", row.values.getAsString(TaskAlarms.TRIGGER_RELATIVE))
        } finally {
            taskList.delete()
        }
    }

    /**
     * The key new-code path: [TaskProperties] and [TaskAlarms] are two different tables (unlike
     * the DMFS backend's single Properties table, see [DavTaskList.subRowTarget]) - a task with
     * both must route each sub-value to its own table and reassemble both correctly on read.
     */
    @Test
    fun testAddTask_withPropertyAndAlarm() {
        val taskList = createTaskList()
        try {
            val entity = Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._UID to "uid-with-both",
                Tasks.SUMMARY to "Task with category and alarm"
            )).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(asSyncAdapter = false),
                    contentValuesOf(
                        TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY,
                        TaskProperties.DATA1 to "Work"
                    )
                )
                addSubValue(
                    taskList.tasksAlarmsUri(asSyncAdapter = false),
                    contentValuesOf(
                        TaskAlarms.ACTION to TaskAlarms.Action.DISPLAY,
                        TaskAlarms.TRIGGER_RELATIVE to "-PT30M"
                    )
                )
            }
            val id = taskList.addTask(entity)

            val result = taskList.getTask(id)!!
            assertEquals(2, result.subValues.size)

            val propertyRow = result.subValues.first { it.uri == TaskProperties.contentUri(DAV_TASKS_TEST_AUTHORITY) }
            assertEquals("Work", propertyRow.values.getAsString(TaskProperties.DATA1))

            val alarmRow = result.subValues.first { it.uri == TaskAlarms.contentUri(DAV_TASKS_TEST_AUTHORITY) }
            assertEquals("-PT30M", alarmRow.values.getAsString(TaskAlarms.TRIGGER_RELATIVE))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testUpdateTask_replacesPropertiesAndAlarms() {
        val taskList = createTaskList()
        try {
            val entity = Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._UID to "uid-update",
                Tasks.SUMMARY to "Original"
            )).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(asSyncAdapter = false),
                    contentValuesOf(TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY, TaskProperties.DATA1 to "Old")
                )
                addSubValue(
                    taskList.tasksAlarmsUri(asSyncAdapter = false),
                    contentValuesOf(TaskAlarms.ACTION to TaskAlarms.Action.DISPLAY, TaskAlarms.TRIGGER_RELATIVE to "-PT5M")
                )
            }
            val id = taskList.addTask(entity)

            val updated = Entity(contentValuesOf(
                Tasks._ID to id,
                Tasks.LIST_ID to taskList.id,
                Tasks._UID to "uid-update",
                Tasks.SUMMARY to "Updated"
            )).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(asSyncAdapter = false),
                    contentValuesOf(TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY, TaskProperties.DATA1 to "New")
                )
                // no alarm this time - must be gone after update
            }
            taskList.updateTask(id, updated)

            val result = taskList.getTask(id)!!
            assertEquals("Updated", result.entityValues.getAsString(Tasks.SUMMARY))
            assertEquals(1, result.subValues.size)
            assertEquals("New", result.subValues.first().values.getAsString(TaskProperties.DATA1))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testDeleteTask_cascadesSubRows() {
        val taskList = createTaskList()
        val entity = Entity(contentValuesOf(
            Tasks.LIST_ID to taskList.id,
            Tasks._UID to "uid-cascade",
            Tasks.SUMMARY to "To be deleted"
        )).apply {
            addSubValue(
                taskList.tasksPropertiesUri(asSyncAdapter = false),
                contentValuesOf(TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY, TaskProperties.DATA1 to "X")
            )
            addSubValue(
                taskList.tasksAlarmsUri(asSyncAdapter = false),
                contentValuesOf(TaskAlarms.ACTION to TaskAlarms.Action.DISPLAY, TaskAlarms.TRIGGER_RELATIVE to "-PT1M")
            )
        }
        try {
            val id = taskList.addTask(entity)
            assertEquals(1, taskList.deleteTask(id))
            assertNull(taskList.getTask(id))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testCountTasks() {
        val taskList = createTaskList()
        try {
            assertEquals(0, taskList.countTasks(null, null))

            taskList.addTask(Entity(contentValuesOf(Tasks.LIST_ID to taskList.id, Tasks._UID to "uid-1", Tasks.SUMMARY to "One")))
            taskList.addTask(Entity(contentValuesOf(Tasks.LIST_ID to taskList.id, Tasks._UID to "uid-2", Tasks.SUMMARY to "Two")))

            assertEquals(2, taskList.countTasks(null, null))
            assertEquals(1, taskList.countTasks("${Tasks._UID}=?", arrayOf("uid-1")))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testFindTask_bySyncId() {
        val taskList = createTaskList()
        try {
            taskList.addTask(Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._UID to "uid-find",
                Tasks._SYNC_ID to "find-me.ics",
                Tasks.SUMMARY to "Findable"
            )))

            val found = taskList.findTask("${Tasks._SYNC_ID}=?", arrayOf("find-me.ics"))
            assertNotNull(found)
            assertEquals("Findable", found?.entityValues?.getAsString(Tasks.SUMMARY))

            assertNull(taskList.findTask("${Tasks._SYNC_ID}=?", arrayOf("does-not-exist.ics")))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testQueryTasks() = runTest {
        val taskList = createTaskList()
        try {
            taskList.addTask(Entity(contentValuesOf(Tasks.LIST_ID to taskList.id, Tasks._UID to "uid-a", Tasks.SUMMARY to "A")))
            taskList.addTask(Entity(contentValuesOf(Tasks.LIST_ID to taskList.id, Tasks._UID to "uid-b", Tasks.SUMMARY to "B")))

            val results = taskList.queryTasks(null, null).toList()
            assertEquals(2, results.size)
            assertEquals(setOf("A", "B"), results.map { it.entityValues.getAsString(Tasks.SUMMARY) }.toSet())
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testUpdateTaskRow() {
        val taskList = createTaskList()
        try {
            val id = taskList.addTask(Entity(contentValuesOf(Tasks.LIST_ID to taskList.id, Tasks._UID to "uid-row", Tasks.SUMMARY to "Before")))

            taskList.updateTaskRow(id, contentValuesOf(Tasks.SUMMARY to "After"))

            assertEquals("After", taskList.getTaskRow(id)?.getAsString(Tasks.SUMMARY))
        } finally {
            taskList.delete()
        }
    }

}
