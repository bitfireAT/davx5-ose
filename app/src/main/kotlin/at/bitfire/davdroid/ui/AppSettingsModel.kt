package at.bitfire.davdroid.ui

import android.app.Application
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPageModel
import at.bitfire.davdroid.ui.intro.OpenSourcePage
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppSettingsModel @Inject constructor(
    val context: Application,
    val settings: SettingsManager
) : ViewModel() {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val powerManager = context.getSystemService<PowerManager>()!!
    val batterySavingExempted = broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED), immediate = true)
        .map { powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val pm = context.packageManager
    private val appInfoFlow = TaskUtils.currentProviderFlow(context, viewModelScope).map { tasksProvider ->
        tasksProvider?.packageName?.let { pkgName ->
            pm.getApplicationInfo(pkgName, 0)
        }
    }
    val appName = appInfoFlow.map { it?.loadLabel(pm)?.toString() }
    val icon = appInfoFlow.map { it?.loadIcon(pm) }

    fun getPrefBoolean(keyToObserve: String): LiveData<Boolean?> =
        object : LiveData<Boolean?>(), SharedPreferences.OnSharedPreferenceChangeListener {
            override fun onActive() {
                preferences.registerOnSharedPreferenceChangeListener(this)
                update()
            }

            override fun onInactive() {
                preferences.unregisterOnSharedPreferenceChangeListener(this)
            }

            private fun update() {
                if (preferences.contains(keyToObserve))
                    postValue(preferences.getBoolean(keyToObserve, false))
                else
                    postValue(null)
            }

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
                if (key == keyToObserve)
                    update()
            }

        }

    fun putPrefBoolean(key: String, value: Boolean) {
        preferences
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun resetCertificates() {
        CustomCertStore.getInstance(context).clearUserDecisions()
    }

    fun resetHints() {
        settings.remove(BatteryOptimizationsPageModel.HINT_BATTERY_OPTIMIZATIONS)
        settings.remove(BatteryOptimizationsPageModel.HINT_AUTOSTART_PERMISSION)
        settings.remove(TasksModel.HINT_OPENTASKS_NOT_INSTALLED)
        settings.remove(OpenSourcePage.Model.SETTING_NEXT_DONATION_POPUP)
    }

}