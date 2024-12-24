/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.app.NotificationCompat
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Responsible for setting/getting the currently used tasks app, and for communicating with it.
 */
class TasksAppManager @Inject constructor(
    private val automaticSyncManager: AutomaticSyncManager,
    @ApplicationContext private val context: Context,
    private val accountRepository: Lazy<AccountRepository>,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val db: AppDatabase,
    private val logger: Logger,
    private val notificationRegistry: Lazy<NotificationRegistry>,
    private val settingsManager: SettingsManager
) {

    /**
     * Gets the currently selected tasks app.
     *
     * @return currently selected tasks app (when installed), or `null` if no tasks app is selected or the selected app is not installed
     */
    fun currentProvider(): ProviderName? {
        val preferredAuthority = settingsManager.getString(Settings.SELECTED_TASKS_PROVIDER) ?: return null
        return preferredAuthorityToProviderName(preferredAuthority)
    }

    /**
     * Like [currentProvider], but as a [Flow].
     */
    fun currentProviderFlow(): Flow<ProviderName?> {
        return settingsManager.getStringFlow(Settings.SELECTED_TASKS_PROVIDER).map { preferred ->
            if (preferred != null)
                preferredAuthorityToProviderName(preferred)
            else
                null
        }
    }

    private fun preferredAuthorityToProviderName(preferredAuthority: String): ProviderName? {
        val packageManager = context.packageManager
        ProviderName.entries.toTypedArray()
            .sortedByDescending { it.authority == preferredAuthority }
            .forEach { providerName ->
                if (packageManager.resolveContentProvider(providerName.authority, 0) != null)
                    return providerName
            }
        return null
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
     * - when there previously was no (usable) tasks app and [at.bitfire.davdroid.startup.TasksAppWatcher] detected a new one.
     */
    fun selectProvider(selectedProvider: ProviderName?) {
        logger.info("Selecting tasks app: $selectedProvider")

        settingsManager.putString(Settings.SELECTED_TASKS_PROVIDER, selectedProvider?.authority)

        var permissionsRequired = false     // whether additional permissions are required

        // check all accounts and (de)activate task provider(s) if a CalDAV service is defined
        for (account in accountRepository.get().getAll()) {
            val hasCalDAV = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
            for (providerName in TaskProvider.ProviderName.entries) {
                val syncable = hasCalDAV && providerName == selectedProvider

                // enable/disable sync for the given account and authority
                setSyncable(account, providerName.authority, syncable)

                // if sync has just been enabled: check whether additional permissions are required
                if (syncable && !PermissionUtils.havePermissions(context, providerName.permissions))
                    permissionsRequired = true
            }
        }

        if (permissionsRequired) {
            logger.warning("Tasks synchronization is now enabled for at least one account, but permissions are not granted")
                notificationRegistry.get().notifyPermissions()
        }
    }

    private fun setSyncable(account: Account, authority: String, syncable: Boolean) {
        try {
            val settings = accountSettingsFactory.create(account)
            if (syncable) {
                logger.info("Enabling $authority sync for $account")

                // set sync interval according to settings; also updates periodic sync workers and sync framework on-content-change
                val interval = settings.getTasksSyncInterval() ?: settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL)
                settings.setSyncInterval(authority, interval)
            } else {
                logger.info("Disabling $authority sync for $account")
                automaticSyncManager.disable(account, authority)
            }
        } catch (_: InvalidAccountException) {
            // account has already been removed, make sure periodic sync is disabled, too
            automaticSyncManager.disable(account, authority)
        }
    }


    /**
     * Show a notification that starts an Intent and redirects the user to the tasks app in the app store.
     *
     * @param e   the TaskProvider.ProviderTooOldException to be shown
     */
    fun notifyProviderTooOld(e: TaskProvider.ProviderTooOldException) {
        val registry = notificationRegistry.get()
        registry.notifyIfPossible(NotificationRegistry.NOTIFY_TASKS_PROVIDER_TOO_OLD) {
            val message = context.getString(R.string.sync_error_tasks_required_version, e.provider.minVersionName)

            val pm = context.packageManager
            val tasksAppInfo = pm.getPackageInfo(e.provider.packageName, 0)
            val tasksAppLabel = tasksAppInfo.applicationInfo?.loadLabel(pm)

            val notify = NotificationCompat.Builder(context, registry.CHANNEL_SYNC_ERRORS)
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

            notify.build()
        }
    }

}