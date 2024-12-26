/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.app.NotificationCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.repository.AccountRepository
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
    @ApplicationContext private val context: Context,
    private val accountRepository: Lazy<AccountRepository>,
    private val automaticSyncManager: AutomaticSyncManager,
    private val logger: Logger,
    private val notificationRegistry: Lazy<NotificationRegistry>,
    private val settingsManager: SettingsManager
) {

    /**
     * Gets the currently selected tasks app, if installed.
     *
     * @return currently selected tasks app (when installed), or `null` if no tasks app is selected or the selected app is not installed
     */
    fun currentProvider(): ProviderName? {
        val authority = settingsManager.getString(Settings.SELECTED_TASKS_PROVIDER) ?: return null
        return authorityToProviderName(authority)
    }

    /**
     * Like [currentProvider, but as a [Flow].
     */
    fun currentProviderFlow(): Flow<ProviderName?> =
        settingsManager.getStringFlow(Settings.SELECTED_TASKS_PROVIDER).map { preferred ->
            if (preferred != null)
                authorityToProviderName(preferred)
            else
                null
        }

    /**
     * Converts an authority to a [ProviderName], if the authority is known and the provider is installed.
     */
    private fun authorityToProviderName(authority: String): ProviderName? =
        ProviderName.entries
            .firstOrNull { it.authority == authority }
            .takeIf { context.packageManager.resolveContentProvider(authority, 0) != null }


    /**
     * Sets up sync for the selected TaskProvider.
     */
    fun selectProvider(selectedProvider: ProviderName?) {
        logger.info("Selecting tasks app: $selectedProvider")

        val selectedAuthority = selectedProvider?.authority
        settingsManager.putString(Settings.SELECTED_TASKS_PROVIDER, selectedAuthority)

        // check permission
        if (selectedProvider != null && !PermissionUtils.havePermissions(context, selectedProvider.permissions))
            notificationRegistry.get().notifyPermissions()

        // check all accounts and update task sync
        for (account in accountRepository.get().getAll())
            automaticSyncManager.updateAutomaticSync(account, SyncDataType.TASKS)
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
            } catch (_: PackageManager.NameNotFoundException) {
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