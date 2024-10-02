package at.bitfire.davdroid.push

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.davdroid.R
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
    private fun notificationId(account: Account, authority: String): Int {
        return account.name.hashCode() + account.type.hashCode() + authority.hashCode()
    }

    /**
     * Sends a notification to inform the user that a push notification has been received, the
     * sync has been scheduled, but it still has not run.
     */
    fun notify(account: Account, authority: String) {
        notificationRegistry.notifyIfPossible(notificationId(account, authority)) {
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
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, AccountActivity::class.java).apply {
                            putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        }
    }

    /**
     * Once the sync has been started, the notification is no longer needed and can be dismissed.
     * It's safe to call this method even if the notification has not been shown.
     */
    fun dismissScheduled(account: Account, authority: String) {
        NotificationManagerCompat.from(context)
            .cancel(notificationId(account, authority))
    }
}
