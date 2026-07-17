/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.DmfsTasksContract
import io.mockk.every
import io.mockk.mockk
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LocalTaskListTest {
    private lateinit var providerTasks: MutableList<Entity>
    private lateinit var taskList: LocalTaskList
    private var queryCount = 0

    @Before
    fun setUp() {
        providerTasks = mutableListOf()
        queryCount = 0

        val dmfsTaskList = mockk<DmfsTaskList>()
        every { dmfsTaskList.findTasks(any(), any()) } returns emptyList()
        every { dmfsTaskList.iterateTasks(any(), any(), any()) } answers {
            queryCount++
            val syncId = secondArg<Array<String>>().single()
            val body = thirdArg<(Entity) -> Unit>()
            providerTasks
                .filter { it.entityValues.getAsString(Tasks._SYNC_ID) == syncId }
                .map { Entity(ContentValues(it.entityValues)) }
                .forEach(body)
        }
        every { dmfsTaskList.updateTaskRow(any(), any()) } answers {
            val id = firstArg<Long>()
            val values = secondArg<ContentValues>()
            providerTasks
                .single { it.entityValues.getAsLong(Tasks._ID) == id }
                .entityValues
                .putAll(values)
        }

        taskList = LocalTaskList(dmfsTaskList)
    }

    @Test
    fun testFindByName_ReassignsDirtyDuplicateAndReturnsSyncedTask() {
        val syncId = "duplicate.ics"
        providerTasks += task(id = 1, syncId = syncId, eTag = "etag-1", dirty = 0)
        providerTasks += task(id = 2, syncId = syncId, eTag = null, dirty = 1)

        val result = taskList.findByName(syncId)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals(syncId, result.fileName)
        assertEquals("etag-1", result.eTag)
        val reassignedSyncId =
            providerTasks
                .single { it.entityValues.getAsLong(Tasks._ID) == 2L }
                .entityValues
                .getAsString(Tasks._SYNC_ID)
        assertNotEquals(syncId, reassignedSyncId)
        assertTrue(reassignedSyncId?.endsWith(".ics") == true)
        assertEquals(2, queryCount)
    }

    @Test
    fun testFindByName_AllDuplicatesReassigned_ReturnsNull() {
        val syncId = "duplicate.ics"
        providerTasks += task(id = 1, syncId = syncId, eTag = null, dirty = 1)
        providerTasks += task(id = 2, syncId = syncId, eTag = null, dirty = 1)

        assertNull(taskList.findByName(syncId))
        assertTrue(providerTasks.none { it.entityValues.getAsString(Tasks._SYNC_ID) == syncId })
        assertEquals(2, queryCount)
    }

    private fun task(
        id: Long,
        syncId: String,
        eTag: String?,
        dirty: Int,
    ) = Entity(
        contentValuesOf(
            Tasks._ID to id,
            Tasks._SYNC_ID to syncId,
            DmfsTasksContract.COLUMN_ETAG to eTag,
            Tasks._DIRTY to dirty,
        ),
    )
}
