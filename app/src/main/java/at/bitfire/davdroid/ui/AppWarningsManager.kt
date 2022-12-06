/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.getSystemService
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.StorageLowReceiver
import at.bitfire.davdroid.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
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
 */
class AppWarningsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    storageLowReceiver: StorageLowReceiver
) : AutoCloseable, SyncStatusObserver {

    /** whether storage is low (prevents sync framework from running synchronization) */
    val storageLow = storageLowReceiver.storageLow

    /** whether global sync is disabled (sync framework won't run automatic synchronization in this case) */
    val globalSyncDisabled = MutableLiveData(false)
    private var syncStatusObserver: Any? = null

    /** whether a usable network connection is available (sync framework won't run synchronization otherwise) */
    val networkAvailable = MutableLiveData<Boolean>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkReceiver: BroadcastReceiver? = null
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!

    init {
        Logger.log.fine("Watching for warning conditions")

        // Automatic sync
        syncStatusObserver = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
        onStatusChanged(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS)

        // Network
        watchConnectivity()
    }

    private fun watchConnectivity() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {    // API level <26
            networkReceiver = object: BroadcastReceiver() {
                init {
                    update()
                }

                override fun onReceive(context: Context?, intent: Intent?) = update()

                private fun update() {
                    networkAvailable.postValue(connectivityManager.allNetworkInfo.any { it.isConnected })
                }
            }
            @Suppress("DEPRECATION")
            context.registerReceiver(networkReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            )

        } else {    // API level >= 26
            networkAvailable.postValue(false)

            // check for working (e.g. WiFi after captive portal login) Internet connection
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            val callback = object: ConnectivityManager.NetworkCallback() {
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
            connectivityManager.registerNetworkCallback(networkRequest, callback)
            networkCallback = callback
        }
    }

    override fun onStatusChanged(which: Int) {
        globalSyncDisabled.postValue(!ContentResolver.getMasterSyncAutomatically())
    }

    override fun close() {
        Logger.log.fine("Stopping watching for warning conditions")

        // Automatic sync
        ContentResolver.removeStatusChangeListener(syncStatusObserver)

        // Network
        networkReceiver?.let {
            context.unregisterReceiver(it)
        }
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }

}