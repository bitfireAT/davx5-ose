package at.bitfire.davdroid.ui

import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject

@HiltViewModel
class AppSettingsModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val preference: PreferenceRepository,
    private val settings: SettingsManager,
    private val tasksAppManager: TasksAppManager
) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadPushDistributors()
        }
    }

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

    private val pm: PackageManager = context.packageManager
    private val appInfoFlow = tasksAppManager.currentProviderFlow(viewModelScope).map { tasksProvider ->
        tasksProvider?.packageName?.let { pkgName ->
            pm.getApplicationInfo(pkgName, 0)
        }
    }
    val tasksAppName = appInfoFlow.map { it?.loadLabel(pm)?.toString() }
    val tasksAppIcon = appInfoFlow.map { it?.loadIcon(pm) }


    // push

    val pushEndpoint = preference.unifiedPushEndpointFlow()

    private val _pushDistributor = MutableStateFlow<String?>(null)
    val pushDistributor get() = _pushDistributor.asStateFlow()

    private val _pushDistributors = MutableStateFlow<List<PushDistributorInfo>?>(null)
    val pushDistributors get() = _pushDistributors.asStateFlow()

    fun updatePushDistributor(pushDistributor: String) {
        viewModelScope.launch(Dispatchers.IO) {
            UnifiedPush.saveDistributor(context, pushDistributor)
            UnifiedPush.registerApp(context)
            _pushDistributor.emit(pushDistributor)
        }
    }

    private suspend fun loadPushDistributors() {
        val savedPushDistributor = UnifiedPush.getSavedDistributor(context)
        _pushDistributor.emit(savedPushDistributor)

        val pushDistributors = UnifiedPush.getDistributors(context)
            .map { pushDistributor ->
                try {
                    val applicationInfo = pm.getApplicationInfo(pushDistributor, 0)
                    val label = pm.getApplicationLabel(applicationInfo).toString()
                    val icon = pm.getApplicationIcon(applicationInfo)
                    PushDistributorInfo(pushDistributor, label, icon)
                } catch (_: PackageManager.NameNotFoundException) {
                    // The app is not available for some reason, do not include the app data.
                    PushDistributorInfo(pushDistributor)
                }
            }
        _pushDistributors.emit(pushDistributors)

        // If there's only one distributor, select it by default.
        pushDistributors.singleOrNull()?.let { (distributor) ->
            updatePushDistributor(distributor)
        }

        // If there's already a distributor configured, register the app
        UnifiedPush.getAckDistributor(context)?.let {
            UnifiedPush.registerApp(context)
        }
    }


    data class PushDistributorInfo(
        val packageName: String,
        val appName: String? = null,
        val appIcon: Drawable? = null
    )

}