/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.push.PushDistributorManager
import at.bitfire.davdroid.push.PushDistributorPreference
import at.bitfire.davdroid.push.PushRegistrationManager
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.ui.intro.BackupsPage
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPageModel
import at.bitfire.davdroid.ui.intro.OpenSourcePage
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class AppSettingsModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customCertStore: Optional<CustomCertStore>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val preferences: PreferenceRepository,
    private val pushRegistrationManager: PushRegistrationManager,
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

    fun resetHints() = runBlocking(ioDispatcher) {
        settings.remove(BackupsPage.Model.SETTING_BACKUPS_ACCEPTED)
        settings.remove(BatteryOptimizationsPageModel.HINT_BATTERY_OPTIMIZATIONS)
        settings.remove(BatteryOptimizationsPageModel.HINT_AUTOSTART_PERMISSION)
        settings.remove(OpenSourcePage.Model.SETTING_NEXT_DONATION_POPUP)
        settings.remove(TasksModel.HINT_OPENTASKS_NOT_INSTALLED)
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

    val pushDistributorPreference = pushDistributorManager.getPushDistributorPreferenceFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PushDistributorPreference.Disabled)

    fun updatePushDistributor(preference: PushDistributorPreference) {
        viewModelScope.launch(ioDispatcher) {
            pushDistributorManager.setPushDistributorPreference(preference)
        }
    }

}