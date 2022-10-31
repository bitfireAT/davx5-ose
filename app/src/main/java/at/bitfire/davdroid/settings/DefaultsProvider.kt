/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.getSystemService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

class DefaultsProvider(
    context: Context,
    settingsManager: SettingsManager
): BaseDefaultsProvider(context, settingsManager) {

    override val booleanDefaults = mutableMapOf(
        Pair(Settings.DISTRUST_SYSTEM_CERTIFICATES, false),
        Pair(Settings.SYNC_ALL_COLLECTIONS, false),
        Pair(Settings.FORCE_READ_ONLY_ADDRESSBOOKS, false)
    )

    override val intDefaults = mapOf(
        Pair(Settings.PROXY_TYPE, Settings.PROXY_TYPE_SYSTEM),
        Pair(Settings.PROXY_PORT, 9050)     // Orbot SOCKS
    )

    override val longDefaults = mapOf<String, Long>(
        Pair(Settings.DEFAULT_SYNC_INTERVAL, 4*3600)    /* 4 hours */
    )

    override val stringDefaults = mapOf(
        Pair(Settings.PROXY_HOST, "localhost")
    )

    val dataSaverChangedListener by lazy {
        object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                evaluateDataSaver(true)
            }
        }
    }


    init {
        if (Build.VERSION.SDK_INT >= 24) {
            val dataSaverChangedFilter = IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
            context.registerReceiver(dataSaverChangedListener, dataSaverChangedFilter)
            evaluateDataSaver()
        }
    }

    override fun forceReload() {
        evaluateDataSaver()
    }

    override fun close() {
        if (Build.VERSION.SDK_INT >= 24)
            context.unregisterReceiver(dataSaverChangedListener)
    }

    fun evaluateDataSaver(notify: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 24) {
            context.getSystemService<ConnectivityManager>()?.let { connectivityManager ->
                if (connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
                    booleanDefaults[AccountSettings.KEY_WIFI_ONLY] = true
                else
                    booleanDefaults -= AccountSettings.KEY_WIFI_ONLY
            }
            if (notify)
                settingsManager.onSettingsChanged()
        }
    }


    class Factory @Inject constructor(): SettingsProviderFactory {
        override fun getProviders(context: Context, settingsManager: SettingsManager) =
            listOf(DefaultsProvider(context, settingsManager))
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class DefaultsProviderFactoryModule {
        @Binds
        @IntoMap @IntKey(/* priority */ 0)
        abstract fun factory(impl: Factory): SettingsProviderFactory
    }

}