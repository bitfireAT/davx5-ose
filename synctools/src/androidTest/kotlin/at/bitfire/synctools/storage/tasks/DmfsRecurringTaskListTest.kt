/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.test.account.TestAccount
import at.bitfire.synctools.test.assertEntitiesEqual
import at.bitfire.synctools.test.assertExceptionsEqual
import at.bitfire.synctools.verifyCompat
import io.mockk.junit4.MockKRule
import io.mockk.spyk
import net.fortuna.ical4j.util.TimeZones
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Tests for [DmfsRecurringTaskList], which provides recurrence support for tasks.
 * Similar to [at.bitfire.synctools.storage.calendar.AndroidRecurringCalendarTest] for events.
 */
class DmfsRecurringTaskListTest(providerName: TaskProvider.ProviderName) :
    DmfsStyleProvidersTaskTest(providerName) {

    @get:Rule
    val mockkRule = MockKRule(this)

    private val timeZoneId = TimeZones.UTC_ID

    private lateinit var taskList: DmfsTaskList
    private lateinit var recurringTaskList: DmfsRecurringTaskList

    @Before
    override fun prepare() {
        super.prepare()

        // A real non-local account is required here:
        // - lists in local (TaskContract.LOCAL_ACCOUNT_TYPE) accounts are deleted immediately instead of being marked as _DELETED
        // - lists in fake non-local accounts are removed by the tasks provider as stale lists
        // The account is created once per class to avoid repeated AccountManager churn while
        // still creating a fresh list for every test method.
        taskList = TestTaskList.create(testAccount, providerName, provider)
        recurringTaskList = spyk(DmfsRecurringTaskList(taskList))
    }

    @After
    fun cleanUp() {
        // Clean up tasks after every test
        taskList.deleteTasks(null, null)
        taskList.delete()
    }

    companion object {

        private lateinit var testAccount: Account

        @BeforeClass
        @JvmStatic
        fun createTestAccount() {
            testAccount = TestAccount.create(accountName = DmfsRecurringTaskListTest::class.java.simpleName)
        }

        @AfterClass
        @JvmStatic
        fun removeTestAccount() {
            TestAccount.remove(testAccount)
        }

    }

    // test CRUD

    @Test
    fun testAddTaskAndExceptions_and_GetById() {
        // add task and exceptions
        val task = insertRecurring()

        // verify
        val task2 = recurringTaskList.getById(task.main.entityValues.getAsLong(Tasks._ID)!!)
        assertTaskAndExceptionsEqual(task, task2!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindTaskAndExceptions() {
        val task = insertRecurring(syncId = "testFindTaskAndExceptions")
        val result = recurringTaskList.findTaskAndExceptions("${Tasks._SYNC_ID}=?", arrayOf("testFindTaskAndExceptions"))
        assertTaskAndExceptionsEqual(task, result!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindTaskAndExceptions_IgnoresExceptionMatches() {
        insertRecurring()

        val result = recurringTaskList.findTaskAndExceptions("${Tasks.TITLE}=?", arrayOf("Exception"))

        assertNull(result)
    }

    @Test
    fun testFindTaskAndExceptions_NotFound() {
        assertNull(recurringTaskList.findTaskAndExceptions("${Tasks._SYNC_ID}=?", arrayOf("not-existent")))
    }

    @Test
    fun testGetById_ExceptionId_ReturnsNull() {
        val task = insertRecurring()
        val mainTaskId = task.main.entityValues.getAsLong(Tasks._ID)!!
        val exceptionId = taskList.findTaskRow(
            arrayOf(Tasks._ID),
            "${Tasks.ORIGINAL_INSTANCE_ID}=?",
            arrayOf(mainTaskId.toString())
        )!!.getAsLong(Tasks._ID)!!

        assertNull(recurringTaskList.getById(exceptionId))
    }

    @Test
    fun testGetById_NotFound() {
        // make sure there's no task with id=1
        recurringTaskList.deleteTaskAndExceptions(1)

        assertNull(recurringTaskList.getById(1))
    }

    @Test
    fun testIterateTaskAndExceptions() {
        val task1 = insertRecurring(syncId = "testIterateTaskAndExceptions1")
        val task2 = insertRecurring(syncId = "testIterateTaskAndExceptions2")
        val result = mutableListOf<TaskAndExceptions>()
        recurringTaskList.iterateTaskAndExceptions(
            "${Tasks._SYNC_ID} IN (?, ?)",
            arrayOf("testIterateTaskAndExceptions1", "testIterateTaskAndExceptions2")
        ) { result += it }
        val orderedResult = result.sortedBy { it.main.entityValues.getAsInteger(Tasks._ID) }
        assertEquals(2, orderedResult.size)
        assertTaskAndExceptionsEqual(task1, orderedResult[0], onlyFieldsInExpected = true)
        assertTaskAndExceptionsEqual(task2, orderedResult[1], onlyFieldsInExpected = true)
    }

    @Test
    fun testIterateTaskAndExceptions_IgnoresExceptionMatches() {
        insertRecurring()

        recurringTaskList.iterateTaskAndExceptions("${Tasks.TITLE}=?", arrayOf("Exception")) {
            fail("must not be called")
        }
    }

    @Test
    fun testIterateTaskAndExceptions_NotFound() {
        recurringTaskList.iterateTaskAndExceptions("${Tasks._SYNC_ID}=?", arrayOf("not-existent")) {
            fail("must not be called")
        }
    }

    @Test
    fun testUpdateTaskAndExceptions() {
        // Create initial task
        val now = 1754233504000L    // Sun Aug 03 2025 15:05:04 GMT+0000
        val initialTask = Entity(
            contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._SYNC_ID to "recur2",
                Tasks.DTSTART to now,
                Tasks.TZ to timeZoneId,
                Tasks.DURATION to "PT1H",
                Tasks.TITLE to "Initial Task",
                Tasks.RRULE to "FREQ=DAILY;COUNT=3"
            )
        )
        val initialExceptions = listOf(
            Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.ORIGINAL_INSTANCE_SYNC_ID to "recur2",
                    Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                    Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                    Tasks.DTSTART to now + 86400000,
                    Tasks.DUE to now + 86400000 + 2 * 3600000,
                    Tasks.TZ to timeZoneId,
                    Tasks.TITLE to "Initial Exception"
                )
            )
        )
        val initialTaskAndExceptions = TaskAndExceptions(main = initialTask, exceptions = initialExceptions)

        // Add initial task
        val addedTaskId = recurringTaskList.addTaskAndExceptions(initialTaskAndExceptions)

        // Update task
        val updatedTask = Entity(
            contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._SYNC_ID to "recur2",
                Tasks.DTSTART to now,
                Tasks.TZ to timeZoneId,
                Tasks.DURATION to "PT1H",
                Tasks.TITLE to "Updated Task",
                Tasks.RRULE to "FREQ=DAILY;COUNT=3"
            )
        )
        val updatedExceptions = listOf(
            Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.ORIGINAL_INSTANCE_SYNC_ID to "recur2",
                    Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                    Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                    Tasks.DTSTART to now + 86400000,
                    Tasks.DUE to now + 86400000 + 2 * 3600000,
                    Tasks.TZ to timeZoneId,
                    Tasks.TITLE to "Updated Exception"
                )
            )
        )
        val updatedTaskAndExceptions = TaskAndExceptions(main = updatedTask, exceptions = updatedExceptions)
        recurringTaskList.updateTaskAndExceptions(addedTaskId, updatedTaskAndExceptions)

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
        val mainTaskId = recurringTaskList.addTaskAndExceptions(
            TaskAndExceptions(
                main = Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._SYNC_ID to "recur4",
                        Tasks.DTSTART to now,
                        Tasks.TZ to timeZoneId,
                        Tasks.DURATION to "PT1H",
                        Tasks.TITLE to "Main Task",
                        Tasks.RRULE to "FREQ=DAILY;COUNT=3"
                    )
                ),
                exceptions = listOf(
                    Entity(
                        contentValuesOf(
                            Tasks.LIST_ID to taskList.id,
                            Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                            Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                            Tasks.DTSTART to now + 86400000,
                            Tasks.DUE to now + 86400000 + 2 * 3600000,
                            Tasks.TZ to timeZoneId,
                            Tasks.TITLE to "Exception"
                        )
                    )
                )
            )
        )

        // Delete task and exceptions
        recurringTaskList.deleteTaskAndExceptions(mainTaskId)

        // Verify deletion
        val deletedTask = recurringTaskList.getById(mainTaskId)
        assertNull(deletedTask)
    }


    // test validation / clean-up logic

    @Test
    fun testCleanUp_Recurring() {
        val mainTask = Entity(
            contentValuesOf(
                Tasks.TITLE to "Recurring Main Task",
                Tasks.RRULE to "Some RRULE"
            )
        )
        val exception = Entity(
            contentValuesOf(
                Tasks.TITLE to "Exception"
            )
        )
        val original = TaskAndExceptions(
            main = mainTask,
            exceptions = listOf(exception)
        )
        val cleaned = recurringTaskList.cleanUp(original, mainId = 123L)

        // verify cleanup methods were called
        verifyCompat(exactly = 1) {
            recurringTaskList.cleanMainTask(any())
            recurringTaskList.cleanException(any(), 123L)
        }

        // verify result is not the same as original
        assertNotSame(cleaned, original)
    }

    @Test
    fun testCleanUp_NotRecurring() {
        val cleaned = recurringTaskList.cleanUp(
            TaskAndExceptions(
                main = Entity(
                    contentValuesOf(
                        Tasks._SYNC_ID to "SomeSyncID",
                        Tasks.TITLE to "Non-Recurring Main Task"
                    )
                ),
                exceptions = listOf(
                    Entity(
                        contentValuesOf(
                            Tasks.TITLE to "Exception"
                        )
                    )
                )
            ),
            mainId = null
        )

        // verify that exceptions were dropped (because the main task is not recurring)
        assertTrue(cleaned.exceptions.isEmpty())
    }

    @Test
    fun testCleanMainTask() {
        val result = recurringTaskList.cleanMainTask(
            Entity(
                contentValuesOf(
                    Tasks.ORIGINAL_INSTANCE_ID to 123L,
                    Tasks.ORIGINAL_INSTANCE_SYNC_ID to "SomeValue",
                    Tasks.ORIGINAL_INSTANCE_TIME to 456L,
                    Tasks.ORIGINAL_INSTANCE_ALLDAY to 1
                )
            )
        )
        // all these fields should have been removed (they're for exceptions, not for a main task)
        assertEquals(0, result.entityValues.size())
    }

    @Test
    fun testCleanException() {
        val result = recurringTaskList.cleanException(
            Entity(
                contentValuesOf(
                    Tasks.RRULE to "SomeValue",
                    Tasks.RDATE to "SomeValue",
                    Tasks.EXDATE to "SomeValue",
                    Tasks.ORIGINAL_INSTANCE_ID to "SomeValue",
                    Tasks.ORIGINAL_INSTANCE_SYNC_ID to "SomeSyncID",
                    Tasks.ORIGINAL_INSTANCE_TIME to 456L,
                    Tasks.ORIGINAL_INSTANCE_ALLDAY to 0
                )
            ),
            mainId = 123L
        )

        // ORIGINAL_INSTANCE_ID should be reset to actual ID
        assertEquals(123L, result.entityValues.getAsLong(Tasks.ORIGINAL_INSTANCE_ID))

        // references to original task should have been kept
        assertEquals(456L, result.entityValues.getAsLong(Tasks.ORIGINAL_INSTANCE_TIME).toLong())
        assertEquals(0, result.entityValues.getAsInteger(Tasks.ORIGINAL_INSTANCE_ALLDAY).toInt())

        // recurrence fields and ORIGINAL_INSTANCE_SYNC_ID should have been dropped
        assertNull(result.entityValues.getAsString(Tasks.ORIGINAL_INSTANCE_SYNC_ID))
        assertNull(result.entityValues.getAsString(Tasks.RRULE))
        assertNull(result.entityValues.getAsString(Tasks.RDATE))
        assertNull(result.entityValues.getAsString(Tasks.EXDATE))
    }


    // test processing dirty/deleted tasks and exceptions

    /**
     * Tests processing _DELETED exceptions.
     *
     * Note: the used test account must not have accountType=[TaskContract.LOCAL_ACCOUNT_TYPE],
     * because then the tasks provider directly deletes tasks and doesn't mark them as [Tasks._DELETED].
     */
    @Test
    fun testProcessDeletedExceptions() {
        // Insert a recurring task with an exception
        val taskAndExceptions = insertRecurring()
        val mainTaskId = taskAndExceptions.main.entityValues.getAsLong(Tasks._ID)!!

        // Get the actual exception ID from the database
        val exceptionId = taskList.findTask("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(mainTaskId.toString()))!!.entityValues.getAsLong(Tasks._ID)!!

        // Mark the exception as deleted (delete, but not "as sync adapter"!)
        val exceptionUri = ContentUris.withAppendedId(Tasks.getContentUri(providerName.authority), exceptionId)
        taskList.client.delete(exceptionUri, null, null)

        // Verify: exception should still be here, but with _DELETED flag
        val exception = taskList.getTaskRow(exceptionId)
        assertNotNull(exception)

        // Process deleted exceptions
        recurringTaskList.processDeletedExceptions()

        // Verify: exception should be deleted
        val deletedException = taskList.getTaskRow(exceptionId)
        assertNull("Exception should be deleted", deletedException)

        // Verify: main task SYNC_VERSION should be increased by 1 (from 0 to 1)
        val mainTaskRow = taskList.getTaskRow(mainTaskId, arrayOf(Tasks.SYNC_VERSION, Tasks._DIRTY))
        assertEquals("1", mainTaskRow?.getAsString(Tasks.SYNC_VERSION))
        assertEquals(1L, mainTaskRow?.getAsLong(Tasks._DIRTY))
    }

    @Test
    fun testProcessDirtyExceptions() {
        // Insert a recurring task with an exception
        val taskAndExceptions = insertRecurring()
        val mainTaskId = taskAndExceptions.main.entityValues.getAsLong(Tasks._ID)!!

        // Get the actual exception ID from the database
        val exceptionId = taskList.findTask("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(mainTaskId.toString()))!!.entityValues.getAsLong(Tasks._ID)!!

        // Mark the exception as dirty (but not deleted)
        taskList.updateTaskRow(exceptionId, contentValuesOf(Tasks._DIRTY to 1, Tasks.SYNC_VERSION to 5))

        // Process dirty exceptions
        recurringTaskList.processDirtyExceptions()

        // Verify: exception SYNC_VERSION should be increased by 1 (from 5 to 6)
        val exceptionRow = taskList.getTaskRow(exceptionId, arrayOf(Tasks.SYNC_VERSION, Tasks._DIRTY))
        assertEquals(6L, exceptionRow?.getAsLong(Tasks.SYNC_VERSION))
        assertEquals(0L, exceptionRow?.getAsLong(Tasks._DIRTY))

        // Verify: main task should be marked as dirty
        val mainTaskRow = taskList.getTaskRow(mainTaskId, arrayOf(Tasks._DIRTY))
        assertEquals(1L, mainTaskRow?.getAsLong(Tasks._DIRTY))
    }


    // helpers

    private fun insertRecurring(syncId: String = UUID.randomUUID().toString()): TaskAndExceptions {
        val now = 1754233504000L     // Sun Aug 03 2025 15:05:04 GMT+0000
        val task = TaskAndExceptions(
            main = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks._SYNC_ID to syncId,
                    Tasks.DTSTART to now,
                    Tasks.TZ to timeZoneId,
                    Tasks.DURATION to "PT1H",
                    Tasks.TITLE to "Main Task",
                    Tasks.RRULE to "FREQ=DAILY;COUNT=3"
                )
            ),
            exceptions = listOf(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks.ORIGINAL_INSTANCE_TIME to now + 86400000,
                        Tasks.ORIGINAL_INSTANCE_ALLDAY to 0,
                        Tasks.DTSTART to now + 86400000,
                        Tasks.DUE to now + 86400000 + 2 * 3600000,
                        Tasks.TZ to timeZoneId,
                        Tasks.TITLE to "Exception"
                    )
                )
            )
        )
        val id = recurringTaskList.addTaskAndExceptions(task)
        return task.withTaskId(id)
    }


    // helpers

    private fun assertTaskAndExceptionsEqual(expected: TaskAndExceptions, actual: TaskAndExceptions, onlyFieldsInExpected: Boolean = false) {
        assertEntitiesEqual(expected.main, actual.main, onlyFieldsInExpected)
        assertExceptionsEqual(expected.exceptions, actual.exceptions, onlyFieldsInExpected) {
            it.entityValues.getAsLong(Tasks.ORIGINAL_INSTANCE_TIME)
        }
    }

    private fun Entity.withTaskId(taskId: Long): Entity =
        Entity(ContentValues(this.entityValues)).also { newEntity ->
            newEntity.entityValues.put(Tasks._ID, taskId)
            newEntity.subValues.addAll(subValues)
        }

    private fun TaskAndExceptions.withTaskId(mainTaskId: Long): TaskAndExceptions =
        TaskAndExceptions(
            main = main.withTaskId(mainTaskId),
            exceptions = exceptions.map { exception ->
                Entity(ContentValues(exception.entityValues)).also { newException ->
                    newException.entityValues.put(Tasks.ORIGINAL_INSTANCE_ID, mainTaskId)
                    newException.subValues.addAll(exception.subValues)
                }
            }
        )

}
