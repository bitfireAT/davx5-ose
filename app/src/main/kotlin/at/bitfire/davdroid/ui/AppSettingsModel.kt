/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

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
import at.bitfire.davdroid.push.PushRegistrationManager
import at.bitfire.davdroid.repository.DavCollectionRepository
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
    private val collectionRepository: DavCollectionRepository,
    private val preferences: PreferenceRepository,
    private val pushRegistrationManager: PushRegistrationManager,
    private val settings: SettingsManager,
    tasksAppManager: TasksAppManager
) : ViewModel() {


    // debugging

    private val powerManager = context.getSystemService<PowerManager>()!!
    val batterySavingExempted = broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED), immediate = true)
        .map { powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) }
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
    private val appInfoFlow = tasksAppManager.currentProviderFlow().map { tasksProvider ->
        tasksProvider?.packageName?.let { pkgName ->
            pm.getApplicationInfo(pkgName, 0)
        }
    }
    val tasksAppName = appInfoFlow.map { it?.loadLabel(pm)?.toString() }
    val tasksAppIcon = appInfoFlow.map { it?.loadIcon(pm) }


    // push

    private val _pushDistributor = MutableStateFlow<String?>(null)
    val pushDistributor = _pushDistributor.asStateFlow()

    private val _pushDistributors = MutableStateFlow<List<PushDistributorInfo>?>(null)
    val pushDistributors = _pushDistributors.asStateFlow()

    /**
     * Loads the push distributors configuration:
     *
     * - Loads the currently selected distributor into [pushDistributor].
     * - Loads all the available distributors into [pushDistributors].
     * - If there's only one push distributor available, and none is selected, it's selected automatically.
     * - Makes sure the app is registered with UnifiedPush if there's already a distributor selected.
     */
    private fun loadPushDistributors() {
        val savedPushDistributor = UnifiedPush.getSavedDistributor(context)
        _pushDistributor.value = savedPushDistributor

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
        _pushDistributors.value = pushDistributors
    }

    /**
     * Updates the current push distributor selection.
     *
     * Saves the preference in UnifiedPush, (un)registers the app, and writes the selection to [pushDistributor].
     *
     * @param pushDistributor The package name of the push distributor, _null_ to disable push.
     */
    fun updatePushDistributor(pushDistributor: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (pushDistributor == null) {
                // Disable UnifiedPush if the distributor given is null
                UnifiedPush.removeDistributor(context)
            } else {
                // If a distributor was passed, store it
                UnifiedPush.saveDistributor(context, pushDistributor)

                // … and register it so that UnifiedPushReceiver.onNewEndpoint is called
                pushRegistrationManager.update()
            }
            _pushDistributor.value = pushDistributor
        }
    }


    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadPushDistributors()
        }
    }


    data class PushDistributorInfo(
        val packageName: String,
        val appName: String? = null,
        val appIcon: Drawable? = null
    )

}