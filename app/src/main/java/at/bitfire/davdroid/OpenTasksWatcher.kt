/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.TaskProvider.ProviderName.OpenTasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OpenTasksWatcher(
        context: Context
): PackageChangedReceiver(context) {

    companion object {

        @WorkerThread
        fun updateTaskSync(context: Context) {
            val tasksInstalled = LocalTaskList.tasksProviderAvailable(context)
            Logger.log.info("App launched or other package (un)installed; OpenTasks provider now available = $tasksInstalled")

            var enabledAnyAccount = false

            // check all accounts and (de)activate OpenTasks if a CalDAV service is defined
            val db = AppDatabase.getInstance(context)
            val accountManager = AccountManager.get(context)
            for (account in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
                val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)
                val currentSyncable = ContentResolver.getIsSyncable(account, OpenTasks.authority)
                if (tasksInstalled && service != null) {
                    if (currentSyncable <= 0) {
                        Logger.log.info("Enabling OpenTasks sync for $account")
                        ContentResolver.setIsSyncable(account, OpenTasks.authority, 1)
                        try {
                            AccountSettings(context, account).setSyncInterval(OpenTasks.authority, Constants.DEFAULT_SYNC_INTERVAL)
                            enabledAnyAccount = true
                        } catch (e: InvalidAccountException) {
                            // account has been removed just now
                        }
                    }
                } else if (currentSyncable != 0) {
                    Logger.log.info("Disabling OpenTasks sync for $account")
                    ContentResolver.setIsSyncable(account, OpenTasks.authority, 0)
                }
            }

            if (enabledAnyAccount && !PermissionUtils.havePermissions(context, PermissionUtils.TASKS_PERMISSIONS)) {
                Logger.log.warning("Tasks sync is now enabled for at least one account, but OpenTasks permissions are not granted")
                PermissionUtils.notifyPermissions(context, null)
            }
        }

    }


    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.Default).launch {
            updateTaskSync(context)
        }
    }

}
