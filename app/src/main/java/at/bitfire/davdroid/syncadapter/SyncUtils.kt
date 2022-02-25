/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.TaskProvider

object SyncUtils {

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

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            flags = flags or PendingIntent.FLAG_IMMUTABLE

        if (intent.resolveActivity(pm) != null)
            notify.setContentIntent(PendingIntent.getActivity(context, 0, intent, flags))

        nm.notify(NotificationUtils.NOTIFY_TASKS_PROVIDER_TOO_OLD, notify.build())
    }

}