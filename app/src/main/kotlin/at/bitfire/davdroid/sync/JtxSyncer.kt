/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level
import javax.inject.Inject

/**
 * Sync logic for jtx board
 */
class JtxSyncer @Inject constructor(
    @ApplicationContext context: Context,
    db: AppDatabase,
    private val jtxSyncManagerFactory: JtxSyncManager.Factory
): Syncer(context, db) {

    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {

        // 0. preparations

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

        val accountSettings = AccountSettings(context, account)

        // 1. find jtxCollection collections to be synced
        val remoteCollections = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)
        if (service != null)
            for (collection in db.collectionDao().getSyncJtxCollections(service.id))
                remoteCollections[collection.url] = collection

        // 2. delete/update local jtxCollection lists
        val updateColors = accountSettings.getManageCalendarColors()
        for (jtxCollection in JtxCollection.find(account, provider, context, LocalJtxCollection.Factory, null, null))
            jtxCollection.url?.let { strUrl ->
                val url = strUrl.toHttpUrl()
                val collection = remoteCollections[url]
                if (collection == null) {
                    Logger.log.fine("Deleting obsolete local collection $url")
                    jtxCollection.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.log(Level.FINE, "Updating local collection $url", collection)
                    val owner = collection.ownerId?.let { db.principalDao().get(it) }
                    jtxCollection.updateCollection(collection, owner, updateColors)
                    // we already have a local task list for this remote collection, don't take into consideration anymore
                    remoteCollections -= url
                }
            }

        // 3. create new local jtxCollections
        for ((_,info) in remoteCollections) {
            Logger.log.log(Level.INFO, "Adding local collections", info)
            val owner = info.ownerId?.let { db.principalDao().get(it) }
            LocalJtxCollection.create(account, provider, info, owner)
        }

        // 4. sync local jtxCollection lists
        val localCollections = JtxCollection.find(account, provider, context, LocalJtxCollection.Factory, null, null)
        for (localCollection in localCollections) {
            Logger.log.info("Synchronizing $localCollection")

            val url = localCollection.url?.toHttpUrl()
            remoteCollections[url]?.let { collection ->
                val syncManager = jtxSyncManagerFactory.jtxSyncManager(
                    account,
                    accountSettings,
                    extras,
                    httpClient.value,
                    authority,
                    syncResult,
                    localCollection,
                    collection
                )
                syncManager.performSync()
            }
        }
    }
}