/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.synctools.storage.davtasks.DavTaskListProvider
import at.bitfire.tasks.contract.TaskLists
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.WillNotClose
import javax.inject.Inject

/**
 * [LocalDataStore] for the DAVx⁵-hosted tasks provider. Structurally mirrors [LocalTaskListStore]
 * (DMFS backend) — unlike that one, there's only one implementation of this provider (it's part
 * of this app), so no assisted-injected `providerName`/variant is needed.
 */
class LocalDavTaskListStore @Inject constructor(
    val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext val context: Context,
    val db: AppDatabase,
    val logger: Logger
): LocalDataStore<LocalDavTaskList> {

    private val serviceDao = db.serviceDao()

    override val authority: String
        get() = context.getString(R.string.tasks_provider_authority)

    override fun acquireContentProvider(throwOnMissingPermissions: Boolean) = try {
        context.contentResolver.acquireContentProviderClient(authority)
    } catch (e: SecurityException) {
        if (throwOnMissingPermissions)
            throw e
        else
            /* return */ null
    }

    override suspend fun create(client: ContentProviderClient, fromCollection: Collection): LocalDavTaskList {
        val service = serviceDao.get(fromCollection.serviceId)
            ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        logger.info("Adding local task list: $fromCollection")
        val davTaskList = create(account, client, fromCollection)
        return LocalDavTaskList(davTaskList)
    }

    private fun create(account: Account, client: ContentProviderClient, fromCollection: Collection): DavTaskList {
        val collectionWithColor = if (fromCollection.color != null)
            fromCollection
        else
            fromCollection.copy(color = Constants.DAVDROID_GREEN_RGBA)

        val values = valuesFromCollectionInfo(info = collectionWithColor, withColor = true).apply {
            put(TaskLists.LIST_OWNER, account.name)
            put(TaskLists.SYNC_ENABLED, true)
            put(TaskLists.VISIBLE, true)
            put(TaskLists.SUPPORTED_COMPONENTS, TaskLists.Component.VTODO)
        }
        return DavTaskListProvider(account, client, authority).createAndGetTaskList(values)
    }

    private fun valuesFromCollectionInfo(info: Collection, withColor: Boolean): ContentValues {
        val values = ContentValues(3)
        values.put(TaskLists._SYNC_ID, info.id.toString())
        values.put(TaskLists.LIST_NAME,
            if (info.displayName.isNullOrBlank()) info.url.lastSegment else info.displayName)

        if (withColor && info.color != null)
            // RFC 7986 §5.9 COLOR is a CSS3 colour name, not an int — but Collection.color is an
            // Android colour int (from CalDAV apple-calendar-color); store its hex form as a
            // best-effort passthrough until the collection layer itself understands CSS3 names.
            values.put(TaskLists.LIST_COLOR, String.format("#%06X", 0xFFFFFF and info.color))

        values.put(
            TaskLists.ACCESS_LEVEL,
            if (info.privWriteContent && !info.forceReadOnly)
                TaskLists.AccessLevel.READ_WRITE
            else
                TaskLists.AccessLevel.READ_ONLY
        )

        return values
    }

    override fun getAll(account: Account, client: ContentProviderClient) =
        DavTaskListProvider(account, client, authority).findTaskLists()
            .map { LocalDavTaskList(it) }

    override fun getByDbCollectionId(account: Account, client: ContentProviderClient, dbCollectionId: Long): LocalDavTaskList? =
        DavTaskListProvider(account, client, authority)
            .findFirstTaskList("${TaskLists._SYNC_ID}=?", arrayOf(dbCollectionId.toString()))
            ?.let { LocalDavTaskList(it) }

    override fun update(client: ContentProviderClient, localCollection: LocalDavTaskList, fromCollection: Collection) {
        logger.log(Level.FINE, "Updating local task list {0}: {1}", arrayOf(fromCollection.url, fromCollection))
        val accountSettings = accountSettingsFactory.create(localCollection.davTaskList.account)
        localCollection.davTaskList.update(valuesFromCollectionInfo(fromCollection, withColor = accountSettings.getManageCalendarColors()))
    }

    override fun updateAccount(oldAccount: Account, newAccount: Account, @WillNotClose client: ContentProviderClient?) {
        if (client == null)
            return
        val values = contentValuesOf(TaskLists.ACCOUNT_NAME to newAccount.name)
        val uri = TaskLists.contentUri(authority)
        client.update(uri, values, "${TaskLists.ACCOUNT_NAME}=?", arrayOf(oldAccount.name))
    }

    override fun delete(localCollection: LocalDavTaskList) {
        localCollection.davTaskList.delete()
    }

}
