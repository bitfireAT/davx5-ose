/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.database.DatabaseUtils
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.ServiceDB.Collections
import at.bitfire.davdroid.model.ServiceDB.Services
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.TaskProvider
import okhttp3.HttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks ({@code VTODO}).
 */
class TasksSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = TasksSyncAdapter(this)


	class TasksSyncAdapter(
            context: Context
    ): SyncAdapter(context) {

        override fun sync(settings: ISettings, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                val taskProvider = TaskProvider.fromProviderClient(context, provider)
                val accountSettings = AccountSettings(context, settings, account)
                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                    return

                updateLocalTaskLists(taskProvider, account, accountSettings)

                for (taskList in AndroidTaskList.find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)) {
                    Logger.log.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")
                    TasksSyncManager(context, settings, account, accountSettings, extras, authority, syncResult, taskList).use {
                        it.performSync()
                    }
                }
            } catch (e: TaskProvider.ProviderTooOldException) {
                val nm = NotificationManagerCompat.from(context)
                val message = context.getString(R.string.sync_error_opentasks_required_version, e.provider.minVersionName, e.installedVersionName)
                val notify = NotificationUtils.newBuilder(context)
                        .setSmallIcon(R.drawable.ic_sync_error_notification)
                        .setContentTitle(context.getString(R.string.sync_error_opentasks_too_old))
                        .setContentText(message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                        .setCategory(NotificationCompat.CATEGORY_ERROR)

                try {
                    val icon = context.packageManager.getApplicationIcon(e.provider.packageName)
                    if (icon is BitmapDrawable)
                        notify.setLargeIcon(icon.bitmap)
                } catch(ignored: PackageManager.NameNotFoundException) {}

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${e.provider.packageName}"))
                if (intent.resolveActivity(context.packageManager) != null)
                    notify  .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                            .setAutoCancel(true)

                nm.notify(NotificationUtils.NOTIFY_OPENTASKS, notify.build())
                syncResult.databaseError = true
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync task lists", e)
                syncResult.databaseError = true
            }

            Logger.log.info("Task sync complete")
        }

        private fun updateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
            ServiceDB.OpenHelper(context).use { dbHelper ->
                val db = dbHelper.readableDatabase

                fun getService() =
                        db.query(Services._TABLE, arrayOf(Services.ID),
                                "${Services.ACCOUNT_NAME}=? AND ${Services.SERVICE}=?",
                                arrayOf(account.name, Services.SERVICE_CALDAV), null, null, null)?.use { c ->
                            if (c.moveToNext())
                                c.getLong(0)
                            else
                                null
                        }

                fun remoteTaskLists(service: Long?): MutableMap<HttpUrl, CollectionInfo> {
                    val collections = mutableMapOf<HttpUrl, CollectionInfo>()
                    service?.let {
                        db.query(Collections._TABLE, null,
                                "${Collections.SERVICE_ID}=? AND ${Collections.SUPPORTS_VTODO}!=0 AND ${Collections.SYNC}",
                                arrayOf(service.toString()), null, null, null)?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val values = ContentValues(cursor.columnCount)
                                DatabaseUtils.cursorRowToContentValues(cursor, values)
                                val info = CollectionInfo(values)
                                collections[info.url] = info
                            }
                        }
                    }
                    return collections
                }

                // enumerate remote and local task lists
                val service = getService()
                val remote = remoteTaskLists(service)

                // delete/update local task lists
                val updateColors = settings.getManageCalendarColors()

                for (list in AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null))
                    list.syncId?.let {
                        val url = HttpUrl.parse(it)!!
                        val info = remote[url]
                        if (info == null) {
                            Logger.log.fine("Deleting obsolete local task list $url")
                            list.delete()
                        } else {
                            // remote CollectionInfo found for this local collection, update data
                            Logger.log.log(Level.FINE, "Updating local task list $url", info)
                            list.update(info, updateColors)
                            // we already have a local task list for this remote collection, don't take into consideration anymore
                            remote -= url
                        }
                    }

                // create new local task lists
                for ((_,info) in remote) {
                    Logger.log.log(Level.INFO, "Adding local task list", info)
                    LocalTaskList.create(account, provider, info)
                }
            }
        }

    }

}
