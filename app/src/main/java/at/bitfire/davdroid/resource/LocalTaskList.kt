/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.AndroidTaskListFactory
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.TaskProvider
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.io.FileNotFoundException

class LocalTaskList private constructor(
        account: Account,
        provider: TaskProvider,
        id: Long
): AndroidTaskList<LocalTask>(account, provider, LocalTask.Factory, id), LocalCollection<LocalTask> {

    companion object {

        val defaultColor = 0xFFC3EA6E.toInt()     // "DAVdroid green"

        val COLUMN_CTAG = TaskLists.SYNC_VERSION

        val BASE_INFO_COLUMNS = arrayOf(
            Tasks._ID,
            Tasks._SYNC_ID,
            LocalTask.COLUMN_ETAG
        )

        @JvmStatic
        fun tasksProviderAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return context.packageManager.resolveContentProvider(TaskProvider.ProviderName.OpenTasks.authority, 0) != null
            else {
                val provider = TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)
                provider?.use { return true }
                return false
            }
        }

        @JvmStatic
        @Throws(CalendarStorageException::class)
        fun create(account: Account, provider: TaskProvider, info: CollectionInfo): Uri {
            val values = valuesFromCollectionInfo(info, true)
            values.put(TaskLists.OWNER, account.name)
            values.put(TaskLists.SYNC_ENABLED, 1)
            values.put(TaskLists.VISIBLE, 1)
            return create(account, provider, values)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun onRenameAccount(resolver: ContentResolver, oldName: String, newName: String) {
            var client: ContentProviderClient? = null
            try {
                client = resolver.acquireContentProviderClient(TaskProvider.ProviderName.OpenTasks.authority)
                client?.use {
                    val values = ContentValues(1)
                    values.put(Tasks.ACCOUNT_NAME, newName)
                    it.update(Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority), values, "${Tasks.ACCOUNT_NAME}=?", arrayOf(oldName))
                }
            } finally {
                if (Build.VERSION.SDK_INT >= 24)
                    client?.close()
                else
                    client?.release()
            }
        }

        private fun valuesFromCollectionInfo(info: CollectionInfo, withColor: Boolean): ContentValues {
            val values = ContentValues(3)
            values.put(TaskLists._SYNC_ID, info.url)
            values.put(TaskLists.LIST_NAME, if (info.displayName.isNullOrBlank()) DavUtils.lastSegmentOfUrl(info.url) else info.displayName)

            if (withColor)
                values.put(TaskLists.LIST_COLOR, info.color ?: defaultColor)

            return values
        }

    }


    override fun taskBaseInfoColumns() = BASE_INFO_COLUMNS


    @Throws(CalendarStorageException::class)
    fun update(info: CollectionInfo, updateColor: Boolean) {
        update(valuesFromCollectionInfo(info, updateColor));
    }


    @Throws(CalendarStorageException::class)
    override fun getAll() = queryTasks(null, null)

    @Throws(CalendarStorageException::class)
    override fun getDeleted() = queryTasks("${Tasks._DELETED}!=0", null)

    @Throws(CalendarStorageException::class)
    override fun getWithoutFileName() = queryTasks("${Tasks._SYNC_ID} IS NULL", null)

    @Throws(FileNotFoundException::class, CalendarStorageException::class)
    override fun getDirty(): List<LocalTask> {
        val tasks = queryTasks("${Tasks._DIRTY}!=0", null)
        for (localTask in tasks) {
            val task = requireNotNull(localTask.task)
            val sequence = task.sequence
            if (sequence == null)    // sequence has not been assigned yet (i.e. this task was just locally created)
                task.sequence = 0
            else
                task.sequence = sequence + 1
        }
        return tasks
    }


    @Throws(CalendarStorageException::class)
    override fun getCTag(): String? =
        try {
            provider.client.query(taskListSyncUri(), arrayOf(COLUMN_CTAG), null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.getString(0)
            }
            null
        } catch(e: Exception) {
            throw CalendarStorageException("Couldn't read local (last known) CTag", e)
        }

    @Throws(CalendarStorageException::class)
    override fun setCTag(cTag: String?) {
        try {
            val values = ContentValues(1)
            values.put(COLUMN_CTAG, cTag)
            provider.client.update(taskListSyncUri(), values, null, null)
        } catch (e: Exception) {
            throw CalendarStorageException("Couldn't write local (last known) CTag", e)
        }
    }


    object Factory: AndroidTaskListFactory<LocalTaskList> {

        override fun newInstance(account: Account, provider: TaskProvider, id: Long) =
                LocalTaskList(account, provider, id)

    }

}
