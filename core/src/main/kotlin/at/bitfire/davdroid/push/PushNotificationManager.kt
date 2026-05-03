/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
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

    fun notify(
        id: Int,
        channelId: String,
        @DrawableRes icon: Int = R.drawable.ic_sync,
        title: String,
        text: String,
        subText: String? = null,
        intent: Intent,
        priority: Int = NotificationCompat.PRIORITY_LOW,
        category: String = NotificationCompat.CATEGORY_STATUS,
        autoCancel: Boolean = true,
        onlyAlertOnce: Boolean = true
    ) {
        notificationRegistry.notifyIfPossible(id) {
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setPriority(priority)
                .setCategory(category)
                .setAutoCancel(autoCancel)
                .setOnlyAlertOnce(onlyAlertOnce)
                .setContentIntent(
                    TaskStackBuilder.create(context)
                        .addNextIntentWithParentStack(intent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                .build()
        }
    }

    /**
     * Sends a message to inform the user that a push notification has been received, the sync has been scheduled, but it still has not run.
     */
    fun notify(account: Account, dataType: SyncDataType) {
        notify(
            id = notificationId(account, dataType),
            channelId = notificationRegistry.CHANNEL_STATUS,
            title = context.getString(R.string.sync_notification_pending_push_title),
            text = context.getString(R.string.sync_notification_pending_push_message),
            subText = account.name,
            intent = Intent(context, AccountActivity::class.java).apply {
                putExtra(AccountActivity.EXTRA_ACCOUNT, account)
            }
        )
    }

    /**
     * Dismisses the notification with the given [notificationId]. If no such notification is shown, nothing happens.
     */
    fun dismiss(notificationId: Int) {
        NotificationManagerCompat.from(context)
            .cancel(notificationId)
    }

    /**
     * Once the sync has been started, the notification is no longer needed and can be dismissed.
     * It's safe to call this method even if the notification has not been shown.
     */
    fun dismiss(account: Account, dataType: SyncDataType) = dismiss(notificationId(account, dataType))

}
