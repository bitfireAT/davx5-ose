/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.NotificationUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

class StorageLowReceiver private constructor(
    val context: Context
): BroadcastReceiver(), AutoCloseable {

    @Module
    @InstallIn(SingletonComponent::class)
    object storageLowReceiverModule {
        @Provides
        @Singleton
        fun storageLowReceiver(@ApplicationContext context: Context) = StorageLowReceiver(context)
    }


    val storageLow = MutableLiveData<Boolean>(false)

    fun listen() {
        Logger.log.fine("Listening for device storage low/OK broadcasts")
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
            addAction(Intent.ACTION_DEVICE_STORAGE_OK)
        }
        context.registerReceiver(this, filter)
    }

    override fun close() {
        context.unregisterReceiver(this)
    }


    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DEVICE_STORAGE_LOW -> onStorageLow()
            Intent.ACTION_DEVICE_STORAGE_OK -> onStorageOk()
        }
    }

    fun onStorageLow() {
        Logger.log.warning("Low storage, sync will not be started by Android!")

        storageLow.postValue(true)

        val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
            .setSmallIcon(R.drawable.ic_storage_notify)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentTitle(context.getString(R.string.storage_low_notify_title))
            .setContentText(context.getString(R.string.storage_low_notify_text))

        val settingsIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        if (settingsIntent.resolveActivity(context.packageManager) != null)
            notify.setContentIntent(PendingIntent.getActivity(context, 0, settingsIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val nm = NotificationManagerCompat.from(context)
        nm.notify(NotificationUtils.NOTIFY_LOW_STORAGE, notify.build())
    }

    fun onStorageOk() {
        Logger.log.info("Storage OK again")

        storageLow.postValue(false)

        val nm = NotificationManagerCompat.from(context)
        nm.cancel(NotificationUtils.NOTIFY_LOW_STORAGE)
    }

}