/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.app.Application
import android.content.ContentResolver
import android.content.IntentFilter
import android.content.SyncStatusObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.StorageLowReceiver
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Watches some conditions that result in *Warnings* that should
 * be shown to the user in the launcher activity. The variables are
 * available as LiveData so they can be directly observed in the UI.
 *
 * Currently watches:
 *
 *   - whether storage is low → [storageLow]
 *   - whether global sync is disabled → [globalSyncDisabled]
 *   - whether a network connection is available → [networkAvailable]
 *   - whether data saver is turned on -> [dataSaverEnabled]
 */
@HiltViewModel
class AppWarningsModel @Inject constructor(
    val context: Application,
    storageLowReceiver: StorageLowReceiver
): ViewModel(), SyncStatusObserver {

    /** whether storage is low (prevents sync framework from running synchronization) */
    val storageLow = storageLowReceiver.storageLow

    /** whether global sync is disabled (sync framework won't run automatic synchronization in this case) */
    val globalSyncDisabled = MutableLiveData<Boolean>()
    private var syncStatusObserver: Any? = null

    /** whether a usable network connection is available (sync framework won't run synchronization otherwise) */
    val networkAvailable = MutableLiveData<Boolean>()
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private val powerManager = context.getSystemService<PowerManager>()!!
    /** whether battery saver is active */
    val batterySaverActive = broadcastReceiverFlow(context, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        .map { powerManager.isPowerSaveMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    /** whether data saver is restricting background synchronization ([ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED]) */
    val dataSaverEnabled = broadcastReceiverFlow(context, IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED))
        .map { connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    init {
        // Automatic Sync
        syncStatusObserver = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
        onStatusChanged(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS)

        // Network
        watchConnectivity()
    }

    override fun onCleared() {
        // Automatic sync
        ContentResolver.removeStatusChangeListener(syncStatusObserver)

        // Network
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onStatusChanged(which: Int) {
        globalSyncDisabled.postValue(!ContentResolver.getMasterSyncAutomatically())
    }

    private fun watchConnectivity() {
        networkAvailable.postValue(false)

        // check for working (e.g. WiFi after captive portal login) Internet connection
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        networkCallback = object: ConnectivityManager.NetworkCallback() {
            val availableNetworks = hashSetOf<Network>()

            override fun onAvailable(network: Network) {
                availableNetworks += network
                update()
            }

            override fun onLost(network: Network) {
                availableNetworks -= network
                update()
            }

            private fun update() {
                networkAvailable.postValue(availableNetworks.isNotEmpty())
            }
        }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

}