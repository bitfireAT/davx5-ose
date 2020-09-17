/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TasksWatcher(
        context: Context
): PackageChangedReceiver(context) {

    companion object {

        @WorkerThread
        fun updateTaskSync(context: Context) {
            val tasksProvider = TaskUtils.currentProvider(context)
            Logger.log.info("App launched or other package (un)installed; current tasks provider = $tasksProvider")

            var permissionsRequired = false     // whether additional permissions are required
            val currentProvider by lazy {       // only this provider shall be enabled (null to disable all providers)
                TaskUtils.currentProvider(context)
            }

            // check all accounts and (de)activate task provider(s) if a CalDAV service is defined
            val db = AppDatabase.getInstance(context)
            val accountManager = AccountManager.get(context)
            for (account in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
                val hasCalDAV = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
                for (providerName in TaskProvider.ProviderName.values()) {
                    val isSyncable = ContentResolver.getIsSyncable(account, providerName.authority)     // may be -1 (unknown state)
                    val shallBeSyncable = hasCalDAV && providerName == currentProvider
                    if ((shallBeSyncable && isSyncable != 1) || (!shallBeSyncable && isSyncable != 0)) {
                        // enable/disable sync
                        setSyncable(context, account, providerName.authority, shallBeSyncable)

                        // if sync has just been enabled: check whether additional permissions are required
                        if (shallBeSyncable && !PermissionUtils.havePermissions(context, providerName.permissions))
                            permissionsRequired = true
                    }
                }
            }

            if (permissionsRequired) {
                Logger.log.warning("Tasks synchronization is now enabled for at least one account, but permissions are not granted")
                PermissionUtils.notifyPermissions(context, null)
            }
        }

        private fun setSyncable(context: Context, account: Account, authority: String, syncable: Boolean) {
            if (syncable) {
                Logger.log.info("Enabling $authority sync for $account")
                ContentResolver.setIsSyncable(account, authority, 1)
                try {
                    AccountSettings(context, account).setSyncInterval(authority, Constants.DEFAULT_SYNC_INTERVAL)
                } catch (e: InvalidAccountException) {
                    // account has already been removed
                }
            } else {
                Logger.log.info("Disabling ${authority} sync for $account")
                ContentResolver.setIsSyncable(account, authority, 0)
            }
        }

    }


    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.Default).launch {
            updateTaskSync(context)
        }
    }

}
