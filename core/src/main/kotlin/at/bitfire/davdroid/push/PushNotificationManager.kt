/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import at.bitfire.davdroid.R
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.davdroid.ui.account.AccountActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PushNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationRegistry: NotificationRegistry
) {

    /**
     * Generates the notification ID for a push notification.
     */
    private fun notificationId(account: Account, dataType: SyncDataType): Int {
        return account.name.hashCode() + account.type.hashCode() + dataType.hashCode()
    }

    /**
     * Sends a notification to inform the user that a push notification has been received, the
     * sync has been scheduled, but it still has not run.
     */
    fun notify(account: Account, dataType: SyncDataType) {
        notificationRegistry.notifyIfPossible(notificationId(account, dataType)) {
            NotificationCompat.Builder(context, notificationRegistry.CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle(context.getString(R.string.sync_notification_pending_push_title))
                .setContentText(context.getString(R.string.sync_notification_pending_push_message))
                .setSubText(account.name)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(
                    TaskStackBuilder.create(context)
                        .addNextIntentWithParentStack(
                            Intent(context, AccountActivity::class.java).apply {
                                putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                            }
                        )
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                .build()
        }
    }

    /**
     * Once the sync has been started, the notification is no longer needed and can be dismissed.
     * It's safe to call this method even if the notification has not been shown.
     */
    fun dismiss(account: Account, dataType: SyncDataType) {
        NotificationManagerCompat.from(context)
            .cancel(notificationId(account, dataType))
    }

}
