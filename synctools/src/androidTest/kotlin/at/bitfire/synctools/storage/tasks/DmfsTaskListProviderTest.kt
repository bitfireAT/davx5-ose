/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentValues
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.TaskProvider
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DmfsTaskListProviderTest(providerName: TaskProvider.ProviderName) :
    DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)
    private val dmfsTaskListProvider by lazy {
        DmfsTaskListProvider(testAccount, provider.client, providerName)
    }

    @Test
    fun testGetTaskListRow_withProjection() {
        val taskList = createTaskList()
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

    private fun createTaskList(): DmfsTaskList {
        val info = ContentValues()
        info.put(TaskLists.LIST_NAME, "Test Task List")
        info.put(TaskLists.LIST_COLOR, 0xffff0000)
        info.put(TaskLists.OWNER, "test@example.com")
        info.put(TaskLists.SYNC_ENABLED, 1)
        info.put(TaskLists.VISIBLE, 1)

        val id = dmfsTaskListProvider.createTaskList(info)
        assertNotNull(id)

        return dmfsTaskListProvider.getTaskList(id)!!
    }

}