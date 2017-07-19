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
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.*
import org.dmfs.provider.tasks.TaskContract.Tasks
import java.io.FileNotFoundException
import java.text.ParseException
import java.util.*

class LocalTask: AndroidTask, LocalResource {

    companion object {
        val COLUMN_ETAG = Tasks.SYNC1
        val COLUMN_UID = Tasks.SYNC2
        val COLUMN_SEQUENCE = Tasks.SYNC3
    }

    override var fileName: String? = null
    override var eTag: String? = null

    constructor(taskList: AndroidTaskList<*>, task: Task, fileName: String?, eTag: String?): super(taskList, task) {
        this.fileName = fileName
        this.eTag = eTag
    }

    private constructor(taskList: AndroidTaskList<*>, id: Long, baseInfo: ContentValues?): super(taskList, id) {
        baseInfo?.let {
            fileName = it.getAsString(Events._SYNC_ID)
            eTag = it.getAsString(COLUMN_ETAG)
        }
    }


    /* process LocalTask-specific fields */

    @Throws(ParseException::class)
    override fun populateTask(values: ContentValues) {
        super.populateTask(values)

        fileName = values.getAsString(Events._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)

        val task = requireNotNull(task)
        task.uid = values.getAsString(COLUMN_UID)
        task.sequence = values.getAsInteger(COLUMN_SEQUENCE)
    }

    @Throws(FileNotFoundException::class, CalendarStorageException::class)
    override fun buildTask(builder: ContentProviderOperation.Builder, update: Boolean) {
        super.buildTask(builder, update)
        val task = requireNotNull(task)

        builder .withValue(Tasks._SYNC_ID, fileName)
                .withValue(COLUMN_UID, task.uid)
                .withValue(COLUMN_SEQUENCE, task.sequence)
                .withValue(COLUMN_ETAG, eTag)
    }


    /* custom queries */

    @Throws(CalendarStorageException::class)
    override fun prepareForUpload() {
        try {
            val uid = UUID.randomUUID().toString()
            val newFileName = uid + ".ics"

            val values = ContentValues(2)
            values.put(Tasks._SYNC_ID, newFileName)
            values.put(COLUMN_UID, uid)
            taskList.provider.client.update(taskSyncURI(), values, null, null)

            fileName = newFileName

            task!!.uid = uid
        } catch (e: Exception) {
            throw CalendarStorageException("Couldn't update UID", e)
        }
    }

    @Throws(CalendarStorageException::class)
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


    object Factory: AndroidTaskFactory<LocalTask> {

        override fun newInstance(calendar: AndroidTaskList<*>, id: Long, baseInfo: ContentValues?) =
                LocalTask(calendar, id, baseInfo)

        override fun newInstance(calendar: AndroidTaskList<*>, task: Task) =
                LocalTask(calendar, task, null, null)

    }
}
