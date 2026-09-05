/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.PrincipalRepository
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.dav4jvm.ktor.withTrailingSlash
import at.bitfire.davdroid.util.DavUtils.extractCollectionName
import at.bitfire.synctools.storage.jtx.JtxCollectionProvider
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Logger
import javax.annotation.WillNotClose
import javax.inject.Inject

class LocalJtxCollectionStore @Inject constructor(
    @ApplicationContext val context: Context,
    val accountRepository: AccountRepository,
    val accountSettingsFactory: AccountSettingsFactory,
    val androidAccountManager: AndroidAccountManager,
    db: AppDatabase,
    val principalRepository: PrincipalRepository
): LocalDataStore<LocalJtxCollection> {

    private val serviceDao = db.serviceDao()

    override val authority: String
        get() = JtxContract.AUTHORITY

    override fun acquireContentProvider(throwOnMissingPermissions: Boolean) = try {
        context.contentResolver.acquireContentProviderClient(authority)
    } catch (e: SecurityException) {
        if (throwOnMissingPermissions)
            throw e
        else
            /* return */ null
    }

    override suspend fun create(client: ContentProviderClient, fromCollection: Collection): LocalJtxCollection {
        val service = serviceDao.get(fromCollection.serviceId)
            ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val accountId = accountRepository.getAccountIdFromName(service.accountName)
        val account = androidAccountManager.getAndroidAccount(accountId)

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

        val jtxCollection = JtxCollectionProvider(account, client).createAndGetCollection(values)
        return LocalJtxCollection(jtxCollection)
    }

    private fun valuesFromCollection(info: Collection, account: Account, withColor: Boolean): ContentValues {
        val owner = info.ownerId?.let { principalRepository.getBlocking(it) }

        return ContentValues().apply {
            put(JtxContract.JtxCollection.SYNC_ID, info.id)
            put(JtxContract.JtxCollection.URL, info.url.toString())
            put(
                JtxContract.JtxCollection.DISPLAYNAME,
                info.displayName ?: extractCollectionName(info.url.withTrailingSlash())
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

    override fun getAll(accountId: AccountId, client: ContentProviderClient): List<LocalJtxCollection> {
        val account = androidAccountManager.getAndroidAccount(accountId)
        return JtxCollectionProvider(account, client).findCollections().map { jtxCollection ->
            LocalJtxCollection(jtxCollection)
        }
    }

    override fun getByDbCollectionId(
        accountId: AccountId,
        client: ContentProviderClient,
        dbCollectionId: Long
    ): LocalJtxCollection? {
        val account = androidAccountManager.getAndroidAccount(accountId)
        return JtxCollectionProvider(account, client).findFirstCollection(
            "${JtxContract.JtxCollection.SYNC_ID}=?", arrayOf(dbCollectionId.toString())
        )?.let { jtxCollection -> LocalJtxCollection(jtxCollection) }
    }

    override fun update(
        accountId: AccountId,
        client: ContentProviderClient,
        localCollection: LocalJtxCollection,
        fromCollection: Collection
    ) {
        val values = valuesFromCollection(
            info = fromCollection,
            account = localCollection.jtxCollection.account,
            withColor = useManagedCalendarColors(accountId)
        )
        localCollection.jtxCollection.update(values)
    }

    private fun useManagedCalendarColors(accountId: AccountId): Boolean {
        val accountSettings = accountSettingsFactory.create(accountId)
        return accountSettings.getManageCalendarColors()
    }

    override fun updateAccount(oldAccount: Account, newAccount: Account, @WillNotClose client: ContentProviderClient?) {
        if (client == null)
            return
        val values = contentValuesOf(JtxContract.JtxCollection.ACCOUNT_NAME to newAccount.name)
        val uri = JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(oldAccount)
        client.update(uri, values, "${JtxContract.JtxCollection.ACCOUNT_NAME}=?", arrayOf(oldAccount.name))
    }

    override fun delete(localCollection: LocalJtxCollection) {
        localCollection.jtxCollection.delete()
    }

}