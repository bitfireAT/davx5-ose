/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.tasks.contract.TaskInstances
import at.bitfire.tasks.contract.TaskLists
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks
import at.bitfire.tasks.contract.TasksContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Tests [at.bitfire.davdroid.tasks.provider.DavTasksProvider] directly via [ContentProviderClient]
 * (not through the [DavTaskList]/[DavTaskListProvider] wrapper), to exercise provider-level
 * semantics a wrapper always uses "correctly" and would never accidentally violate: column
 * allow-listing, dirty propagation, tombstones vs. real deletes, and the sync-adapter escape
 * hatch. See §4 of the design doc.
 */
class DavTasksProviderSecurityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var client: ContentProviderClient

    private var taskListId: Long = 0

    @Before
    fun setUp() {
        client = context.contentResolver.acquireContentProviderClient(DAV_TASKS_TEST_AUTHORITY)!!

        val listUri = client.insert(
            TaskLists.contentUri(DAV_TASKS_TEST_AUTHORITY).asSyncAdapter(),
            contentValuesOf(
                TaskLists.ACCOUNT_NAME to "Security Test Account",
                TaskLists.ACCOUNT_TYPE to "at.bitfire.synctools.test.davtasks.account",
                TaskLists._SYNC_ID to "1",
                TaskLists.LIST_NAME to "Security Test List",
                TaskLists.SUPPORTED_COMPONENTS to TaskLists.Component.VTODO
            )
        )!!
        taskListId = ContentUris.parseId(listUri)
    }

    @After
    fun tearDown() {
        client.delete(TaskLists.contentUri(DAV_TASKS_TEST_AUTHORITY).asSyncAdapter(), "${TaskLists._ID}=?", arrayOf(taskListId.toString()))
        client.close()
    }

    private fun Uri.asSyncAdapter(): Uri = buildUpon()
        .appendQueryParameter(TasksContract.PARAM_CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(TasksContract.PARAM_ACCOUNT_NAME, "Security Test Account")
        .appendQueryParameter(TasksContract.PARAM_ACCOUNT_TYPE, "at.bitfire.synctools.test.davtasks.account")
        .build()

    private fun insertTask(uid: String, syncAdapter: Boolean, extraValues: ContentValues = ContentValues()): Long {
        val values = contentValuesOf(
            Tasks.LIST_ID to taskListId,
            Tasks._UID to uid,
            Tasks.SUMMARY to "Task $uid"
        ).apply { putAll(extraValues) }
        val uri = Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY).let { if (syncAdapter) it.asSyncAdapter() else it }
        return ContentUris.parseId(client.insert(uri, values)!!)
    }

    private fun getTaskRow(id: Long): ContentValues? {
        val uri = ContentUris.withAppendedId(Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY), id)
        client.query(uri, null, null, null, null)!!.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val values = ContentValues()
            android.database.DatabaseUtils.cursorRowToContentValues(cursor, values)
            return values
        }
    }


    // --- column allow-listing (§4: SQL injection defense) -------------------------------------

    @Test
    fun testInsert_unknownColumn_throws() {
        try {
            client.insert(
                Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY),
                contentValuesOf(
                    Tasks.LIST_ID to taskListId,
                    Tasks._UID to "uid-bad-column",
                    "evil_column; DROP TABLE tasks;--" to "value"
                )
            )
            fail("Expected insert with unknown column to be rejected")
        } catch (e: Exception) {
            // IllegalArgumentException wrapped in a RemoteException/TransactionTooLargeException-style
            // wrapper depending on process boundary; either way, it must not silently succeed.
            assertTrue(
                "Expected an error mentioning the unknown column, got: $e",
                (e.message ?: "").contains("evil_column", ignoreCase = true) || e.cause?.message?.contains("evil_column", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun testQuery_unknownProjectionColumn_throws() {
        try {
            client.query(
                Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY),
                arrayOf("sqlite_master.sql"), // not a real Tasks column - attempted schema exfiltration
                null, null, null
            )
            fail("Expected query with unknown projection column to be rejected")
        } catch (_: Exception) {
            // expected: SQLiteQueryBuilder's projection map rejects it
        }
    }


    // --- dirty flag semantics -------------------------------------------------------------------

    @Test
    fun testInsert_nonSyncAdapter_forcesDirty() {
        val id = insertTask("uid-dirty-1", syncAdapter = false, extraValues = contentValuesOf(Tasks._DIRTY to false))
        val row = getTaskRow(id)!!
        assertEquals(1, row.getAsInteger(Tasks._DIRTY))
    }

    @Test
    fun testInsert_syncAdapter_honorsCallerDirty() {
        val id = insertTask("uid-dirty-2", syncAdapter = true, extraValues = contentValuesOf(Tasks._DIRTY to false))
        val row = getTaskRow(id)!!
        assertEquals(0, row.getAsInteger(Tasks._DIRTY))
    }

    @Test
    fun testInsertProperty_nonSyncAdapter_dirtiesParentTask() {
        val id = insertTask("uid-parent-1", syncAdapter = true, extraValues = contentValuesOf(Tasks._DIRTY to false))
        assertEquals(0, getTaskRow(id)!!.getAsInteger(Tasks._DIRTY))

        client.insert(
            TaskProperties.contentUri(DAV_TASKS_TEST_AUTHORITY), // not as sync adapter
            contentValuesOf(
                TaskProperties.TASK_ID to id,
                TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY,
                TaskProperties.DATA1 to "Home"
            )
        )

        assertEquals(1, getTaskRow(id)!!.getAsInteger(Tasks._DIRTY))
    }

    @Test
    fun testInsertProperty_syncAdapter_doesNotDirtyParentTask() {
        val id = insertTask("uid-parent-2", syncAdapter = true, extraValues = contentValuesOf(Tasks._DIRTY to false))

        client.insert(
            TaskProperties.contentUri(DAV_TASKS_TEST_AUTHORITY).asSyncAdapter(),
            contentValuesOf(
                TaskProperties.TASK_ID to id,
                TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY,
                TaskProperties.DATA1 to "Home"
            )
        )

        assertEquals(0, getTaskRow(id)!!.getAsInteger(Tasks._DIRTY))
    }


    // --- tombstones vs. real deletes -------------------------------------------------------------

    @Test
    fun testDelete_nonSyncAdapter_tombstonesInsteadOfPhysicalDelete() {
        val id = insertTask("uid-tombstone", syncAdapter = true, extraValues = contentValuesOf(Tasks._DIRTY to false))

        val uri = ContentUris.withAppendedId(Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY), id)
        val count = client.delete(uri, null, null) // not as sync adapter
        assertEquals(1, count)

        // row must still exist, marked deleted + dirty (so the deletion can be uploaded)
        val row = getTaskRow(id)
        assertNotNull("Non-sync-adapter delete must tombstone, not physically remove, the row", row)
        assertEquals(1, row!!.getAsInteger(Tasks._DELETED))
        assertEquals(1, row.getAsInteger(Tasks._DIRTY))
    }

    @Test
    fun testDelete_syncAdapter_physicallyDeletes() {
        val id = insertTask("uid-real-delete", syncAdapter = true)

        val uri = ContentUris.withAppendedId(Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY), id)
        val count = client.delete(uri.asSyncAdapter(), null, null)
        assertEquals(1, count)

        assertNull(getTaskRow(id))
    }

    @Test
    fun testDeleteTask_cascadesToProperties() {
        val id = insertTask("uid-cascade-delete", syncAdapter = true)
        client.insert(
            TaskProperties.contentUri(DAV_TASKS_TEST_AUTHORITY).asSyncAdapter(),
            contentValuesOf(TaskProperties.TASK_ID to id, TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY, TaskProperties.DATA1 to "X")
        )

        val uri = ContentUris.withAppendedId(Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY), id)
        client.delete(uri.asSyncAdapter(), null, null) // physical delete -> FK cascade

        client.query(
            TaskProperties.contentUri(DAV_TASKS_TEST_AUTHORITY),
            null, "${TaskProperties.TASK_ID}=?", arrayOf(id.toString()), null
        )!!.use { cursor ->
            assertEquals(0, cursor.count)
        }
    }


    // --- Instances is read-only ------------------------------------------------------------------

    @Test
    fun testInstances_insert_throws() {
        try {
            client.insert(
                TaskInstances.contentUri(DAV_TASKS_TEST_AUTHORITY),
                contentValuesOf(TaskInstances.TASK_ID to 1L)
            )
            fail("Expected insert into read-only Instances table to be rejected")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test
    fun testInstances_delete_throws() {
        try {
            client.delete(TaskInstances.contentUri(DAV_TASKS_TEST_AUTHORITY), null, null)
            fail("Expected delete on read-only Instances table to be rejected")
        } catch (_: Exception) {
            // expected
        }
    }

}
