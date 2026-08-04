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
import at.bitfire.tasks.contract.Tasks
import at.bitfire.tasks.contract.TasksContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * End-to-end tests of RRULE/RDATE/EXDATE expansion and RECURRENCE-ID override folding into
 * [TaskInstances], via [at.bitfire.davdroid.tasks.provider.recurrence.InstanceMaintainer] as
 * triggered by real writes through [at.bitfire.davdroid.tasks.provider.DavTasksProvider]
 * (design doc §3.5 / Phase 3).
 */
class DavTasksProviderInstancesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var client: ContentProviderClient

    private var taskListId: Long = 0

    @Before
    fun setUp() {
        client = context.contentResolver.acquireContentProviderClient(DAV_TASKS_TEST_AUTHORITY)!!

        val listUri = client.insert(
            TaskLists.contentUri(DAV_TASKS_TEST_AUTHORITY).asSyncAdapter(),
            contentValuesOf(
                TaskLists.ACCOUNT_NAME to "Instances Test Account",
                TaskLists.ACCOUNT_TYPE to "at.bitfire.synctools.test.davtasks.account",
                TaskLists._SYNC_ID to "1",
                TaskLists.LIST_NAME to "Instances Test List",
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
        .appendQueryParameter(TasksContract.PARAM_ACCOUNT_NAME, "Instances Test Account")
        .appendQueryParameter(TasksContract.PARAM_ACCOUNT_TYPE, "at.bitfire.synctools.test.davtasks.account")
        .build()

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun insertTask(values: ContentValues, syncAdapter: Boolean = true): Long {
        val uri = Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY).let { if (syncAdapter) it.asSyncAdapter() else it }
        return ContentUris.parseId(client.insert(uri, values)!!)
    }

    private fun updateTask(id: Long, values: ContentValues, syncAdapter: Boolean = true) {
        val uri = ContentUris.withAppendedId(Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY), id)
            .let { if (syncAdapter) it.asSyncAdapter() else it }
        client.update(uri, values, null, null)
    }

    private fun deleteTask(id: Long, syncAdapter: Boolean) {
        val uri = ContentUris.withAppendedId(Tasks.contentUri(DAV_TASKS_TEST_AUTHORITY), id)
            .let { if (syncAdapter) it.asSyncAdapter() else it }
        client.delete(uri, null, null)
    }

    private fun instancesOf(taskId: Long): List<ContentValues> {
        val results = mutableListOf<ContentValues>()
        client.query(TaskInstances.contentUri(DAV_TASKS_TEST_AUTHORITY), null, "${TaskInstances.TASK_ID}=?", arrayOf(taskId.toString()), null)!!.use { cursor ->
            while (cursor.moveToNext()) {
                val cv = ContentValues()
                android.database.DatabaseUtils.cursorRowToContentValues(cursor, cv)
                results += cv
            }
        }
        return results
    }

    /** All instances belonging to [rootId]'s family (itself + any RECURRENCE-ID exceptions). */
    private fun familyInstances(rootId: Long, exceptionIds: List<Long> = emptyList()): List<ContentValues> =
        (listOf(rootId) + exceptionIds).flatMap { instancesOf(it) }


    @Test
    fun nonRecurringTask_getsExactlyOneInstance() {
        val id = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-single", Tasks.SUMMARY to "Single",
            Tasks.DTSTART to millis("2026-02-01T09:00:00Z"), Tasks.DTSTART_TZ to "UTC",
            Tasks.DUE to millis("2026-02-01T10:00:00Z")
        ))

        val instances = instancesOf(id)

        assertEquals(1, instances.size)
        assertEquals(millis("2026-02-01T09:00:00Z"), instances[0].getAsLong(TaskInstances.INSTANCE_START))
        assertEquals(millis("2026-02-01T10:00:00Z"), instances[0].getAsLong(TaskInstances.INSTANCE_DUE))
    }

    @Test
    fun recurringTask_expandsToMultipleInstances() {
        val id = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-recurring", Tasks.SUMMARY to "Daily",
            Tasks.DTSTART to millis("2026-02-01T09:00:00Z"), Tasks.DTSTART_TZ to "UTC",
            Tasks.RRULE to "FREQ=DAILY;COUNT=4"
        ))

        val instances = instancesOf(id)

        assertEquals(4, instances.size)
        val starts = instances.mapNotNull { it.getAsLong(TaskInstances.INSTANCE_START) }.sorted()
        assertEquals(millis("2026-02-01T09:00:00Z"), starts.first())
        assertEquals(millis("2026-02-04T09:00:00Z"), starts.last())
    }

    @Test
    fun updatingRrule_regeneratesInstances() {
        val id = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-update-rrule", Tasks.SUMMARY to "Daily",
            Tasks.DTSTART to millis("2026-02-01T09:00:00Z"), Tasks.DTSTART_TZ to "UTC",
            Tasks.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        assertEquals(3, instancesOf(id).size)

        updateTask(id, contentValuesOf(Tasks.RRULE to "FREQ=DAILY;COUNT=6"))

        assertEquals(6, instancesOf(id).size)
    }

    @Test
    fun recurrenceIdException_overridesOneOccurrence() {
        val mainId = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-with-exception", Tasks.SUMMARY to "Daily",
            Tasks.DTSTART to millis("2026-02-01T09:00:00Z"), Tasks.DTSTART_TZ to "UTC",
            Tasks.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val overriddenAnchor = millis("2026-02-02T09:00:00Z")

        val exceptionId = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-with-exception", Tasks.SUMMARY to "Moved",
            Tasks.ORIGINAL_INSTANCE_ID to mainId, Tasks.ORIGINAL_INSTANCE_TIME to overriddenAnchor,
            Tasks.DTSTART to millis("2026-02-02T14:00:00Z"), Tasks.DTSTART_TZ to "UTC"
        ))

        val instances = familyInstances(mainId, listOf(exceptionId))
        assertEquals(3, instances.size)

        val overridden = instances.single { it.getAsLong(TaskInstances.TASK_ID) == exceptionId }
        assertEquals(millis("2026-02-02T14:00:00Z"), overridden.getAsLong(TaskInstances.INSTANCE_START))

        // the other two occurrences are still owned by the main task
        assertEquals(2, instances.count { it.getAsLong(TaskInstances.TASK_ID) == mainId })
    }

    @Test
    fun deletingException_revertsOccurrenceToMainTask() {
        val mainId = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-revert", Tasks.SUMMARY to "Daily",
            Tasks.DTSTART to millis("2026-02-01T09:00:00Z"), Tasks.DTSTART_TZ to "UTC",
            Tasks.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val overriddenAnchor = millis("2026-02-02T09:00:00Z")
        val exceptionId = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-revert", Tasks.SUMMARY to "Moved",
            Tasks.ORIGINAL_INSTANCE_ID to mainId, Tasks.ORIGINAL_INSTANCE_TIME to overriddenAnchor,
            Tasks.DTSTART to millis("2026-02-02T14:00:00Z"), Tasks.DTSTART_TZ to "UTC"
        ))
        assertEquals(1, instancesOf(exceptionId).size)

        deleteTask(exceptionId, syncAdapter = true) // physical delete, as after a successful upload

        val instances = instancesOf(mainId)
        assertEquals(3, instances.size)
        assertTrue(instances.any { it.getAsLong(TaskInstances.INSTANCE_START) == overriddenAnchor })
        assertEquals(0, instancesOf(exceptionId).size)
    }

    @Test
    fun tombstoningMainTask_clearsItsInstances() {
        val id = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-tombstone", Tasks.SUMMARY to "Soon deleted",
            Tasks.DTSTART to millis("2026-02-01T09:00:00Z"), Tasks.DTSTART_TZ to "UTC"
        ))
        assertEquals(1, instancesOf(id).size)

        deleteTask(id, syncAdapter = false) // tombstone, row still exists pending upload

        assertEquals(0, instancesOf(id).size)
    }

    @Test
    fun exdate_removesOneOccurrence() {
        val id = insertTask(contentValuesOf(
            Tasks.LIST_ID to taskListId, Tasks._UID to "uid-exdate", Tasks.SUMMARY to "Daily minus one",
            Tasks.DTSTART to millis("2026-02-01T09:00:00Z"), Tasks.DTSTART_TZ to "UTC",
            Tasks.RRULE to "FREQ=DAILY;COUNT=3", Tasks.EXDATE to "20260202T090000Z"
        ))

        val instances = instancesOf(id)

        assertEquals(2, instances.size)
        assertTrue(instances.none { it.getAsLong(TaskInstances.INSTANCE_START) == millis("2026-02-02T09:00:00Z") })
    }

}
