/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SyncStatusObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.StorageLowReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
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
    context: Application,
    storageLowReceiver: StorageLowReceiver
): AndroidViewModel(context), SyncStatusObserver {

    /** whether storage is low (prevents sync framework from running synchronization) */
    val storageLow = storageLowReceiver.storageLow

    /** whether global sync is disabled (sync framework won't run automatic synchronization in this case) */
    val globalSyncDisabled = MutableLiveData<Boolean>()
    private var syncStatusObserver: Any? = null

    /** whether a usable network connection is available (sync framework won't run synchronization otherwise) */
    val networkAvailable = MutableLiveData<Boolean>()
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!

    val batterySaverActive = MutableLiveData<Boolean>()
    private val batterySaverListener: BroadcastReceiver

    /** whether data saver is restricting background synchronization ([ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED]) */
    val dataSaverEnabled = MutableLiveData<Boolean>()
    private val dataSaverChangedListener: BroadcastReceiver

    init {
        // Automatic Sync
        syncStatusObserver = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
        onStatusChanged(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS)

        // Network
        watchConnectivity()

        // Battery saver
        batterySaverListener = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                checkBatterySaver()
            }
        }
        val batterySaverListenerFilter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(batterySaverListener, batterySaverListenerFilter)
        checkBatterySaver()

        // Data saver
        dataSaverChangedListener = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                checkDataSaver()
            }
        }
        val dataSaverChangedFilter = IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
        context.registerReceiver(dataSaverChangedListener, dataSaverChangedFilter)
        checkDataSaver()
    }

    private fun checkBatterySaver() {
        batterySaverActive.postValue(
            getApplication<Application>().getSystemService<PowerManager>()?.isPowerSaveMode
        )
    }

    private fun checkDataSaver() {
        dataSaverEnabled.postValue(
            getApplication<Application>().getSystemService<ConnectivityManager>()?.let { connectivityManager ->
                connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            }
        )
    }

    override fun onCleared() {
        val context = getApplication<Application>()

        // Automatic sync
        ContentResolver.removeStatusChangeListener(syncStatusObserver)

        // Network
        connectivityManager.unregisterNetworkCallback(networkCallback)

        // Battery saver
        context.unregisterReceiver(batterySaverListener)

        // Data Saver
        context.unregisterReceiver(dataSaverChangedListener)
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