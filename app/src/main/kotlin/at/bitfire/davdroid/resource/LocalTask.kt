/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.content.ContentValues
import at.bitfire.ical4android.*
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.*

class LocalTask: AndroidTask, LocalResource<Task> {

    companion object {
        const val COLUMN_ETAG = Tasks.SYNC1
        const val COLUMN_FLAGS = Tasks.SYNC2
    }

    override var fileName: String? = null

    override var scheduleTag: String? = null
    override var eTag: String? = null

    override var flags = 0
        private set


    constructor(taskList: AndroidTaskList<*>, task: Task, fileName: String?, eTag: String?, flags: Int)
            : super(taskList, task) {
        this.fileName = fileName
        this.eTag = eTag
        this.flags = flags
    }

    private constructor(taskList: AndroidTaskList<*>, values: ContentValues): super(taskList) {
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
        var uid: String? = null
        taskList.provider.client.query(taskSyncURI(), arrayOf(Tasks._UID), null, null, null)?.use { cursor ->
            if (cursor.moveToNext())
                uid = cursor.getString(0)
        }

        if (uid == null) {
            // generate new UID
            uid = UUID.randomUUID().toString()

            val values = ContentValues(1)
            values.put(Tasks._UID, uid)
            taskList.provider.client.update(taskSyncURI(), values, null, null)

            task!!.uid = uid
        }

        return "$uid.ics"
    }

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            Ical4Android.log.fine("Schedule-Tag for tasks not supported yet, won't save")

        val values = ContentValues(4)
        if (fileName != null)
            values.put(Tasks._SYNC_ID, fileName)
        values.put(COLUMN_ETAG, eTag)
        values.put(Tasks.SYNC_VERSION, task!!.sequence)
        values.put(Tasks._DIRTY, 0)
        taskList.provider.client.update(taskSyncURI(), values, null, null)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag
    }

    override fun updateFlags(flags: Int) {
        if (id != null) {
            val values = ContentValues(1)
            values.put(COLUMN_FLAGS, flags)
            taskList.provider.client.update(taskSyncURI(), values, null, null)
        }

        this.flags = flags
    }


    object Factory: AndroidTaskFactory<LocalTask> {
        override fun fromProvider(taskList: AndroidTaskList<*>, values: ContentValues) =
                LocalTask(taskList, values)
    }
}
