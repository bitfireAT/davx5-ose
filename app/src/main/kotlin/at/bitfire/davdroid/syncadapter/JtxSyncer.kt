/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Build
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.TaskProvider
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level

/**
 * Sync logic for jtx board
 */
class JtxSyncer(context: Context): Syncer(context) {

    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        try {
            // check whether jtx Board is new enough
            TaskProvider.checkVersion(context, TaskProvider.ProviderName.JtxBoard)

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

            // sync list of collections
            updateLocalCollections(account, provider, accountSettings)

            // sync contents of collections
            val collections = JtxCollection.find(account, provider, context, LocalJtxCollection.Factory, null, null)
            for (collection in collections) {
                Logger.log.info("Synchronizing $collection")
                JtxSyncManager(context, account, accountSettings, extras, httpClient.value, authority, syncResult, collection).performSync()
            }

        } catch (e: TaskProvider.ProviderTooOldException) {
            TaskUtils.notifyProviderTooOld(context, e)
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't sync jtx collections", e)
        }
        Logger.log.info("jtx sync complete")
    }

    private fun updateLocalCollections(account: Account, client: ContentProviderClient, settings: AccountSettings) {
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)

        val remoteCollections = mutableMapOf<HttpUrl, Collection>()
        if (service != null)
            for (collection in db.collectionDao().getSyncJtxCollections(service.id))
                remoteCollections[collection.url] = collection

        val updateColors = settings.getManageCalendarColors()

        for (jtxCollection in JtxCollection.find(account, client, context, LocalJtxCollection.Factory, null, null))
            jtxCollection.url?.let { strUrl ->
                val url = strUrl.toHttpUrl()
                val info = remoteCollections[url]
                if (info == null) {
                    Logger.log.fine("Deleting obsolete local collection $url")
                    jtxCollection.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.log(Level.FINE, "Updating local collection $url", info)
                    val owner = info.ownerId?.let { db.principalDao().get(it) }
                    jtxCollection.updateCollection(info, owner, updateColors)
                    // we already have a local task list for this remote collection, don't take into consideration anymore
                    remoteCollections -= url
                }
            }

        // create new local collections
        for ((_,info) in remoteCollections) {
            Logger.log.log(Level.INFO, "Adding local collections", info)
            val owner = info.ownerId?.let { db.principalDao().get(it) }
            LocalJtxCollection.create(account, client, info, owner)
        }
    }
}