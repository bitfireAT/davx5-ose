package at.bitfire.davdroid.ui

import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPageModel
import at.bitfire.davdroid.ui.intro.OpenSourcePage
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppSettingsModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val preference: PreferenceRepository,
    private val settings: SettingsManager,
    private val tasksAppManager: TasksAppManager
) : ViewModel() {

    // debugging

    private val powerManager = context.getSystemService<PowerManager>()!!
    val batterySavingExempted = broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED), immediate = true)
        .map { powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun verboseLogging() = preference.logToFileFlow()
    fun updateVerboseLogging(verbose: Boolean) {
        preference.logToFile(verbose)
    }


    // connection

    fun proxyType() = settings.getIntFlow(Settings.PROXY_TYPE)
    fun updateProxyType(type: Int) {
        settings.putInt(Settings.PROXY_TYPE, type)
    }

    fun proxyHostName() = settings.getStringFlow(Settings.PROXY_HOST)
    fun updateProxyHostName(host: String) {
        settings.putString(Settings.PROXY_HOST, host)
    }

    fun proxyPort() = settings.getIntFlow(Settings.PROXY_PORT)
    fun updateProxyPort(port: Int) {
        settings.putInt(Settings.PROXY_PORT, port)
    }


    // security

    fun distrustSystemCertificates() = settings.getBooleanFlow(Settings.DISTRUST_SYSTEM_CERTIFICATES)
    fun updateDistrustSystemCertificates(distrust: Boolean) {
        settings.putBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES, distrust)
    }

    fun resetCertificates() {
        CustomCertStore.getInstance(context).clearUserDecisions()
    }


    // user interface

    fun theme() = settings.getIntFlow(Settings.PREFERRED_THEME)
    fun updateTheme(theme: Int) {
        settings.putInt(Settings.PREFERRED_THEME, theme)
        UiUtils.updateTheme(context)
    }

    fun resetHints() {
        settings.remove(BatteryOptimizationsPageModel.HINT_BATTERY_OPTIMIZATIONS)
        settings.remove(BatteryOptimizationsPageModel.HINT_AUTOSTART_PERMISSION)
        settings.remove(TasksModel.HINT_OPENTASKS_NOT_INSTALLED)
        settings.remove(OpenSourcePage.Model.SETTING_NEXT_DONATION_POPUP)
    }


    // tasks

    val pm: PackageManager = context.packageManager
    private val appInfoFlow = tasksAppManager.currentProviderFlow(viewModelScope).map { tasksProvider ->
        tasksProvider?.packageName?.let { pkgName ->
            pm.getApplicationInfo(pkgName, 0)
        }
    }
    val appName = appInfoFlow.map { it?.loadLabel(pm)?.toString() }
    val icon = appInfoFlow.map { it?.loadIcon(pm) }


    // push

    val pushEndpoint = preference.unifiedPushEndpointFlow()

}