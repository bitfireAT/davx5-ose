/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.test.assertTaskAndExceptionsEqual
import at.bitfire.synctools.test.withTaskId
import at.bitfire.synctools.verifyCompat
import io.mockk.junit4.MockKRule
import io.mockk.spyk
import net.fortuna.ical4j.util.TimeZones
import org.dmfs.tasks.contract.TaskContract
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Tests for [DmfsRecurringTaskList], which provides recurrence support for tasks.
 * Similar to [at.bitfire.synctools.storage.calendar.AndroidRecurringCalendarTest] for events.
 */
class DmfsRecurringTaskListTest(providerName: TaskProvider.ProviderName) :
    DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(DmfsRecurringTaskListTest::class.java.name, TaskContract.LOCAL_ACCOUNT_TYPE)
    private val timeZoneId = TimeZones.UTC_ID

    private lateinit var taskList: DmfsTaskList
    private lateinit var recurringTaskList: DmfsRecurringTaskList

    @get:Rule
    val mockkRule = MockKRule(this)

    @Before
    override fun prepare() {
        super.prepare()

        // Create a test task list
        val info = ContentValues().apply {
            put(TaskContract.TaskLists.LIST_NAME, "Test Recurring Task List")
            put(TaskContract.TaskLists.LIST_COLOR, 0xffff0000)
            put(TaskContract.TaskLists.OWNER, "test@example.com")
            put(TaskContract.TaskLists.SYNC_ENABLED, 1)
            put(TaskContract.TaskLists.VISIBLE, 1)
        }

        val dmfsTaskListProvider = DmfsTaskListProvider(testAccount, provider.client, providerName)
        val id = dmfsTaskListProvider.createTaskList(info)
        taskList = dmfsTaskListProvider.getTaskList(id)!!
        recurringTaskList = spyk(DmfsRecurringTaskList(taskList))
    }

    @After
    fun cleanUp() {
        // Clean up tasks after every test
        taskList.deleteTasks(null, null)
    }

    companion object {
        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            // Clean up will be handled by the test framework
        }
    }


    // test CRUD

    @Test
    fun testAddTaskAndExceptions_and_GetById() {
        // add task and exceptions
        val (mainTaskId, task) = insertRecurring()
        val addedWithId = task.withTaskId(mainTaskId)

        // verify that cleanUp was called
        verifyCompat(exactly = 1) {
            recurringTaskList.cleanUp(task)
        }

        // verify
        val task2 = recurringTaskList.getById(mainTaskId)
        assertTaskAndExceptionsEqual(addedWithId, task2!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindTaskAndExceptions() {
        val (mainTaskId, task) = insertRecurring(syncId = "testFindTaskAndExceptions")
        val addedWithId = task.withTaskId(mainTaskId)
        val result = recurringTaskList.findTaskAndExceptions("${TaskContract.Tasks._SYNC_ID}=?", arrayOf("testFindTaskAndExceptions"))
        assertTaskAndExceptionsEqual(addedWithId, result!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindTaskAndExceptions_NotFound() {
        assertNull(recurringTaskList.findTaskAndExceptions("${TaskContract.Tasks._SYNC_ID}=?", arrayOf("not-existent")))
    }

    @Test
    fun testGetById_NotFound() {
        // make sure there's no task with id=1
        recurringTaskList.deleteTaskAndExceptions(1)

        assertNull(recurringTaskList.getById(1))
    }

    @Test
    fun testIterateTaskAndExceptions() {
        val (id1, task1) = insertRecurring(syncId = "testIterateTaskAndExceptions1")
        val (id2, task2) = insertRecurring(syncId = "testIterateTaskAndExceptions2")
        val result = mutableListOf<TaskAndExceptions>()
        recurringTaskList.iterateTaskAndExceptions(
            "${TaskContract.Tasks._SYNC_ID} IN (?, ?)",
            arrayOf("testIterateTaskAndExceptions1", "testIterateTaskAndExceptions2")
        ) { result += it }
        val orderedResult = result.sortedBy { it.main.entityValues.getAsInteger(TaskContract.Tasks._ID) }
        assertEquals(2, orderedResult.size)
        assertTaskAndExceptionsEqual(task1.withTaskId(id1), orderedResult[0], onlyFieldsInExpected = true)
        assertTaskAndExceptionsEqual(task2.withTaskId(id2), orderedResult[1], onlyFieldsInExpected = true)
    }

    @Test
    fun testIterateTaskAndExceptions_NotFound() {
        recurringTaskList.iterateTaskAndExceptions("${TaskContract.Tasks._SYNC_ID}=?", arrayOf("not-existent")) {
            fail("must not be called")
        }
    }

    @Test
    fun testUpdateTaskAndExceptions() {
        // Create initial task
        val now = 1754233504000L    // Sun Aug 03 2025 15:05:04 GMT+0000
        val initialTask = Entity(
            contentValuesOf(
                TaskContract.Tasks.LIST_ID to taskList.id,
                TaskContract.Tasks._SYNC_ID to "recur2",
                TaskContract.Tasks.DTSTART to now,
                TaskContract.Tasks.TZ to timeZoneId,
                TaskContract.Tasks.DURATION to "PT1H",
                TaskContract.Tasks.TITLE to "Initial Task",
                TaskContract.Tasks.RRULE to "FREQ=DAILY;COUNT=3"
            )
        )
        val initialExceptions = listOf(
            Entity(
                contentValuesOf(
                    TaskContract.Tasks.LIST_ID to taskList.id,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID to "recur2",
                    TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                    TaskContract.Tasks.DTSTART to now + 86400000,
                    TaskContract.Tasks.DUE to now + 86400000 + 2 * 3600000,
                    TaskContract.Tasks.TZ to timeZoneId,
                    TaskContract.Tasks.TITLE to "Initial Exception"
                )
            )
        )
        val initialTaskAndExceptions = TaskAndExceptions(main = initialTask, exceptions = initialExceptions)

        // Add initial task
        val addedTaskId = recurringTaskList.addTaskAndExceptions(initialTaskAndExceptions)

        // Create updated task
        val updatedTask = Entity(
            contentValuesOf(
                TaskContract.Tasks.LIST_ID to taskList.id,
                TaskContract.Tasks._SYNC_ID to "recur2",
                TaskContract.Tasks.DTSTART to now,
                TaskContract.Tasks.TZ to timeZoneId,
                TaskContract.Tasks.DURATION to "PT1H",
                TaskContract.Tasks.TITLE to "Updated Task",
                TaskContract.Tasks.RRULE to "FREQ=DAILY;COUNT=3"
            )
        )
        val updatedExceptions = listOf(
            Entity(
                contentValuesOf(
                    TaskContract.Tasks.LIST_ID to taskList.id,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID to "recur2",
                    TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                    TaskContract.Tasks.DTSTART to now + 86400000,
                    TaskContract.Tasks.DUE to now + 86400000 + 2 * 3600000,
                    TaskContract.Tasks.TZ to timeZoneId,
                    TaskContract.Tasks.TITLE to "Updated Exception"
                )
            )
        )
        val updatedTaskAndExceptions = TaskAndExceptions(main = updatedTask, exceptions = updatedExceptions)

        // Update task
        val updatedTaskId = recurringTaskList.updateTaskAndExceptions(addedTaskId, updatedTaskAndExceptions)
        assertEquals(updatedTaskId, addedTaskId)

        // verify that cleanUp was called
        verifyCompat(exactly = 1) {
            recurringTaskList.cleanUp(updatedTaskAndExceptions)
        }

        // Verify update
        val task2 = recurringTaskList.getById(addedTaskId)
        assertTaskAndExceptionsEqual(
            updatedTaskAndExceptions.withTaskId(addedTaskId),
            task2!!,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun testDeleteTaskAndExceptions() {
        // Add task with exceptions
        val now = 1754233504000L    // Sun Aug 03 2025 15:05:04 GMT+0000
        val mainTask = Entity(
            contentValuesOf(
                TaskContract.Tasks.LIST_ID to taskList.id,
                TaskContract.Tasks._SYNC_ID to "recur4",
                TaskContract.Tasks.DTSTART to now,
                TaskContract.Tasks.TZ to timeZoneId,
                TaskContract.Tasks.DURATION to "PT1H",
                TaskContract.Tasks.TITLE to "Main Task",
                TaskContract.Tasks.RRULE to "FREQ=DAILY;COUNT=3"
            )
        )
        val exceptions = listOf(
            Entity(
                contentValuesOf(
                    TaskContract.Tasks.LIST_ID to taskList.id,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                    TaskContract.Tasks.DTSTART to now + 86400000,
                    TaskContract.Tasks.DUE to now + 86400000 + 2 * 3600000,
                    TaskContract.Tasks.TZ to timeZoneId,
                    TaskContract.Tasks.TITLE to "Exception"
                )
            )
        )
        val mainTaskId = recurringTaskList.addTaskAndExceptions(TaskAndExceptions(main = mainTask, exceptions = exceptions))

        // Delete task and exceptions
        recurringTaskList.deleteTaskAndExceptions(mainTaskId)

        // Verify deletion
        val deletedTask = recurringTaskList.getById(mainTaskId)
        assertNull(deletedTask)
    }


    // test validation / clean-up logic

    @Test
    fun testCleanUp_Recurring_Exceptions_NoSyncId() {
        val cleaned = recurringTaskList.cleanUp(
            TaskAndExceptions(
                main = Entity(
                    contentValuesOf(
                        TaskContract.Tasks.TITLE to "Recurring Main Task",
                        TaskContract.Tasks.RRULE to "Some RRULE"
                    )
                ),
                exceptions = listOf(
                    Entity(
                        contentValuesOf(
                            TaskContract.Tasks.TITLE to "Exception"
                        )
                    )
                )
            )
        )

        // verify that exceptions were dropped (because the provider wouldn't be able to associate them without SYNC_ID)
        assertTrue(cleaned.exceptions.isEmpty())
    }

    @Test
    fun testCleanUp_Recurring_Exceptions_WithSyncId() {
        val original = TaskAndExceptions(
            main = Entity(
                contentValuesOf(
                    TaskContract.Tasks._SYNC_ID to "SomeSyncId",
                    TaskContract.Tasks.TITLE to "Recurring Main Task",
                    TaskContract.Tasks.RRULE to "Some RRULE"
                )
            ),
            exceptions = listOf(
                Entity(
                    contentValuesOf(
                        TaskContract.Tasks.TITLE to "Exception",
                        TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID to "SomeSyncId",
                        TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to 456L,
                        TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0
                    )
                )
            )
        )
        val cleaned = recurringTaskList.cleanUp(original)

        // verify that cleanUp didn't modify anything
        assertTaskAndExceptionsEqual(original, cleaned)
    }

    @Test
    fun testCleanUp_NotRecurring_Exceptions() {
        val cleaned = recurringTaskList.cleanUp(
            TaskAndExceptions(
                main = Entity(
                    contentValuesOf(
                        TaskContract.Tasks._SYNC_ID to "SomeSyncID",
                        TaskContract.Tasks.TITLE to "Non-Recurring Main Task"
                    )
                ),
                exceptions = listOf(
                    Entity(
                        contentValuesOf(
                            TaskContract.Tasks.TITLE to "Exception"
                        )
                    )
                )
            )
        )

        // verify that exceptions were dropped (because the main task is not recurring)
        assertTrue(cleaned.exceptions.isEmpty())
    }

    @Test
    fun testCleanMainTask_RemovesOriginalFields() {
        val result = recurringTaskList.cleanMainTask(
            Entity(
                contentValuesOf(
                    TaskContract.Tasks.ORIGINAL_INSTANCE_ID to 123L,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID to "SomeValue",
                    TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to 456L,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 1
                )
            )
        )
        assertEquals(0, result.entityValues.size())
    }

    @Test
    fun testCleanException_RemovesRecurrenceFields_AddsSyncId_PreservesOriginalInstanceFields() {
        val result = recurringTaskList.cleanException(
            Entity(
                contentValuesOf(
                    TaskContract.Tasks.RRULE to "SomeValue",
                    TaskContract.Tasks.RDATE to "SomeValue",
                    TaskContract.Tasks.EXDATE to "SomeValue",
                    TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to 456L,
                    TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0
                )
            ), "SomeSyncID"
        )

        // all fields should have been dropped, but ORIGINAL_INSTANCE_SYNC_ID should have been added
        assertEquals(
            "SomeSyncID",
            result.entityValues.getAsString(TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID)
        )
        assertEquals(456L, result.entityValues.getAsLong(TaskContract.Tasks.ORIGINAL_INSTANCE_TIME).toLong())
        assertEquals(0, result.entityValues.getAsInteger(TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY).toInt())
        assertNull(result.entityValues.getAsString(TaskContract.Tasks.RRULE))
        assertNull(result.entityValues.getAsString(TaskContract.Tasks.RDATE))
        assertNull(result.entityValues.getAsString(TaskContract.Tasks.EXDATE))
    }


    // test processing dirty/deleted tasks and exceptions

    @Ignore("Tasks.org does not currently expose a stable provider-backed deleted-exception setup for this case")
    @Test
    fun testProcessDeletedExceptions() {
        val now = System.currentTimeMillis()
        val keptInstanceTime = now + 86400000
        val deletedInstanceTime = now + 2 * 86400000
        val mainValues = contentValuesOf(
            TaskContract.Tasks._SYNC_ID to "testProcessDeletedExceptions",
            TaskContract.Tasks.LIST_ID to taskList.id,
            TaskContract.Tasks.DTSTART to now,
            TaskContract.Tasks.TZ to timeZoneId,
            TaskContract.Tasks.DURATION to "PT1H",
            TaskContract.Tasks.RRULE to "FREQ=DAILY;COUNT=5",
            TaskContract.Tasks._DIRTY to 0,
            TaskContract.Tasks.SYNC_VERSION to 15
        )
        val exNotDeleted = Entity(
            contentValuesOf(
                TaskContract.Tasks.LIST_ID to taskList.id,
                TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to keptInstanceTime,
                TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                TaskContract.Tasks.DTSTART to keptInstanceTime,
                TaskContract.Tasks.TZ to timeZoneId,
                TaskContract.Tasks.TITLE to "not marked as deleted",
                TaskContract.Tasks._DIRTY to 0
            )
        )
        val mainId = recurringTaskList.addTaskAndExceptions(
            TaskAndExceptions(
                main = Entity(mainValues),
                exceptions = listOf(
                    exNotDeleted,
                    Entity(
                        contentValuesOf(
                            TaskContract.Tasks.LIST_ID to taskList.id,
                            TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to deletedInstanceTime,
                            TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                            TaskContract.Tasks.DTSTART to deletedInstanceTime,
                            TaskContract.Tasks.TZ to timeZoneId,
                            TaskContract.Tasks._DIRTY to 1,
                            TaskContract.Tasks.TITLE to "marked as deleted"
                        )
                    )
                )
            )
        )

        val deletedInstanceId = recurringTaskList.getById(mainId)!!
            .exceptions
            .single { it.entityValues.getAsString(TaskContract.Tasks.TITLE) == "marked as deleted" }
            .entityValues
            .getAsLong(TaskContract.Tasks._ID)
        val instancesUri = TaskContract.Instances.getContentUri(providerName.authority)
            .asSyncAdapter(taskList.account)
        val instanceRowId = taskList.client.query(
            instancesUri,
            arrayOf(TaskContract.Instances._ID),
            "${TaskContract.Instances.TASK_ID}=? AND ${TaskContract.Instances.INSTANCE_ORIGINAL_TIME}=?",
            arrayOf(deletedInstanceId.toString(), deletedInstanceTime.toString()),
            null
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
        taskList.client.delete(
            ContentUris.withAppendedId(instancesUri, instanceRowId),
            null,
            null
        )

        // should update main task and purge the deleted exception
        recurringTaskList.processDeletedExceptions()

        val result = recurringTaskList.getById(mainId)!!
        val expectedMainValues = ContentValues(mainValues).apply {
            put(TaskContract.Tasks._DIRTY, 1)
            put(TaskContract.Tasks.SYNC_VERSION, 16)
        }
        val expectedTaskAndExceptions = TaskAndExceptions(
            main = Entity(expectedMainValues),
            exceptions = listOf(exNotDeleted)
        )
        assertTaskAndExceptionsEqual(expectedTaskAndExceptions, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testProcessDirtyExceptions() {
        val now = System.currentTimeMillis()
        val mainValues = contentValuesOf(
            TaskContract.Tasks._SYNC_ID to "testProcessDirtyExceptions",
            TaskContract.Tasks.LIST_ID to taskList.id,
            TaskContract.Tasks.DTSTART to now,
            TaskContract.Tasks.TZ to timeZoneId,
            TaskContract.Tasks.DURATION to "PT1H",
            TaskContract.Tasks.RRULE to "FREQ=DAILY;COUNT=5",
            TaskContract.Tasks._DIRTY to 0,
            TaskContract.Tasks.SYNC_VERSION to 15
        )
        val exDirtyValues = contentValuesOf(
            TaskContract.Tasks.LIST_ID to taskList.id,
            TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to now,
            TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
            TaskContract.Tasks.DTSTART to now,
            TaskContract.Tasks.TZ to timeZoneId,
            TaskContract.Tasks._DIRTY to 1,
            TaskContract.Tasks.TITLE to "marked as dirty",
            TaskContract.Tasks.SYNC_VERSION to null
        )
        val mainId = recurringTaskList.addTaskAndExceptions(
            TaskAndExceptions(
                main = Entity(mainValues),
                exceptions = listOf(Entity(exDirtyValues))
            )
        )

        // should mark main task as dirty and increase exception SEQUENCE
        recurringTaskList.processDirtyExceptions()

        val result = recurringTaskList.getById(mainId)!!
        val expectedMainValues = ContentValues(mainValues).apply {
            put(TaskContract.Tasks._DIRTY, 1)
        }
        val expectedExDirtyValues = ContentValues(exDirtyValues).apply {
            put(TaskContract.Tasks._DIRTY, 0)
            put(TaskContract.Tasks.SYNC_VERSION, 1)
        }
        assertTaskAndExceptionsEqual(
            TaskAndExceptions(
                main = Entity(expectedMainValues),
                exceptions = listOf(Entity(expectedExDirtyValues))
            ), result, onlyFieldsInExpected = true
        )
    }


    // helpers

    private fun insertRecurring(syncId: String = UUID.randomUUID().toString()): Pair<Long, TaskAndExceptions> {
        val now = 1754233504000L     // Sun Aug 03 2025 15:05:04 GMT+0000
        val mainTask = Entity(
            contentValuesOf(
                TaskContract.Tasks.LIST_ID to taskList.id,
                TaskContract.Tasks._SYNC_ID to syncId,
                TaskContract.Tasks.DTSTART to now,
                TaskContract.Tasks.TZ to timeZoneId,
                TaskContract.Tasks.DURATION to "PT1H",
                TaskContract.Tasks.TITLE to "Main Task",
                TaskContract.Tasks.RRULE to "FREQ=DAILY;COUNT=3"
            )
        )
        val task = TaskAndExceptions(
            main = mainTask,
            exceptions = listOf(
                Entity(
                    contentValuesOf(
                        TaskContract.Tasks.LIST_ID to taskList.id,
                        TaskContract.Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                        TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                        TaskContract.Tasks.DTSTART to now + 86400000,
                        TaskContract.Tasks.DUE to now + 86400000 + 2 * 3600000,
                        TaskContract.Tasks.TZ to timeZoneId,
                        TaskContract.Tasks.TITLE to "Exception"
                    )
                )
            )
        )
        val id = recurringTaskList.addTaskAndExceptions(task)
        return id to task
    }

}
