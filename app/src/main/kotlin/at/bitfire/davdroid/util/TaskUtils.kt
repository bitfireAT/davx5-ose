/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import android.accounts.Account
import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.PeriodicSyncWorker
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

object TaskUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskUtilsEntryPoint {
        fun settingsManager(): SettingsManager
    }

    /**
     * Returns the currently selected tasks provider (if it's still available = installed).
     *
     * @return the currently selected tasks provider, or null if none is available
     */
    fun currentProvider(context: Context): ProviderName? {
        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
        val preferredAuthority = settingsManager.getString(Settings.SELECTED_TASKS_PROVIDER) ?: return null
        return preferredAuthorityToProviderName(preferredAuthority, context.packageManager)
    }

    /**
     * Returns the currently selected tasks provider (if it's still available = installed).
     *
     * @return the currently selected tasks provider, or null if none is available
     */
    fun currentProviderLive(context: Context): LiveData<ProviderName?> {
        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
        return settingsManager.getStringLive(Settings.SELECTED_TASKS_PROVIDER).map { preferred ->
            if (preferred != null)
                preferredAuthorityToProviderName(preferred, context.packageManager)
            else
                null
        }
    }

    private fun preferredAuthorityToProviderName(
        preferredAuthority: String,
        packageManager: PackageManager
    ): ProviderName? {
        ProviderName.entries.toTypedArray()
            .sortedByDescending { it.authority == preferredAuthority }
            .forEach { providerName ->
                if (packageManager.resolveContentProvider(providerName.authority, 0) != null)
                    return providerName
            }
        return null
    }

    fun isAvailable(context: Context) = currentProvider(context) != null

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
     * Sets up sync for the current TaskProvider (and disables sync for unavailable task providers):
     *
     * 1. Makes selected tasks authority _syncable_ in the sync framework, all other authorities _not syncable_.
     * 2. Creates periodic sync worker for selected authority, disables periodic sync workers for all other authorities.
     * 3. If the permissions don't allow synchronizing with the selected tasks app, a notification is shown.
     *
     * Called
     *
     * - when a user explicitly selects another task app, or
     * - when there previously was no (usable) tasks app and [at.bitfire.davdroid.TasksWatcher] detected a new one.
     */
    fun selectProvider(context: Context, selectedProvider: ProviderName?) {
        Logger.log.info("Selecting tasks app: $selectedProvider")

        val settingsManager = EntryPointAccessors.fromApplication(context, TaskUtilsEntryPoint::class.java).settingsManager()
        settingsManager.putString(Settings.SELECTED_TASKS_PROVIDER, selectedProvider?.authority)

        var permissionsRequired = false     // whether additional permissions are required

        // check all accounts and (de)activate task provider(s) if a CalDAV service is defined
        val db = EntryPointAccessors.fromApplication(context, SyncUtils.SyncUtilsEntryPoint::class.java).appDatabase()
        val accountManager = AccountManager.get(context)
        for (account in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
            val hasCalDAV = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
            for (providerName in TaskProvider.ProviderName.entries) {
                val syncable = hasCalDAV && providerName == selectedProvider

                // enable/disable sync for the given account and authority
                setSyncable(
                    context,
                    account,
                    providerName.authority,
                    syncable
                )

                // if sync has just been enabled: check whether additional permissions are required
                if (syncable && !PermissionUtils.havePermissions(context, providerName.permissions))
                    permissionsRequired = true
            }
        }

        if (permissionsRequired) {
            Logger.log.warning("Tasks synchronization is now enabled for at least one account, but permissions are not granted")
            PermissionUtils.notifyPermissions(context, null)
        }
    }

    private fun setSyncable(context: Context, account: Account, authority: String, syncable: Boolean) {
        val settingsManager by lazy { EntryPointAccessors.fromApplication(context, SyncUtils.SyncUtilsEntryPoint::class.java).settingsManager() }
        try {
            val settings = AccountSettings(context, account)
            if (syncable) {
                Logger.log.info("Enabling $authority sync for $account")

                // make account syncable by sync framework
                ContentResolver.setIsSyncable(account, authority, 1)

                // set sync interval according to settings; also updates periodic sync workers and sync framework on-content-change
                val interval = settings.getTasksSyncInterval() ?: settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL)
                settings.setSyncInterval(authority, interval)
            } else {
                Logger.log.info("Disabling $authority sync for $account")

                // make account not syncable by sync framework
                ContentResolver.setIsSyncable(account, authority, 0)

                // disable periodic sync worker
                PeriodicSyncWorker.disable(context, account, authority)
            }
        } catch (e: InvalidAccountException) {
            // account has already been removed, make sure periodic sync is disabled, too
            PeriodicSyncWorker.disable(context, account, authority)
        }
    }

}