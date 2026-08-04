/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.Context
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavRecurringTaskList
import at.bitfire.synctools.storage.davtasks.DavTaskAndExceptions
import at.bitfire.synctools.storage.davtasks.DavTasksContract
import at.bitfire.tasks.contract.Tasks
import com.google.common.base.MoreObjects
import org.apache.commons.lang3.StringUtils
import java.util.Optional
import java.util.logging.Logger

/**
 * Represents a task stored in the DAVx⁵-hosted tasks provider. Structurally mirrors [LocalTask]
 * (which does the same for the DMFS backend, see `doc/tasks-provider.md`).
 */
class LocalDavTask(
    val recurringTaskList: DavRecurringTaskList,
    val taskAndExceptions: DavTaskAndExceptions
): LocalResource {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val taskList = recurringTaskList.taskList

    private val mainValues = taskAndExceptions.main.entityValues

    override val id: Long
        get() = mainValues.getAsLong(Tasks._ID)

    override val fileName: String?
        get() = mainValues.getAsString(Tasks._SYNC_ID)

    override val eTag: String?
        get() = mainValues.getAsString(DavTasksContract.COLUMN_ETAG)

    /**
     * Note: Schedule-Tag is not supported for tasks
     */
    override val scheduleTag: String? = null

    override val flags: Int
        get() = mainValues.getAsInteger(DavTasksContract.COLUMN_FLAGS) ?: 0


    // sync methods

    fun update(data: DavTaskAndExceptions) {
        recurringTaskList.updateTaskAndExceptions(id, data)
    }


    // LocalResource implementation

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            logger.fine("Schedule-Tag for tasks not supported, won't save")

        val values = contentValuesOf(
            DavTasksContract.COLUMN_ETAG to eTag,
            Tasks._DIRTY to false
        )
        if (fileName.isPresent)
            values.put(Tasks._SYNC_ID, fileName.get())
        taskList.updateTaskRow(id, values)
    }

    override fun updateFlags(flags: Int) {
        taskList.updateTaskRow(id, contentValuesOf(DavTasksContract.COLUMN_FLAGS to flags))
    }

    override fun updateSequence(sequence: Int) {
        taskList.updateTaskRow(id, contentValuesOf(Tasks.SEQUENCE to sequence))
    }

    override fun updateUid(uid: String) {
        taskList.updateTaskRow(id, contentValuesOf(Tasks._UID to uid))
    }

    override fun deleteLocal() {
        recurringTaskList.deleteTaskAndExceptions(id)
    }

    override fun resetDeleted() {
        throw NotImplementedError()
    }

    override fun getDebugSummary() =
        MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("fileName", fileName)
            .add("eTag", eTag)
            .add("flags", flags)
            .add(
                "task", try {
                    // only include truncated main task row (won't contain attachments, unknown properties etc.)
                    StringUtils.abbreviate(mainValues.toString(), 1000)
                } catch (e: Exception) {
                    e
                }
            )
            .add(
                "exceptions [max 10]", try {
                taskAndExceptions.exceptions.take(10).joinToString { exception ->
                    exception.entityValues.toString().take(1000)
                }
            } catch (e: Exception) {
                e
            })
            .toString()

    /** No `VIEW` intent-filter is declared for the tasks provider yet, so there's no view URI (D2/§5). */
    override fun getViewUri(context: Context): Uri? = null

}
