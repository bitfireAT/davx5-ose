/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.content.ContentProviderOperation
import android.content.ContentValues
import at.bitfire.ical4android.*
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.*

class LocalTask: AndroidTask, LocalResource {

    companion object {
        const val COLUMN_ETAG = Tasks.SYNC_VERSION
        const val COLUMN_FLAGS = Tasks.SYNC1
        const val COLUMN_SEQUENCE = Tasks.SYNC3
    }

    override var fileName: String? = null
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

    override fun populateTask(values: ContentValues) {
        super.populateTask(values)

        val task = requireNotNull(task)
        task.sequence = values.getAsInteger(COLUMN_SEQUENCE)
    }

    override fun buildTask(builder: ContentProviderOperation.Builder, update: Boolean) {
        super.buildTask(builder, update)
        val task = requireNotNull(task)

        builder .withValue(Tasks._SYNC_ID, fileName)
                .withValue(COLUMN_SEQUENCE, task.sequence)
                .withValue(COLUMN_ETAG, eTag)
    }


    /* custom queries */

    override fun assignNameAndUID() {
        try {
            val uid = UUID.randomUUID().toString()
            val newFileName = uid + ".ics"

            val values = ContentValues(2)
            values.put(Tasks._SYNC_ID, newFileName)
            values.put(Tasks._UID, uid)
            taskList.provider.client.update(taskSyncURI(), values, null, null)

            fileName = newFileName

            task!!.uid = uid
        } catch (e: Exception) {
            throw CalendarStorageException("Couldn't update UID", e)
        }
    }

    override fun clearDirty(eTag: String?) {
        try {
            val values = ContentValues(2)
            values.put(Tasks._DIRTY, 0)
            values.put(COLUMN_ETAG, eTag)

            values.put(COLUMN_SEQUENCE, task!!.sequence)
            taskList.provider.client.update(taskSyncURI(), values, null, null)

            this.eTag = eTag
        } catch (e: Exception) {
            throw CalendarStorageException("Couldn't update _DIRTY/ETag/SEQUENCE", e)
        }
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
