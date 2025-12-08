/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.DmfsTaskFactory
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import com.google.common.base.MoreObjects
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.Optional

/**
 * Represents a Dmfs Task (OpenTasks and Tasks.org) entry
 */
class LocalTask: DmfsTask, LocalResource {

    override var fileName: String? = null

    /**
     * Note: Schedule-Tag for tasks is not supported
     */
    override var scheduleTag: String? = null


    constructor(taskList: DmfsTaskList<*>, task: Task, fileName: String?, eTag: String?, flags: Int)
            : super(taskList, task, fileName, eTag, flags)

    private constructor(taskList: DmfsTaskList<*>, values: ContentValues)
            : super(taskList, values)


    /* custom queries */

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            logger.fine("Schedule-Tag for tasks not supported, won't save")

        val values = ContentValues(4)
        if (fileName.isPresent)
            values.put(Tasks._SYNC_ID, fileName.get())
        values.put(COLUMN_ETAG, eTag)
        values.put(Tasks.SYNC_VERSION, task!!.sequence)
        values.put(Tasks._DIRTY, 0)
        taskList.provider.update(taskSyncURI(), values, null, null)

        if (fileName.isPresent)
            this.fileName = fileName.get()
        this.eTag = eTag
    }

    fun update(data: Task, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        if (scheduleTag != null)
            logger.fine("Schedule-Tag for tasks not supported, won't save")

        this.fileName = fileName
        this.eTag = eTag
        this.flags = flags

        // processes this.{fileName, eTag, scheduleTag, flags} and resets DIRTY flag
        update(data)
    }

    override fun updateFlags(flags: Int) {
        if (id != null) {
            val values = contentValuesOf(COLUMN_FLAGS to flags)
            taskList.provider.update(taskSyncURI(), values, null, null)
        }

        this.flags = flags
    }

    override fun updateSequence(sequence: Int) = throw NotImplementedError()

    override fun updateUid(uid: String) {
        val values = contentValuesOf(Tasks._UID to uid)
        taskList.provider.update(taskSyncURI(), values, null, null)
    }

    override fun deleteLocal() {
        delete()
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
            /*.add("task",
                try {
                    // too dangerous, may contain unknown properties and cause another OOM
                    Ascii.truncate(task.toString(), 1000, "…")
                } catch (e: Exception) {
                    e
                }
            )*/
            .toString()

    override fun getViewUri(context: Context): Uri? = id?.let { id ->
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


    object Factory: DmfsTaskFactory<LocalTask> {
        override fun fromProvider(taskList: DmfsTaskList<*>, values: ContentValues) =
                LocalTask(taskList, values)
    }

}