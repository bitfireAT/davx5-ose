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
import at.bitfire.synctools.storage.TaskProvider
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Property
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        val dmfsTaskListProvider = DmfsTaskListProvider(testAccount, provider, providerName)
        return dmfsTaskListProvider.createAndGetTaskList(info)
    }

    @Test
    fun testTouchRelations() {
        val taskList = createTaskList()
        try {
            // insert child before parent
            val childId = taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._UID to "child",
                        Tasks.TITLE to "Child task",
                        Tasks.PARENT_ID to null
                    )
                ).apply {
                    addSubValue(
                        taskList.tasksPropertiesUri(),
                        contentValuesOf(
                            TaskContract.Properties.MIMETYPE to Property.Relation.CONTENT_ITEM_TYPE,
                            Property.Relation.RELATED_UID to "parent",
                            Property.Relation.RELATED_TYPE to Property.Relation.RELTYPE_PARENT
                        )
                    )
                }
            )
            val parentId = taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._UID to "parent",
                        Tasks.TITLE to "Parent task"
                    )
                )
            )

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
                    "parent",
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
            val child = taskList.getTaskRow(childId, arrayOf(Tasks.PARENT_ID))
            assertNotNull(child)
            assertEquals(parentId, child?.getAsLong(Tasks.PARENT_ID))
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
            taskList.addTask(Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._SYNC_ID to "sync-id-1",
                Tasks._UID to "filter-uid-1",
                Tasks.TITLE to "Filter Test 1"
            )))
            taskList.addTask(Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._SYNC_ID to "sync-id-2",
                Tasks._UID to "filter-uid-2",
                Tasks.TITLE to "Filter Test 2"
            )))

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
            taskList.addTask(Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._SYNC_ID to "sync-id-1",
                Tasks._UID to "task-1",
                Tasks.TITLE to "Test Task 1"
            )))
            taskList.addTask(Entity(contentValuesOf(
                Tasks.LIST_ID to taskList.id,
                Tasks._SYNC_ID to "sync-id-2",
                Tasks._UID to "task-2",
                Tasks.TITLE to "Test Task 2"
            )))

            val count = taskList.countTasks(null, null)
            assertEquals(2, count)
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
    fun testFindTask() {
        val taskList = createTaskList()
        try {
            // Add a task
            taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._SYNC_ID to "find-test-sync-id",
                        Tasks.TITLE to "Find Test Task"
                    )
                )
            )

            // Find by sync ID
            val result = taskList.findTask("${Tasks._SYNC_ID}=?", arrayOf("find-test-sync-id"))
            assertNotNull(result)
            assertEquals("Find Test Task", result?.entityValues?.getAsString(Tasks.TITLE))

            // Find non-existent
            val notFound = taskList.findTask("${Tasks._SYNC_ID}=?", arrayOf("non-existent"))
            assertNull(notFound)
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
    fun testGetTaskRow() {
        val taskList = createTaskList()
        try {
            val id = taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks.TITLE to "GetRow Test Task",
                        Tasks.DESCRIPTION to "Description"
                    )
                )
            )

            // Get specific row with projection
            val result = taskList.getTaskRow(id, arrayOf(Tasks.TITLE))
            assertNotNull(result)
            assertEquals("GetRow Test Task", result?.getAsString(Tasks.TITLE))

            // Get non-existent
            taskList.deleteTask(999)
            val notFound = taskList.getTaskRow(999)
            assertNull(notFound)
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
    fun testIterateTasks() {
        val taskList = createTaskList()
        try {
            // Add tasks directly via Entity
            taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._UID to "iterate-task-1",
                        Tasks.TITLE to "Iterate Task 1",
                        Tasks.DESCRIPTION to "Description 1"
                    )
                ).apply {
                    addSubValue(
                        taskList.tasksPropertiesUri(),
                        contentValuesOf(
                            TaskContract.Properties.MIMETYPE to Property.Comment.CONTENT_ITEM_TYPE,
                            Property.Comment.COMMENT to "Task 1 Comment"
                        )
                    )
                }
            )
            taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._UID to "iterate-task-2",
                        Tasks.TITLE to "Iterate Task 2",
                        Tasks.DESCRIPTION to "Description 2"
                    )
                )
            )

            val result = mutableListOf<Entity>()
            taskList.iterateTasks(null, null) { entity ->
                result += entity
            }

            assertEquals(2, result.size)
            val uids = result.mapNotNull { it.entityValues.getAsString(Tasks._UID) }.toSet()
            assertEquals(setOf("iterate-task-1", "iterate-task-2"), uids)

            // Verify that entities contain properties (description)
            val task1Entity = result.find { it.entityValues.getAsString(Tasks._UID) == "iterate-task-1" }
            assertNotNull(task1Entity)
            assertEquals("Description 1", task1Entity?.entityValues?.getAsString(Tasks.DESCRIPTION))
            assertEquals("Task 1 Comment", task1Entity?.subValues?.first()?.values?.getAsString(Property.Comment.COMMENT))
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
    fun testUpdateTaskRow_WithBatch() {
        val taskList = createTaskList()
        try {
            val id = taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks.TITLE to "Batch Update Row Test"
                    )
                )
            )

            val batch = TasksBatchOperation(taskList.client)
            taskList.updateTaskRow(id, contentValuesOf(Tasks.TITLE to "Updated Via Batch"), batch)
            batch.commit()

            val result = taskList.getTask(id)
            assertNotNull(result)
            assertEquals("Updated Via Batch", result?.entityValues?.getAsString(Tasks.TITLE))
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
    fun testUpdateTask_WithBatch() {
        val taskList = createTaskList()
        try {
            val entity = Entity(
                contentValuesOf(
                    Tasks.LIST_ID to taskList.id,
                    Tasks.TITLE to "Batch Update Test"
                )
            ).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(),
                    contentValuesOf(
                        TaskContract.Properties.MIMETYPE to Property.Comment.CONTENT_ITEM_TYPE,
                        Property.Comment.COMMENT to "Original Comment"
                    )
                )
            }
            val id = taskList.addTask(entity)

            // Update via batch
            val updatedEntity = Entity(
                contentValuesOf(
                    Tasks.TITLE to "Updated Title"
                )
            ).apply {
                addSubValue(
                    taskList.tasksPropertiesUri(),
                    contentValuesOf(
                        TaskContract.Properties.MIMETYPE to Property.Comment.CONTENT_ITEM_TYPE,
                        Property.Comment.COMMENT to "Updated Comment"
                    )
                )
            }

            val batch = TasksBatchOperation(taskList.client)
            taskList.updateTask(id, updatedEntity, batch)
            batch.commit()

            val result = taskList.getTask(id)
            assertNotNull(result)
            assertEquals("Updated Title", result?.entityValues?.getAsString(Tasks.TITLE))

            val commentProperty = result?.subValues?.find {
                it.values.getAsString(TaskContract.Properties.MIMETYPE) == Property.Comment.CONTENT_ITEM_TYPE
            }
            assertNotNull(commentProperty)
            assertEquals("Updated Comment", commentProperty?.values?.getAsString(Property.Comment.COMMENT))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testUpdateTasks() {
        val taskList = createTaskList()
        try {
            // Add tasks
            taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._SYNC_ID to "update-test-1",
                        Tasks.TITLE to "Original Title 1",
                        Tasks.COMPLETED to 0
                    )
                )
            )
            taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._SYNC_ID to "update-test-2",
                        Tasks.TITLE to "Original Title 2",
                        Tasks.COMPLETED to 0
                    )
                )
            )

            // Bulk update to mark as completed
            val updatedCount = taskList.updateTasks(
                contentValuesOf(Tasks.COMPLETED to 1, Tasks.TITLE to "Updated Title"),
                "${Tasks._SYNC_ID} LIKE ?",
                arrayOf("update-test-%")
            )
            assertEquals(2, updatedCount)

            // Verify updates
            val tasks = taskList.findTasks()
            assertEquals(2, tasks.size)
            for (task in tasks) {
                assertEquals("Updated Title", task.entityValues.getAsString(Tasks.TITLE))
                assertEquals(1L, task.entityValues.getAsLong(Tasks.COMPLETED))
            }
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

    @Test
    fun testDeleteTask_WithBatch() {
        val taskList = createTaskList()
        try {
            val id = taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks.TITLE to "Batch Delete Test"
                    )
                )
            )

            // Verify exists
            assertNotNull(taskList.getTask(id))

            // Delete via batch
            val batch = TasksBatchOperation(taskList.client)
            taskList.deleteTask(id, batch)
            batch.commit()

            // Verify deleted
            assertNull(taskList.getTask(id))
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testDeleteTasks() {
        val taskList = createTaskList()
        try {
            // Add multiple tasks
            val id1 = taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._SYNC_ID to "delete-test-1",
                        Tasks.TITLE to "Delete Test 1"
                    )
                )
            )
            val id2 = taskList.addTask(
                Entity(
                    contentValuesOf(
                        Tasks.LIST_ID to taskList.id,
                        Tasks._SYNC_ID to "delete-test-2",
                        Tasks.TITLE to "Delete Test 2"
                    )
                )
            )

            // Verify both exist
            assertNotNull(taskList.getTask(id1))
            assertNotNull(taskList.getTask(id2))

            // Delete all matching sync IDs
            val deletedCount = taskList.deleteTasks(
                "${Tasks._SYNC_ID} LIKE ?",
                arrayOf("delete-test-%")
            )
            assertEquals(2, deletedCount)

            // Verify both are gone
            assertNull(taskList.getTask(id1))
            assertNull(taskList.getTask(id2))
        } finally {
            taskList.delete()
        }
    }

}
