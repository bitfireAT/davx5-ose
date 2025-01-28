/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.PrincipalRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.TaskProvider
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Logger
import javax.inject.Inject

class LocalJtxCollectionStore @Inject constructor(
    @ApplicationContext val context: Context,
    val accountSettingsFactory: AccountSettings.Factory,
    db: AppDatabase,
    val principalRepository: PrincipalRepository
): LocalDataStore<LocalJtxCollection> {

    private val serviceDao = db.serviceDao()

    override val authority: String
        get() = JtxContract.AUTHORITY

    override fun acquireContentProvider() =
        context.contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)

    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalJtxCollection? {
        val service = serviceDao.get(fromCollection.serviceId) ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        // If the collection doesn't have a color, use a default color.
        val collectionWithColor =
            if (fromCollection.color != null)
                fromCollection
            else
                fromCollection.copy(color = Constants.DAVDROID_GREEN_RGBA)

        val values = valuesFromCollection(
            info = collectionWithColor,
            account = account,
            withColor = true
        )

        val uri = JtxCollection.create(account, provider, values)
        return LocalJtxCollection(account, provider, ContentUris.parseId(uri))
    }

    private fun valuesFromCollection(info: Collection, account: Account, withColor: Boolean): ContentValues {
        val owner = info.ownerId?.let { principalRepository.get(it) }

        return ContentValues().apply {
            put(JtxContract.JtxCollection.URL, info.url.toString())
            put(
                JtxContract.JtxCollection.DISPLAYNAME,
                info.displayName ?: info.url.lastSegment
            )
            put(JtxContract.JtxCollection.DESCRIPTION, info.description)
            if (owner != null)
                put(JtxContract.JtxCollection.OWNER, owner.url.toString())
            else
                Logger.getGlobal().warning("No collection owner given. Will create jtx collection without owner")
            put(JtxContract.JtxCollection.OWNER_DISPLAYNAME, owner?.displayName)
            if (withColor && info.color != null)
                put(JtxContract.JtxCollection.COLOR, info.color)
            put(JtxContract.JtxCollection.SUPPORTSVEVENT, info.supportsVEVENT)
            put(JtxContract.JtxCollection.SUPPORTSVJOURNAL, info.supportsVJOURNAL)
            put(JtxContract.JtxCollection.SUPPORTSVTODO, info.supportsVTODO)
            put(JtxContract.JtxCollection.ACCOUNT_NAME, account.name)
            put(JtxContract.JtxCollection.ACCOUNT_TYPE, account.type)
            put(JtxContract.JtxCollection.READONLY, info.forceReadOnly || !info.privWriteContent)
        }
    }

    override fun getAll(account: Account, provider: ContentProviderClient): List<LocalJtxCollection> =
        JtxCollection.find(account, provider, context, LocalJtxCollection.Factory, null, null)

    override fun update(provider: ContentProviderClient, localCollection: LocalJtxCollection, fromCollection: Collection) {
        val accountSettings = accountSettingsFactory.create(localCollection.account)
        val values = valuesFromCollection(fromCollection, account = localCollection.account, withColor = accountSettings.getManageCalendarColors())
        localCollection.update(values)
    }

    override fun updateAccount(oldAccount: Account, newAccount: Account) {
        TaskProvider.acquire(context, TaskProvider.ProviderName.JtxBoard)?.use { provider ->
            val values = contentValuesOf(JtxContract.JtxCollection.ACCOUNT_NAME to newAccount.name)
            val uri = JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(oldAccount)
            provider.client.update(uri, values, "${JtxContract.JtxCollection.ACCOUNT_NAME}=?", arrayOf(oldAccount.name))
        }
    }

    override fun delete(localCollection: LocalJtxCollection) {
        localCollection.delete()
    }

}