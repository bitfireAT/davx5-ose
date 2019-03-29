/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.accounts.Account
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.TaskProvider.ProviderName.OpenTasks
import kotlin.concurrent.thread

class PackageChangedReceiver: BroadcastReceiver() {

    companion object {

        @WorkerThread
        fun updateTaskSync(context: Context) {
            val tasksInstalled = LocalTaskList.tasksProviderAvailable(context)
            Logger.log.info("Tasks provider available = $tasksInstalled")

            // check all accounts and (de)activate OpenTasks if a CalDAV service is defined
            val db = AppDatabase.getInstance(context)
            db.serviceDao().getByType(Service.TYPE_CALDAV).forEach { service ->
                val account = Account(service.accountName, context.getString(R.string.account_type))
                if (tasksInstalled) {
                    if (ContentResolver.getIsSyncable(account, OpenTasks.authority) <= 0) {
                        ContentResolver.setIsSyncable(account, OpenTasks.authority, 1)
                        ContentResolver.addPeriodicSync(account, OpenTasks.authority, Bundle(), Constants.DEFAULT_SYNC_INTERVAL)
                    }
                } else
                    ContentResolver.setIsSyncable(account, OpenTasks.authority, 0)

            }
        }

    }


    override fun onReceive(context: Context, intent: Intent) {
        thread {
            updateTaskSync(context)
        }
    }

}
