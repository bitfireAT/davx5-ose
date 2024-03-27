/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.log.Logger
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
    object StorageLowReceiverModule {
        @Provides
        @Singleton
        fun storageLowReceiver(@ApplicationContext context: Context) = StorageLowReceiver(context)
    }


    val storageLow = MutableLiveData(false)

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
    }

    fun onStorageOk() {
        Logger.log.info("Storage OK again")

        storageLow.postValue(false)
    }

}