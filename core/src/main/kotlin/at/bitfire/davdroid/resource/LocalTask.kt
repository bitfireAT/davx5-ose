/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.TaskProvider
import at.bitfire.synctools.storage.tasks.DmfsRecurringTaskList
import at.bitfire.synctools.storage.tasks.DmfsTask
import at.bitfire.synctools.storage.tasks.TaskAndExceptions
import com.google.common.base.MoreObjects
import org.apache.commons.lang3.StringUtils
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.Optional
import java.util.logging.Logger

/**
 * Represents a Dmfs Task (OpenTasks and Tasks.org) entry
 */
class LocalTask(
    val recurringTaskList: DmfsRecurringTaskList,
    val taskAndExceptions: TaskAndExceptions
): LocalResource {

    private val logger: Logger = Logger.getLogger(javaClass.name)

    private val taskList = recurringTaskList.taskList

    private val mainValues = taskAndExceptions.main.entityValues

    override val id: Long
        get() = mainValues.getAsLong(Tasks._ID)

    override val fileName: String?
        get() = mainValues.getAsString(Tasks._SYNC_ID)

    override val eTag: String?
        get() = mainValues.getAsString(DmfsTask.COLUMN_ETAG)

    /**
     * Note: Schedule-Tag is not supported for tasks
     */
    override val scheduleTag: String? = null

    override val flags: Int
        get() = mainValues.getAsInteger(DmfsTask.COLUMN_FLAGS) ?: 0


    // sync methods

    fun update(data: TaskAndExceptions) {
        recurringTaskList.updateTaskAndExceptions(id, data)
    }


    // LocalResource implementation

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            logger.fine("Schedule-Tag for tasks not supported, won't save")

        val values = contentValuesOf(
            DmfsTask.COLUMN_ETAG to eTag,
            Tasks._DIRTY to 0
        )
        if (fileName.isPresent)
            values.put(Tasks._SYNC_ID, fileName.get())
        taskList.updateTaskRow(id, values)
    }

    override fun updateFlags(flags: Int) {
        taskList.updateTaskRow(
            id, contentValuesOf(
                DmfsTask.COLUMN_FLAGS to flags
            )
        )
    }

    override fun updateSequence(sequence: Int) {
        taskList.updateTaskRow(id, contentValuesOf(
            Tasks.SYNC_VERSION to sequence
        ))
    }

    override fun updateUid(uid: String) {
        taskList.updateTaskRow(id, contentValuesOf(
            Tasks._UID to uid
        ))
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
                "event", try {
                    // only include truncated main task row (won't contain attachments, unknown properties etc.)
                    StringUtils.abbreviate(mainValues.toString(), 1000)
                } catch (e: Exception) {
                    e
                }
            )
            .add(
                "exceptions [max 10]", try {
                taskAndExceptions.exceptions.take(10).joinToString { exception ->
                    // truncated exception row
                    exception.entityValues.toString().take(1000)
                }
            } catch (e: Exception) {
                e
            })
            .toString()

    override fun getViewUri(context: Context): Uri? =
        when (taskList.providerName) {
            TaskProvider.ProviderName.OpenTasks -> {
                val contentUri = Tasks.getContentUri(taskList.providerName.authority)
                ContentUris.withAppendedId(contentUri, id)
            }
            // Tasks.org can't handle view content URIs (missing intent-filter)
            // Jtx Board tasks are [LocalJtxICalObject]s
            else -> null
        }

}