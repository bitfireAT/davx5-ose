/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.davdroid.R
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.push.PushDistributorManager
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.ui.PushSummary.PushDisabled
import at.bitfire.davdroid.ui.PushSummary.PushEnabled
import at.bitfire.davdroid.ui.PushSummary.PushLoading
import at.bitfire.davdroid.ui.intro.BackupsPage
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPageViewModel
import at.bitfire.davdroid.ui.intro.OpenSourcePage
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customCertStore: Optional<CustomCertStore>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val preferences: PreferenceRepository,
    private val pushDistributorManager: PushDistributorManager,
    private val settings: SettingsManager,
    tasksAppManager: TasksAppManager
) : ViewModel() {

    // debugging

    private val powerManager = context.getSystemService<PowerManager>()!!
    val batterySavingExempted =
        broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED), immediate = true)
            .map { powerManager.isIgnoringBatteryOptimizations(context.packageName) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun verboseLogging() = preferences.logToFileFlow()
    fun updateVerboseLogging(verbose: Boolean) {
        preferences.logToFile(verbose)
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

    val hasCustomCertStore = customCertStore.isPresent

    fun distrustSystemCertificates() = settings.getBooleanFlow(Settings.DISTRUST_SYSTEM_CERTIFICATES)
    fun updateDistrustSystemCertificates(distrust: Boolean) {
        settings.putBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES, distrust)
    }

    fun resetCertificates() {
        customCertStore.getOrNull()?.clearUserDecisions()
    }


    // user interface

    fun theme() = settings.getIntFlow(Settings.PREFERRED_THEME)
    fun updateTheme(theme: Int) {
        settings.putInt(Settings.PREFERRED_THEME, theme)
        UiUtils.updateTheme(context)
    }

    fun resetHints() = viewModelScope.launch(ioDispatcher) {
        settings.remove(BackupsPage.Model.SETTING_BACKUPS_ACCEPTED)
        settings.remove(BatteryOptimizationsPageViewModel.HINT_BATTERY_OPTIMIZATIONS)
        settings.remove(BatteryOptimizationsPageViewModel.HINT_AUTOSTART_PERMISSION)
        settings.remove(OpenSourcePage.Model.SETTING_NEXT_DONATION_POPUP)
        settings.remove(TasksViewModel.HINT_OPENTASKS_NOT_INSTALLED)
    }


    // tasks

    private val pm: PackageManager = context.packageManager
    private val appInfoFlow = tasksAppManager.currentProviderFlow().map { tasksProvider ->
        tasksProvider?.packageName?.let { pkgName ->
            pm.getApplicationInfo(pkgName, 0)
        }
    }
    val tasksAppName = appInfoFlow.map { it?.loadLabel(pm)?.toString() }
    val tasksAppIcon = appInfoFlow.map { it?.loadIcon(pm) }


    // push

    //TODO: Listen to changes and update this when Push settings are changed
    private val _pushSummary = MutableStateFlow<PushSummary>(PushLoading)
    val pushSummary = _pushSummary.asStateFlow()

    private fun loadPushConfiguration() {
        _pushSummary.value = if (pushDistributorManager.isPushEnabled()) {
            val (appName, appIcon) = getPushDistributorInfo()
            PushEnabled(appName = appName, appIcon = appIcon)
        } else {
            PushDisabled
        }
    }

    private fun getPushDistributorInfo(): Pair<String, Drawable?> {
        return when (val packageName = pushDistributorManager.getSelectedDistributor()) {
            null -> {
                context.getString(R.string.app_settings_unifiedpush_no_endpoint) to null
            }
            context.packageName -> {
                val appName = context.getString(R.string.app_settings_unifiedpush_distributor_fcm)
                val appIcon = ContextCompat.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                appName to appIcon
            }
            else -> {
                try {
                    val applicationInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(applicationInfo).toString()
                    val appIcon = pm.getApplicationIcon(applicationInfo)
                    appName to appIcon
                } catch (_: NameNotFoundException) {
                    // Failed to load the app name, use package name instead
                    packageName to null
                }
            }
        }
    }

    init {
        viewModelScope.launch(ioDispatcher) {
            loadPushConfiguration()
        }
    }
}

sealed interface PushSummary {
    object PushLoading : PushSummary
    object PushDisabled : PushSummary
    data class PushEnabled(val appName: String, val appIcon: Drawable?) : PushSummary
}
