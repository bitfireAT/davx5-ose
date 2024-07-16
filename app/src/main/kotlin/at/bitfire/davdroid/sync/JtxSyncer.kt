/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.PrincipalRepository
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level

/**
 * Sync logic for jtx board
 */
class JtxSyncer @AssistedInject constructor(
    @ApplicationContext context: Context,
    serviceRepository: DavServiceRepository,
    collectionRepository: DavCollectionRepository,
    private val principalRepository: PrincipalRepository,
    private val jtxSyncManagerFactory: JtxSyncManager.Factory,
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult
): Syncer(context, serviceRepository, collectionRepository, account, extras, authority, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): JtxSyncer
    }

    private val updateColors = accountSettings.getManageCalendarColors()
    private val localJtxCollections = mutableMapOf<HttpUrl, LocalJtxCollection>()

    override fun beforeSync() {
        // check whether jtx Board is new enough
        try {
            TaskProvider.checkVersion(context, TaskProvider.ProviderName.JtxBoard)
        } catch (e: TaskProvider.ProviderTooOldException) {
            TaskUtils.notifyProviderTooOld(context, e)
        }

        // make sure account can be seen by task provider
        if (Build.VERSION.SDK_INT >= 26) {
            /* Warning: If setAccountVisibility is called, Android 12 broadcasts the
               AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION Intent. This cancels running syncs
               and starts them again! So make sure setAccountVisibility is only called when necessary. */
            val am = AccountManager.get(context)
            if (am.getAccountVisibility(account, TaskProvider.ProviderName.JtxBoard.packageName) != AccountManager.VISIBILITY_VISIBLE)
                am.setAccountVisibility(account, TaskProvider.ProviderName.JtxBoard.packageName, AccountManager.VISIBILITY_VISIBLE)
        }

        // Find all task lists and sync-enabled task lists
        JtxCollection.find(account, provider, context, LocalJtxCollection.Factory, null, null)
            .forEach { localJtxCollection ->
                localJtxCollection.url?.let { url ->
                    localJtxCollections[url.toHttpUrl()] = localJtxCollection
                }
            }
    }

    override fun getServiceType(): String =
        Service.TYPE_CALDAV

    override fun getLocalResourceUrls(): List<HttpUrl?> = localJtxCollections.keys.toList()

    override fun deleteLocalResource(url: HttpUrl?) {
        Logger.log.log(Level.INFO, "Deleting obsolete local jtx collection", url)
        localJtxCollections[url]?.delete()
    }

    override fun updateLocalResource(collection: Collection) {
        Logger.log.log(Level.FINE, "Updating local collection ${collection.url}", collection)
        val owner = collection.ownerId?.let { principalRepository.get(it) }
        localJtxCollections[collection.url]?.updateCollection(collection, owner, updateColors)
    }

    override fun createLocalResource(collection: Collection) {
        Logger.log.log(Level.INFO, "Adding local collections", collection)
        val owner = collection.ownerId?.let { principalRepository.get(it) }
        LocalJtxCollection.create(account, provider, collection, owner)
    }

    override fun getLocalSyncableResourceUrls(): List<HttpUrl?> = localJtxCollections.keys.toList()

    override fun syncLocalResource(collection: Collection) {
        val localJtxCollection = localJtxCollections[collection.url]
            ?: return

        Logger.log.info("Synchronizing jtx collection $localJtxCollection")

        val syncManager = jtxSyncManagerFactory.jtxSyncManager(
            account,
            accountSettings,
            extras,
            httpClient.value,
            authority,
            syncResult,
            localJtxCollection,
            collection
        )
        syncManager.performSync()
    }

    override fun afterSync() {}
}