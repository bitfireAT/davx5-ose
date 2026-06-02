/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.TaskProvider
import at.bitfire.synctools.storage.plusAssign
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DmfsTaskListProviderTest(providerName: TaskProvider.ProviderName) :
    DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)
    private val dmfsTaskListProvider by lazy {
        DmfsTaskListProvider(testAccount, provider.client, providerName)
    }

    @Test
    fun testCreateTaskList() {
        val info = ContentValues()
        info.put(TaskLists.LIST_NAME, "Test Create Task List")
        info.put(TaskLists.LIST_COLOR, 0xff00ff00L)
        info.put(TaskLists.OWNER, "test@example.com")
        info.put(TaskLists.SYNC_ENABLED, 1)
        info.put(TaskLists.VISIBLE, 1)

        val id = dmfsTaskListProvider.createTaskList(info)
        try {
            assertTrue("Created task list ID should be positive", id > 0)
        } finally {
            dmfsTaskListProvider.deleteTaskList(id)
        }
    }

    @Test
    fun testCreateAndGetTaskList() {
        val taskList = createTaskList(
            contentValuesOf(
                TaskLists.LIST_NAME to "Test Create And Get",
                TaskLists.LIST_COLOR to 0xff0000ffL
            )
        )
        try {
            assertNotNull("Created task list should not be null", taskList)
            assertEquals("Test Create And Get", taskList.name)
            assertEquals(0xff0000ffL, taskList.values.getAsLong(TaskLists.LIST_COLOR))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testFindTaskLists() {
        val taskList1 = createTaskList(
            contentValuesOf(
                TaskLists.LIST_NAME to "Find Test List 1",
                TaskLists.OWNER to "findtasklists@example.com"
            )
        )
        val taskList2 = createTaskList(
            contentValuesOf(
                TaskLists.LIST_NAME to "Find Test List 2",
                TaskLists.OWNER to "findtasklists@example.com"
            )
        )

        try {
            val taskLists = dmfsTaskListProvider.findTaskLists(
                "${TaskLists.OWNER}=?",
                arrayOf("findtasklists@example.com"),
                "${TaskLists.LIST_NAME} ASC"
            )
            assertTrue("Should find at least 2 task lists", taskLists.size >= 2)
            assertEquals("Find Test List 1", taskLists[0].name)
            assertEquals("Find Test List 2", taskLists[1].name)
        } finally {
            taskList1.delete()
            taskList2.delete()
        }
    }

    @Test
    fun testFindFirstTaskList() {
        val taskList = createTaskList(
            contentValuesOf(
                TaskLists.LIST_NAME to "First Test List",
                TaskLists.OWNER to "first-test@example.com"
            )
        )

        try {
            // Test finding existing task list
            val found = dmfsTaskListProvider.findFirstTaskList(
                "${TaskLists.OWNER}=?",
                arrayOf("first-test@example.com")
            )
            assertNotNull("Should find task list", found)
            assertEquals("First Test List", found?.name)

            // Test not finding anything
            val notFound = dmfsTaskListProvider.findFirstTaskList(
                "${TaskLists.OWNER}=?",
                arrayOf("nonexistent@example.com")
            )
            assertNull("Should not find task list", notFound)
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testGetTaskListRow_withProjection() {
        val taskList = createTaskList(
            contentValuesOf(
                TaskLists.LIST_NAME to "Test Task List"
            )
        )
        try {
            val result = taskList.provider.getTaskListRow(taskList.id, arrayOf(TaskLists.LIST_NAME))
            assertNotNull(result)
            // Verify that only the requested field is present
            assertEquals("Test Task List", result?.getAsString(TaskLists.LIST_NAME))
            assertNull(result?.getAsString(TaskLists.OWNER))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testGetTaskListRow_notFound() {
        // must not be present because we always clean up after tests
        val result = dmfsTaskListProvider.getTaskListRow(999)
        assertNull(result)
    }

    @Test
    fun testGetTaskList() {
        val taskList = createTaskList(
            contentValuesOf(
                TaskLists.LIST_NAME to "Get Test List",
                TaskLists.LIST_COLOR to 0xffff00ffL
            )
        )

        try {
            val result = dmfsTaskListProvider.getTaskList(taskList.id)
            assertNotNull("Should find task list by ID", result)
            assertEquals("Get Test List", result?.name)
            assertEquals(0xffff00ffL, result?.values?.getAsLong(TaskLists.LIST_COLOR))

            // Test with non-existent ID (doesn't need cleanup)
            val notFound = dmfsTaskListProvider.getTaskList(99999)
            assertNull("Should not find non-existent task list", notFound)
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testUpdateTaskList() {
        val info = contentValuesOf(
            TaskLists.LIST_NAME to "Update Test List",
            TaskLists.LIST_COLOR to 0xff00ffffL
        )
        val taskList = createTaskList(info)

        try {
            val updateValues = ContentValues()
            updateValues.put(TaskLists.LIST_NAME, "Updated List Name")
            updateValues.put(TaskLists.LIST_COLOR, 0xff000000L)

            val updatedCount = dmfsTaskListProvider.updateTaskList(taskList.id, updateValues)
            assertEquals("Should update exactly one row", 1, updatedCount)

            val result = dmfsTaskListProvider.getTaskList(taskList.id)
            assertNotNull("Task list should still exist", result)
            assertEquals("Updated List Name", result?.name)
            assertEquals(0xff000000L, result?.values?.getAsLong(TaskLists.LIST_COLOR))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testDeleteTaskList() {
        val id = dmfsTaskListProvider.createTaskList(
            contentValuesOf(
                TaskLists.LIST_NAME to "Delete Test List"
            )
        )

        try {
            // Verify task list exists
            val taskListBefore = dmfsTaskListProvider.getTaskList(id)
            assertNotNull("Task list should exist before deletion", taskListBefore)

            // Delete it
            val deleted = dmfsTaskListProvider.deleteTaskList(id)
            assertTrue("Should successfully delete task list", deleted)

            // Verify it's gone
            val taskListAfter = dmfsTaskListProvider.getTaskList(id)
            assertNull("Task list should not exist after deletion", taskListAfter)

            // Test deleting non-existent ID (doesn't need cleanup)
            val notDeleted = dmfsTaskListProvider.deleteTaskList(99999)
            assertFalse("Should not delete non-existent task list", notDeleted)
        } finally {
            dmfsTaskListProvider.deleteTaskList(id)
        }
    }

    private fun createTaskList(overwriteValues: ContentValues = ContentValues()): DmfsTaskList {
        val cv = contentValuesOf(
            TaskLists.ACCOUNT_NAME to testAccount.name,
            TaskLists.ACCOUNT_TYPE to testAccount.type,
            TaskLists.LIST_NAME to UUID.randomUUID().toString(),
            TaskLists.LIST_COLOR to 0xffff0000L,
            TaskLists.OWNER to "test@example.com",
            TaskLists.SYNC_ENABLED to 1,
            TaskLists.VISIBLE to 1
        )
        cv += overwriteValues

        val id = dmfsTaskListProvider.createTaskList(cv)
        return dmfsTaskListProvider.getTaskList(id)!!
    }

}