/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks ({@code VTODO}).
 */
open class TasksSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = TasksSyncAdapter(this, workDispatcher)


	class TasksSyncAdapter(
            context: Context,
            workDispatcher: CoroutineDispatcher
    ): SyncAdapter(context, workDispatcher) {

        override fun sync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                val providerName = TaskProvider.ProviderName.fromAuthority(authority)
                val taskProvider = TaskProvider.fromProviderClient(context, providerName, provider)

                // make sure account can be seen by task provider
                if (Build.VERSION.SDK_INT >= 26)
                    AccountManager.get(context).setAccountVisibility(account, providerName.packageName, AccountManager.VISIBILITY_VISIBLE)

                val accountSettings = AccountSettings(context, account)
                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                    return

                updateLocalTaskLists(taskProvider, account, accountSettings)

                val priorityTaskLists = priorityCollections(extras)
                val taskLists = AndroidTaskList
                        .find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)
                        .sortedByDescending { priorityTaskLists.contains(it.id) }
                for (taskList in taskLists) {
                    Logger.log.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")
                    TasksSyncManager(this, account, accountSettings, extras, authority, syncResult, taskList).use {
                        it.performSync()
                    }
                }
            } catch (e: TaskProvider.ProviderTooOldException) {
                val nm = NotificationManagerCompat.from(context)
                val message = context.getString(R.string.sync_error_tasks_required_version, e.provider.minVersionName)

                val pm = context.packageManager
                val tasksAppInfo = pm.getPackageInfo(e.provider.packageName, 0)
                val tasksAppLabel = tasksAppInfo.applicationInfo.loadLabel(pm)

                val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
                        .setSmallIcon(R.drawable.ic_sync_problem_notify)
                        .setContentTitle(context.getString(R.string.sync_error_tasks_too_old, tasksAppLabel))
                        .setContentText(message)
                        .setSubText("$tasksAppLabel ${e.installedVersionName}")
                        .setCategory(NotificationCompat.CATEGORY_ERROR)

                try {
                    val icon = pm.getApplicationIcon(e.provider.packageName)
                    if (icon is BitmapDrawable)
                        notify.setLargeIcon(icon.bitmap)
                } catch(ignored: PackageManager.NameNotFoundException) {}

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${e.provider.packageName}"))
                if (intent.resolveActivity(pm) != null)
                    notify.setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

                nm.notify(NotificationUtils.NOTIFY_OPENTASKS, notify.build())
                syncResult.databaseError = true
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync task lists", e)
                syncResult.databaseError = true
            }

            Logger.log.info("Task sync complete")
        }

        private fun updateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
            val db = AppDatabase.getInstance(context)
            val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)

            val remoteTaskLists = mutableMapOf<HttpUrl, Collection>()
            if (service != null)
                for (collection in db.collectionDao().getSyncTaskLists(service.id)) {
                    remoteTaskLists[collection.url] = collection
                }

            // delete/update local task lists
            val updateColors = settings.getManageCalendarColors()

            for (list in AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null))
                list.syncId?.let {
                    val url = it.toHttpUrl()
                    val info = remoteTaskLists[url]
                    if (info == null) {
                        Logger.log.fine("Deleting obsolete local task list $url")
                        list.delete()
                    } else {
                        // remote CollectionInfo found for this local collection, update data
                        Logger.log.log(Level.FINE, "Updating local task list $url", info)
                        list.update(info, updateColors)
                        // we already have a local task list for this remote collection, don't take into consideration anymore
                        remoteTaskLists -= url
                    }
                }

            // create new local task lists
            for ((_,info) in remoteTaskLists) {
                Logger.log.log(Level.INFO, "Adding local task list", info)
                LocalTaskList.create(account, provider, info)
            }
        }

    }

}
