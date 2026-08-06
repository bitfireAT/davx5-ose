/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.accounts.Account
import android.content.ContentProviderClient
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.tasks.contract.TaskLists
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DavTaskListProviderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAccount = Account(DavTaskListProviderTest::class.java.name, "at.bitfire.synctools.test.davtasks.account")

    private lateinit var client: ContentProviderClient
    private lateinit var provider: DavTaskListProvider

    @Before
    fun setUp() {
        client = context.contentResolver.acquireContentProviderClient(DAV_TASKS_TEST_AUTHORITY)!!
        provider = DavTaskListProvider(testAccount, client, DAV_TASKS_TEST_AUTHORITY)
    }

    @After
    fun tearDown() {
        provider.findTaskLists().forEach { it.delete() }
        assertEquals(0, provider.findTaskLists().size)
        client.close()
    }

    private fun sampleValues(name: String = "Test List") = contentValuesOf(
        TaskLists._SYNC_ID to "1",
        TaskLists.LIST_NAME to name,
        TaskLists.SUPPORTED_COMPONENTS to TaskLists.Component.VTODO
    )


    @Test
    fun testCreateAndGetTaskList() {
        val taskList = provider.createAndGetTaskList(sampleValues())

        assertEquals(testAccount.name, taskList.values.getAsString(TaskLists.ACCOUNT_NAME))
        assertEquals(testAccount.type, taskList.values.getAsString(TaskLists.ACCOUNT_TYPE))
        assertEquals("Test List", taskList.name)
        assertEquals("1", taskList.syncId)

        assertEquals(true, provider.deleteTaskList(taskList.id))
    }

    @Test
    fun testFindTaskLists_empty() {
        assertEquals(0, provider.findTaskLists().size)
    }

    @Test
    fun testFindTaskLists_multiple() {
        provider.createTaskList(sampleValues("List A"))
        provider.createTaskList(sampleValues("List B"))

        assertEquals(2, provider.findTaskLists().size)
    }

    @Test
    fun testFindTaskLists_withWhere() {
        provider.createTaskList(sampleValues("List A"))
        provider.createTaskList(sampleValues("List B"))

        val lists = provider.findTaskLists(
            where = "${TaskLists.LIST_NAME} = ?",
            whereArgs = arrayOf("List A")
        )
        assertEquals(1, lists.size)
        assertEquals("List A", lists[0].name)
    }

    @Test
    fun testFindFirstTaskList_found() {
        provider.createTaskList(sampleValues())

        val taskList = provider.findFirstTaskList(null, null)
        assertNotNull(taskList)
        assertEquals("Test List", taskList!!.name)
    }

    @Test
    fun testFindFirstTaskList_notFound() {
        assertNull(provider.findFirstTaskList(null, null))
    }

    @Test
    fun testGetTaskList_found() {
        val id = provider.createTaskList(sampleValues())

        val taskList = provider.getTaskList(id)
        assertNotNull(taskList)
        assertEquals(id, taskList!!.id)
    }

    @Test
    fun testGetTaskList_notFound() {
        assertNull(provider.getTaskList(Long.MAX_VALUE))
    }

    @Test
    fun testUpdateTaskList() {
        val id = provider.createTaskList(sampleValues())

        val updatedRows = provider.updateTaskList(id, contentValuesOf(TaskLists.LIST_NAME to "Updated Name"))
        assertEquals(1, updatedRows)
        assertEquals("Updated Name", provider.getTaskList(id)?.name)
    }

    @Test
    fun testDeleteTaskList() {
        val id = provider.createTaskList(sampleValues())
        assertEquals(1, provider.findTaskLists().size)

        assertEquals(true, provider.deleteTaskList(id))
        assertEquals(0, provider.findTaskLists().size)
    }

}
