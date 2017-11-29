/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import at.bitfire.davdroid.R

object NotificationUtils {

    val CHANNEL_DEBUG = "debug"
    val CHANNEL_SYNC_STATUS = "syncStatus"
    val CHANNEL_SYNC_PROBLEMS = "syncProblems"

    fun createChannels(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannels(listOf(
                    NotificationChannel(CHANNEL_DEBUG, context.getString(R.string.notification_channel_debugging), NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(CHANNEL_SYNC_STATUS, context.getString(R.string.notification_channel_sync_status), NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(CHANNEL_SYNC_PROBLEMS, context.getString(R.string.notification_channel_sync_problems), NotificationManager.IMPORTANCE_DEFAULT)
            ))

        return nm
    }

}