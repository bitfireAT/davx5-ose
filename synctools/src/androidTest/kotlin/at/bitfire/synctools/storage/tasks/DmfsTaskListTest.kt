/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.database.DatabaseUtils
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Property
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmfsTaskListTest(providerName: TaskProvider.ProviderName) :
    DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)

    private fun createTaskList(): DmfsTaskList {
        val info = ContentValues()
        info.put(TaskLists.LIST_NAME, "Test Task List")
        info.put(TaskLists.LIST_COLOR, 0xffff0000)
        info.put(TaskLists.OWNER, "test@example.com")
        info.put(TaskLists.SYNC_ENABLED, 1)
        info.put(TaskLists.VISIBLE, 1)

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
            val filteredCount = taskList.countTasks("${Tasks._UID}=?", arrayOf("filter-uid-1"))
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
            taskList.provider.client.query(
                taskList.tasksPropertiesUri(), null,
                "${TaskContract.Properties.TASK_ID}=?", arrayOf(childId.toString()),
                null, null
            )!!.use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToNext()

                val row = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor, row)

                assertEquals(
                    Property.Relation.CONTENT_ITEM_TYPE,
                    row.getAsString(TaskContract.Properties.MIMETYPE)
                )
                assertEquals(
                    parentId,
                    row.getAsLong(Property.Relation.RELATED_ID)
                )
                assertEquals(
                    parent.uid,
                    row.getAsString(Property.Relation.RELATED_UID)
                )
                assertEquals(
                    Property.Relation.RELTYPE_PARENT,
                    row.getAsInteger(Property.Relation.RELATED_TYPE)
                )
            }

            // touch the relations to update parent_id values
            taskList.touchRelations()

            // now parent_id should bet set
            taskList.provider.client.query(
                childContentUri, arrayOf(Tasks.PARENT_ID),
                null, null, null
            )!!.use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(parentId, cursor.getLong(0))
            }
        } finally {
            taskList.delete()
        }
    }


    // test tasks CRUD

    @Test
    fun testAddTask() {
        val taskList = createTaskList()
        try {
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Test Task",
                    Tasks.DESCRIPTION to "Test Description"
                )
            )
            val id = taskList.addTask(entity)

            // verify that task has been inserted
            val result = taskList.getTask(id)!!
            assertEquals("Test Task", result.entityValues.getAsString(Tasks.TITLE))
            assertEquals("Test Description", result.entityValues.getAsString(Tasks.DESCRIPTION))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testAddTask_toBatch() {
        val taskList = createTaskList()
        try {
            val batch = TasksBatchOperation(taskList.client)
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Batch Task"
                )
            )
            val backRefIdx = taskList.addTask(entity, batch)
            batch.commit()

            // Get the result URI and parse the ID
            val resultUri = batch.getResult(backRefIdx)?.uri
            val id = ContentUris.parseId(resultUri!!)

            // Verify task was inserted
            val result = taskList.getTask(id)
            assertEquals("Batch Task", result?.entityValues?.getAsString(Tasks.TITLE))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testFindTaskRow() {
        val taskList = createTaskList()
        try {
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Find Test Task"
                )
            )
            taskList.addTask(entity)

            val result = taskList.findTaskRow(
                arrayOf(Tasks.TITLE),
                "${Tasks.TITLE}=?",
                arrayOf("Find Test Task")
            )
            assertEquals("Find Test Task", result?.getAsString(Tasks.TITLE))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testFindTasks() {
        val taskList = createTaskList()
        try {
            taskList.addTask(
                Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Task 1"
                )
                )
            )
            taskList.addTask(
                Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Task 2"
                )
                )
            )

            val tasks = taskList.findTasks()
            assertEquals(2, tasks.size)

            val titles = tasks.map { it.entityValues.getAsString(Tasks.TITLE) }.toSet()
            assertEquals(setOf("Task 1", "Task 2"), titles)
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testGetTask() {
        val taskList = createTaskList()
        try {
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Get Test Task"
                )
            ).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(),
                    contentValuesOf(
                        TaskContract.Properties.MIMETYPE to Property.Comment.CONTENT_ITEM_TYPE,
                        Property.Comment.COMMENT to "Some Comment"
                    )
                )
            }

            val id = taskList.addTask(entity)

            val result = taskList.getTask(id)
            assertNotNull(result)
            assertEquals("Get Test Task", result!!.entityValues?.getAsString(Tasks.TITLE))

            val subvalue = result.subValues.first()
            assertEquals(TaskContract.Properties.getContentUri(taskList.providerName.authority), subvalue.uri)
            assertEquals("Some Comment", subvalue.values?.getAsString(Property.Comment.COMMENT))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testIterateTaskRows() {
        val taskList = createTaskList()
        try {
            taskList.addTask(
                Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Iterate Task 1"
                )
                )
            )
            taskList.addTask(
                Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Iterate Task 2"
                )
                )
            )

            val result = mutableListOf<ContentValues>()
            taskList.iterateTaskRows(null, null, null) { row ->
                result += row
            }

            assertEquals(2, result.size)
            val titles = result.map { it.getAsString(Tasks.TITLE) }.toSet()
            assertEquals(setOf("Iterate Task 1", "Iterate Task 2"), titles)
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testUpdateTaskRow() {
        val taskList = createTaskList()
        try {
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Original Title"
                )
            )
            val id = taskList.addTask(entity)

            taskList.updateTaskRow(id, contentValuesOf(Tasks.TITLE to "Updated Title"))

            val result = taskList.getTask(id)
            assertNotNull(result)
            assertEquals("Updated Title", result?.entityValues?.getAsString(Tasks.TITLE))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testUpdateTask() {
        val taskList = createTaskList()
        try {
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Update Test Task"
                )
            ).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(),
                    contentValuesOf(
                        TaskContract.Properties.MIMETYPE to Property.Category.CONTENT_ITEM_TYPE,
                        Property.Category.CATEGORY_NAME to "Work"
                    )
                )
            }
            val id = taskList.addTask(entity)

            // Update with new entity
            val updatedEntity = Entity(
                contentValuesOf(
                    Tasks.TITLE to "Updated Task Title"
                )
            ).apply {
                // Use base properties URI for sub-values, MIMETYPE goes in ContentValues
                addSubValue(
                    taskList.tasksPropertiesUri(),
                    contentValuesOf(
                        TaskContract.Properties.MIMETYPE to Property.Category.CONTENT_ITEM_TYPE,
                        Property.Category.CATEGORY_NAME to "Work"
                    )
                )
            }

            taskList.updateTask(id, updatedEntity)

            val result = taskList.getTask(id)
            assertNotNull(result)
            assertEquals("Updated Task Title", result?.entityValues?.getAsString(Tasks.TITLE))

            // Check property was updated
            val commentProperty = result?.subValues?.find {
                it.values.getAsString(TaskContract.Properties.MIMETYPE) == Property.Category.CONTENT_ITEM_TYPE
            }
            assertNotNull(commentProperty)
            assertEquals(
                "Work",
                commentProperty?.values?.getAsString(Property.Category.CATEGORY_NAME)
            )
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testDeleteTask() {
        val taskList = createTaskList()
        try {
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Delete Test Task"
                )
            )

            val id = taskList.addTask(entity)
            assertNotNull(taskList.getTask(id))

            taskList.deleteTask(id)

            assertNull(taskList.getTask(id))
        } finally {
            taskList.delete()
        }
    }

}
