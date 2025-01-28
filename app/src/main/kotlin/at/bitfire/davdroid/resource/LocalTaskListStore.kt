/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.dmfs.tasks.contract.TaskContract.TaskListColumns
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger

class LocalTaskListStore @AssistedInject constructor(
    @Assisted private val providerName: TaskProvider.ProviderName,
    val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext val context: Context,
    val db: AppDatabase,
    val logger: Logger
): LocalDataStore<LocalTaskList> {

    @AssistedFactory
    interface Factory {
        fun create(providerName: TaskProvider.ProviderName): LocalTaskListStore
    }

    private val serviceDao = db.serviceDao()

    override val authority: String
        get() = providerName.authority

    override fun acquireContentProvider() =
        context.contentResolver.acquireContentProviderClient(authority)

    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalTaskList? {
        val service = serviceDao.get(fromCollection.serviceId) ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        logger.log(Level.INFO, "Adding local task list", fromCollection)
        val uri = create(account, provider, providerName, fromCollection)
        return DmfsTaskList.findByID(account, provider, providerName, LocalTaskList.Factory, ContentUris.parseId(uri))
    }

    private fun create(account: Account, provider: ContentProviderClient, providerName: TaskProvider.ProviderName, fromCollection: Collection): Uri {
        // If the collection doesn't have a color, use a default color.
        val collectionWithColor = if (fromCollection.color != null)
            fromCollection
        else
            fromCollection.copy(color = Constants.DAVDROID_GREEN_RGBA)

        val values = valuesFromCollectionInfo(
            info = collectionWithColor,
            withColor = true
        ).apply {
            put(TaskLists.OWNER, account.name)
            put(TaskLists.SYNC_ENABLED, 1)
            put(TaskLists.VISIBLE, 1)
        }
        return DmfsTaskList.Companion.create(account, provider, providerName, values)
    }

    private fun valuesFromCollectionInfo(info: Collection, withColor: Boolean): ContentValues {
        val values = ContentValues(3)
        values.put(TaskLists._SYNC_ID, info.url.toString())
        values.put(TaskLists.LIST_NAME,
            if (info.displayName.isNullOrBlank()) info.url.lastSegment else info.displayName)

        if (withColor && info.color != null)
            values.put(TaskLists.LIST_COLOR, info.color)

        if (info.privWriteContent && !info.forceReadOnly)
            values.put(TaskListColumns.ACCESS_LEVEL, TaskListColumns.ACCESS_LEVEL_OWNER)
        else
            values.put(TaskListColumns.ACCESS_LEVEL, TaskListColumns.ACCESS_LEVEL_READ)

        return values
    }

    override fun getAll(account: Account, provider: ContentProviderClient) =
        DmfsTaskList.find(account, LocalTaskList.Factory, provider, providerName, null, null)

    override fun update(provider: ContentProviderClient, localCollection: LocalTaskList, fromCollection: Collection) {
        logger.log(Level.FINE, "Updating local task list ${fromCollection.url}", fromCollection)
        val accountSettings = accountSettingsFactory.create(localCollection.account)
        localCollection.update(valuesFromCollectionInfo(fromCollection, withColor = accountSettings.getManageCalendarColors()))
    }

    override fun updateAccount(oldAccount: Account, newAccount: Account) {
        TaskProvider.acquire(context, providerName)?.use { provider ->
            val values = contentValuesOf(Tasks.ACCOUNT_NAME to newAccount.name)
            val uri = Tasks.getContentUri(providerName.authority)
            provider.client.update(uri, values, "${Tasks.ACCOUNT_NAME}=?", arrayOf(oldAccount.name))
        }
    }

    override fun delete(localCollection: LocalTaskList) {
        localCollection.delete()
    }

}