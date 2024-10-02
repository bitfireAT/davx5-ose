package at.bitfire.davdroid.push

import android.accounts.Account
import android.content.Context
import androidx.core.app.NotificationCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.NotificationRegistry
import java.util.logging.Logger

object PushNotificationManager {
    private val logger = Logger.getLogger("PushNotificationManager")

    /**
     * Generates the notification ID for a push notification.
     */
    private fun notificationId(account: Account, authority: String): Int {
        return account.name.hashCode() + account.type.hashCode() + authority.hashCode()
    }

    /**
     * Sends a notification to inform the user that a push notification has been received, the
     * sync has been scheduled, but it still has not run.
     */
    fun notifyScheduled(context: Context, account: Account, authority: String) {
        val notificationRegistry = NotificationRegistry(context, logger)

        notificationRegistry.notifyIfPossible(notificationId(account, authority)) {
            NotificationCompat.Builder(context, notificationRegistry.CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle(context.getString(R.string.sync_notification_pending_push_title))
                .setContentText(context.getString(R.string.sync_notification_pending_push_message))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }
}
