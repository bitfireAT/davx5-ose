/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import java.util.logging.Level

@Deprecated("Use NotificationRegistry instead")
object NotificationUtils {

    @Deprecated("Use NotificationCompat.Builder instead", ReplaceWith("NotificationCompat.Builder(context, channel)"))
    fun newBuilder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setColor(ResourcesCompat.getColor(context.resources, R.color.primaryColor, null))


    @Deprecated("Use NotificationRegistry.notifyIfPossible instead")
    fun NotificationManagerCompat.notifyIfPossible(tag: String?, id: Int, notification: Notification) {
        try {
            notify(tag, id, notification)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Couldn't post notification (SecurityException)", notification)
        }
    }

    @Deprecated("Use NotificationRegistry.notifyIfPossible instead")
    fun NotificationManagerCompat.notifyIfPossible(id: Int, notification: Notification) =
        notifyIfPossible(null, id, notification)

}