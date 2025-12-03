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
import at.bitfire.ical4android.TaskProvider
import com.google.common.base.MoreObjects
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.Optional
import java.util.logging.Logger

/**
 * Represents a Dmfs Task (OpenTasks and Tasks.org) entry
 */
class LocalTask(
    val dmfsTask: DmfsTask
): LocalResource {

    val logger: Logger = Logger.getLogger(javaClass.name)


    // LocalResource implementation

    override val id: Long?
        get() = dmfsTask.id

    override var fileName: String?
        get() = dmfsTask.syncId
        set(value) { dmfsTask.syncId = value }

    override var eTag: String?
        get() = dmfsTask.eTag
        set(value) { dmfsTask.eTag = value }

    /**
     * Note: Schedule-Tag for tasks is not supported
     */
    override var scheduleTag: String? = null

    override val flags: Int
        get() = dmfsTask.flags

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            logger.fine("Schedule-Tag for tasks not supported, won't save")

        val values = ContentValues(4)
        if (fileName.isPresent)
            values.put(Tasks._SYNC_ID, fileName.get())
        values.put(DmfsTask.COLUMN_ETAG, eTag)
        values.put(Tasks.SYNC_VERSION, dmfsTask.task!!.sequence)
        values.put(Tasks._DIRTY, 0)
        dmfsTask.update(values)

        if (fileName.isPresent)
            this.fileName = fileName.get()
        this.eTag = eTag
    }

    override fun updateFlags(flags: Int) {
        if (id != null) {
            val values = contentValuesOf(DmfsTask.COLUMN_FLAGS to flags)
            dmfsTask.update(values)
        }
        dmfsTask.flags = flags
    }

    override fun updateSequence(sequence: Int) = throw NotImplementedError()

    override fun updateUid(uid: String) {
        val values = contentValuesOf(Tasks._UID to uid)
        dmfsTask.update(values)
    }

    override fun deleteLocal() {
        dmfsTask.delete()
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
        when (dmfsTask.taskList.providerName) {
            TaskProvider.ProviderName.OpenTasks -> {
                val contentUri = Tasks.getContentUri(dmfsTask.taskList.providerName.authority)
                ContentUris.withAppendedId(contentUri, id)
            }
            // Tasks.org can't handle view content URIs (missing intent-filter)
            // Jtx Board tasks are [LocalJtxICalObject]s
            else -> null
        }
    }

}