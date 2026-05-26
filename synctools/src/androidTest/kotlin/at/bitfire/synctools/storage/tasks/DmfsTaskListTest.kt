/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.database.DatabaseUtils
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmfsTaskListTest(providerName: TaskProvider.ProviderName):
    DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)

    private fun createTaskList(): DmfsTaskList {
        val info = ContentValues()
        info.put(TaskContract.TaskLists.LIST_NAME, "Test Task List")
        info.put(TaskContract.TaskLists.LIST_COLOR, 0xffff0000)
        info.put(TaskContract.TaskLists.OWNER, "test@example.com")
        info.put(TaskContract.TaskLists.SYNC_ENABLED, 1)
        info.put(TaskContract.TaskLists.VISIBLE, 1)

        val dmfsTaskListProvider = DmfsTaskListProvider(testAccount, provider.client, providerName)
        val id = dmfsTaskListProvider.createTaskList(info)
        assertNotNull(id)

        return dmfsTaskListProvider.getTaskList(id)!!
    }

    @Test
    fun testCountTasks_empty() {
        val taskList = createTaskList()
        try {
            val count = taskList.countTasks(null, null)
            assertEquals(0, count)
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testCountTasks_withFilter() {
        val taskList = createTaskList()
        try {
            // Add tasks with different UIDs
            val task1 = Task().apply {
                uid = "filter-uid-1"
                summary = "Filter Test 1"
            }
            val task2 = Task().apply {
                uid = "filter-uid-2"
                summary = "Filter Test 2"
            }
            
            DmfsTask(taskList, task1, "sync-id-1", null, 0).add()
            DmfsTask(taskList, task2, "sync-id-2", null, 0).add()
            
            // Test counting with UID filter
            val filteredCount = taskList.countTasks("${TaskContract.Tasks._UID}=?", arrayOf("filter-uid-1"))
            assertEquals(1, filteredCount)
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testCountTasks_withoutFilter() {
        val taskList = createTaskList()
        try {
            // Add multiple tasks
            val task1 = Task().apply {
                uid = "task-1"
                summary = "Test Task 1"
            }
            val task2 = Task().apply {
                uid = "task-2"
                summary = "Test Task 2"
            }

            DmfsTask(taskList, task1, "sync-id-1", null, 0).add()
            DmfsTask(taskList, task2, "sync-id-2", null, 0).add()

            val count = taskList.countTasks(null, null)
            assertEquals(2, count)
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testTouchRelations() {
        val taskList = createTaskList()
        try {
            val parent = Task()
            parent.uid = "parent"
            parent.summary = "Parent task"

            val child = Task()
            child.uid = "child"
            child.summary = "Child task"
            child.relatedTo.add(RelatedTo(parent.uid))

            // insert child before parent
            val childContentUri = DmfsTask(
                taskList,
                child,
                "452a5672-e2b0-434e-92b4-bc70a7a51ef2",
                null,
                0
            ).add()
            val childId = ContentUris.parseId(childContentUri)
            val parentContentUri = DmfsTask(
                taskList,
                parent,
                "452a5672-e2b0-434e-92b4-bc70a7a51ef2",
                null,
                0
            ).add()
            val parentId = ContentUris.parseId(parentContentUri)

            // OpenTasks should provide the correct relation
            taskList.provider.client.query(taskList.tasksPropertiesUri(), null,
                    "${TaskContract.Properties.TASK_ID}=?", arrayOf(childId.toString()),
                    null, null)!!.use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToNext()

                val row = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor, row)

                assertEquals(
                    TaskContract.Property.Relation.CONTENT_ITEM_TYPE,
                    row.getAsString(TaskContract.Properties.MIMETYPE)
                )
                assertEquals(
                    parentId,
                    row.getAsLong(TaskContract.Property.Relation.RELATED_ID)
                )
                assertEquals(
                    parent.uid,
                    row.getAsString(TaskContract.Property.Relation.RELATED_UID)
                )
                assertEquals(
                    TaskContract.Property.Relation.RELTYPE_PARENT,
                    row.getAsInteger(TaskContract.Property.Relation.RELATED_TYPE)
                )
            }

            // touch the relations to update parent_id values
            taskList.touchRelations()

            // now parent_id should bet set
            taskList.provider.client.query(childContentUri, arrayOf(TaskContract.Tasks.PARENT_ID),
                    null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(parentId, cursor.getLong(0))
            }
        } finally {
            taskList.delete()
        }
    }

}