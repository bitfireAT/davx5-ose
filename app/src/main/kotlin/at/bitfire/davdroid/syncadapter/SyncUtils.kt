/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Utility methods related to synchronization management (authorities, workers etc.)
 */
object SyncUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncUtilsEntryPoint {
        fun appDatabase(): AppDatabase
        fun settingsManager(): SettingsManager
    }

    /**
     * Starts an Intent and redirects the user to the package in the market to update the app
     *
     * @param e   the TaskProvider.ProviderTooOldException to be shown
     */
    fun notifyProviderTooOld(context: Context, e: TaskProvider.ProviderTooOldException) {
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
        } catch (ignored: PackageManager.NameNotFoundException) {
            // couldn't get provider app icon
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${e.provider.packageName}"))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        if (intent.resolveActivity(pm) != null)
            notify.setContentIntent(PendingIntent.getActivity(context, 0, intent, flags))

        nm.notifyIfPossible(NotificationUtils.NOTIFY_TASKS_PROVIDER_TOO_OLD, notify.build())
    }

    /**
     * Returns a list of all available sync authorities:
     *
     *   1. calendar authority
     *   2. contacts authority (only if [withContacts] is *true* - mostly we don't want it included)
     *   3. address books authority
     *   4. tasks authority/ies (if available, when tasks managing app(s) installed)
     *
     * Checking the availability of authorities may be relatively expensive, so the
     * result should be cached for the current operation.
     *
     * @param withContacts whether to add contacts authority
     * @return list of available sync authorities for main accounts
     */
    fun syncAuthorities(context: Context, withContacts: Boolean = false): List<String> {
        val result = mutableListOf(
            CalendarContract.AUTHORITY,
            context.getString(R.string.address_books_authority)
        )
        if (withContacts)
            result.add(ContactsContract.AUTHORITY)
        TaskUtils.currentProvider(context)?.let { taskProvider ->
            result += taskProvider.authority
        }
        return result
    }

    // task sync utils

    /**
     * Sets up sync for the current TaskProvider (and disables sync for unavailable task providers).
     *
     * In case of missing permissions, a notification is shown.
     */
    @WorkerThread
    fun updateTaskSync(context: Context) {
        val currentProvider = TaskUtils.currentProvider(context)
        Logger.log.info("App launched or other package (un)installed; current tasks provider = $currentProvider")

        var permissionsRequired = false     // whether additional permissions are required

        // check all accounts and (de)activate task provider(s) if a CalDAV service is defined
        val db = EntryPointAccessors.fromApplication(context, SyncUtilsEntryPoint::class.java).appDatabase()
        val accountManager = AccountManager.get(context)
        for (account in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
            val hasCalDAV = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
            for (providerName in TaskProvider.ProviderName.entries) {
                val isSyncable = ContentResolver.getIsSyncable(account, providerName.authority)     // may be -1 (unknown state)
                val shallBeSyncable = hasCalDAV && providerName == currentProvider
                if ((shallBeSyncable && isSyncable != 1) || (!shallBeSyncable && isSyncable != 0)) {
                    // enable/disable sync
                    setSyncableFromSettings(context, account, providerName.authority, shallBeSyncable)

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

    private fun setSyncableFromSettings(context: Context, account: Account, authority: String, syncable: Boolean) {
        val settingsManager by lazy { EntryPointAccessors.fromApplication(context, SyncUtilsEntryPoint::class.java).settingsManager() }
        if (syncable) {
            Logger.log.info("Enabling $authority sync for $account")
            ContentResolver.setIsSyncable(account, authority, 1)
            try {
                val settings = AccountSettings(context, account)
                val interval = settings.getTasksSyncInterval() ?: settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL)
                settings.setSyncInterval(authority, interval)
            } catch (e: InvalidAccountException) {
                // account has already been removed
            }
        } else {
            Logger.log.info("Disabling $authority sync for $account")
            ContentResolver.setIsSyncable(account, authority, 0)
        }
    }

}