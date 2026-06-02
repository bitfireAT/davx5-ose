/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentValues
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
        val taskList = createTaskList(ContentValues().apply {
            put(TaskLists.LIST_NAME, "Test Create And Get")
            put(TaskLists.LIST_COLOR, 0xff0000ffL)
        })
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
        val taskList1 = createTaskList(ContentValues().apply {
            put(TaskLists.LIST_NAME, "Find Test List 1")
            put(TaskLists.OWNER, "findtasklists@example.com")
        })
        val taskList2 = createTaskList(ContentValues().apply {
            put(TaskLists.LIST_NAME, "Find Test List 2")
            put(TaskLists.OWNER, "findtasklists@example.com")
        })

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
        val taskList = createTaskList(ContentValues().apply {
            put(TaskLists.LIST_NAME, "First Test List")
            put(TaskLists.OWNER, "first-test@example.com")
        })

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
        val taskList = createTaskList(ContentValues().apply {
            put(TaskLists.LIST_NAME, "Test Task List")
        })
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
        val taskList = createTaskList(ContentValues().apply {
            put(TaskLists.LIST_NAME, "Get Test List")
            put(TaskLists.LIST_COLOR, 0xffff00ffL)
        })

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
        val info = ContentValues().apply {
            put(TaskLists.LIST_NAME, "Update Test List")
            put(TaskLists.LIST_COLOR, 0xff00ffffL)
        }
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
        val id = dmfsTaskListProvider.createTaskList(ContentValues().apply {
            put(TaskLists.LIST_NAME, "Delete Test List")
        })

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
        val cv = ContentValues().apply {
            put(TaskLists.ACCOUNT_NAME, testAccount.name)
            put(TaskLists.ACCOUNT_TYPE, testAccount.type)
            put(TaskLists.LIST_NAME, UUID.randomUUID().toString())
            put(TaskLists.LIST_COLOR, 0xffff0000L)
            put(TaskLists.OWNER, "test@example.com")
            put(TaskLists.SYNC_ENABLED, 1)
            put(TaskLists.VISIBLE, 1)
        }
        cv += overwriteValues

        val id = dmfsTaskListProvider.createTaskList(cv)
        return dmfsTaskListProvider.getTaskList(id)!!
    }

}