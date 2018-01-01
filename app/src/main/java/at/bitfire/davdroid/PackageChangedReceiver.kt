/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.ServiceDB.Services
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.TaskProvider

class PackageChangedReceiver: BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED)
            updateTaskSync(context)
    }


    companion object {

        @JvmStatic
        fun updateTaskSync(context: Context) {
            val tasksInstalled = LocalTaskList.tasksProviderAvailable(context)
            Logger.log.info("Package (un)installed; OpenTasks provider now available = $tasksInstalled")

            // check all accounts and (de)activate OpenTasks if a CalDAV service is defined
            ServiceDB.OpenHelper(context).use { dbHelper ->
                val db = dbHelper.readableDatabase

                db.query(Services._TABLE, arrayOf(Services.ACCOUNT_NAME),
                        "${Services.SERVICE}=?", arrayOf(Services.SERVICE_CALDAV), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val account = Account(cursor.getString(0), context.getString(R.string.account_type))

                        if (tasksInstalled) {
                            if (ContentResolver.getIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority) <= 0) {
                                ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1)
                                ContentResolver.setSyncAutomatically(account, TaskProvider.ProviderName.OpenTasks.authority, true)
                                ContentResolver.addPeriodicSync(account, TaskProvider.ProviderName.OpenTasks.authority, Bundle(), Constants.DEFAULT_SYNC_INTERVAL)
                            }
                        } else
                            ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 0)

                    }
                }
            }
        }

    }

}
