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
import at.bitfire.synctools.storage.BatchOperation
import com.google.common.base.MoreObjects
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.Optional

class LocalTask: DmfsTask, LocalResource {

    companion object {
        const val COLUMN_ETAG = Tasks.SYNC1
        const val COLUMN_FLAGS = Tasks.SYNC2
    }

    override var fileName: String? = null

    override var scheduleTag: String? = null
    override var eTag: String? = null

    override var flags = 0
        private set


    constructor(taskList: DmfsTaskList<*>, task: Task, fileName: String?, eTag: String?, flags: Int)
            : super(taskList, task) {
        this.fileName = fileName
        this.eTag = eTag
        this.flags = flags
    }

    private constructor(taskList: DmfsTaskList<*>, values: ContentValues): super(taskList) {
        id = values.getAsLong(Tasks._ID)
        fileName = values.getAsString(Tasks._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }


    /* process LocalTask-specific fields */

    override fun buildTask(builder: BatchOperation.CpoBuilder, update: Boolean) {
        super.buildTask(builder, update)

        builder .withValue(Tasks._SYNC_ID, fileName)
                .withValue(COLUMN_ETAG, eTag)
                .withValue(COLUMN_FLAGS, flags)
    }


    /* custom queries */

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            logger.fine("Schedule-Tag for tasks not supported yet, won't save")

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
        this.fileName = fileName
        this.eTag = eTag
        this.scheduleTag = scheduleTag
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
            .add("scheduleTag", scheduleTag)
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

    override fun getViewUri(context: Context): Uri? {
        val idNotNull = id ?: return null
        val supportedProviders = listOf(
            TaskProvider.ProviderName.JtxBoard,
            TaskProvider.ProviderName.OpenTasks,
            // Tasks.org can't handle view content URIs via (no intent-filter)
        )
        if (taskList.providerName !in supportedProviders)
            return null
        val contentUri = Tasks.getContentUri(taskList.providerName.authority)
        return ContentUris.withAppendedId(contentUri, idNotNull)
    }


    object Factory: DmfsTaskFactory<LocalTask> {
        override fun fromProvider(taskList: DmfsTaskList<*>, values: ContentValues) =
                LocalTask(taskList, values)
    }
}