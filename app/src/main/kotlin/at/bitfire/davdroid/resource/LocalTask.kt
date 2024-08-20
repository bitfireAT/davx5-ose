/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentValues
import at.bitfire.ical4android.BatchOperation
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.DmfsTaskFactory
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.UUID

class LocalTask: DmfsTask, LocalResource<Task> {

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

    override fun prepareForUpload(): String {
        val uid: String = task!!.uid ?: run {
            // generate new UID
            val newUid = UUID.randomUUID().toString()

            // update in tasks provider
            val values = ContentValues(1)
            values.put(Tasks._UID, newUid)
            taskList.provider.update(taskSyncURI(), values, null, null)

            // update this task
            task!!.uid = newUid

            newUid
        }

        return "$uid.ics"
    }

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            logger.fine("Schedule-Tag for tasks not supported yet, won't save")

        val values = ContentValues(4)
        if (fileName != null)
            values.put(Tasks._SYNC_ID, fileName)
        values.put(COLUMN_ETAG, eTag)
        values.put(Tasks.SYNC_VERSION, task!!.sequence)
        values.put(Tasks._DIRTY, 0)
        taskList.provider.update(taskSyncURI(), values, null, null)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag
    }

    override fun updateFlags(flags: Int) {
        if (id != null) {
            val values = ContentValues(1)
            values.put(COLUMN_FLAGS, flags)
            taskList.provider.update(taskSyncURI(), values, null, null)
        }

        this.flags = flags
    }

    override fun resetDeleted() {
        throw NotImplementedError()
    }


    object Factory: DmfsTaskFactory<LocalTask> {
        override fun fromProvider(taskList: DmfsTaskList<*>, values: ContentValues) =
                LocalTask(taskList, values)
    }
}
