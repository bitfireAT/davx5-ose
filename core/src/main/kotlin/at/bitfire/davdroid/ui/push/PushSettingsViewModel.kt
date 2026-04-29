/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.push

import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.di.qualifier.ApplicationScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.push.PushDistributorManager
import at.bitfire.davdroid.ui.push.PushSettingsContract.Event
import at.bitfire.davdroid.ui.push.PushSettingsContract.Event.PushDistributorSelected
import at.bitfire.davdroid.ui.push.PushSettingsContract.Event.PushEnabled
import at.bitfire.davdroid.ui.push.PushSettingsContract.PushDistributorInfo
import at.bitfire.davdroid.ui.push.PushSettingsContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PushSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val pushDistributorManager: PushDistributorManager
) : ViewModel() {
    private val packageManager = context.packageManager

    private val _uiState = MutableStateFlow(State())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            loadPushSetting()
            loadPushDistributors()
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is PushEnabled -> handlePushEnabled(event.enabled)
            is PushDistributorSelected -> handlePushDistributorSelected(event.packageName)
        }
    }

    private fun handlePushEnabled(enabled: Boolean) {
        updateState { state ->
            state.copy(isPushEnabled = enabled)
        }

        applicationScope.launch(ioDispatcher) {
            pushDistributorManager.setPushEnabled(enabled)
        }
    }

    private fun handlePushDistributorSelected(packageName: String) {
        updateState { state ->
            state.copy(selectedPushDistributor = packageName)
        }

        applicationScope.launch(ioDispatcher) {
            pushDistributorManager.setPushDistributorAndEnablePush(packageName)
        }
    }

    private fun loadPushSetting() {
        updateState { state ->
            state.copy(isPushEnabled = pushDistributorManager.isPushEnabled())
        }
    }

    private fun loadPushDistributors() {
        val defaultDistributor = pushDistributorManager.getDefaultDistributor()
        val selectedDistributor = pushDistributorManager.getSelectedDistributor() ?: defaultDistributor
        val pushDistributors = pushDistributorManager.getDistributors()
            .mapNotNull { pushDistributor ->
                if (pushDistributor == context.packageName) {
                    if (isPlayServicesAvailable()) {
                        PushDistributorInfo(
                            packageName = pushDistributor,
                            appName = context.getString(R.string.app_settings_unifiedpush_distributor_fcm),
                            appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                        )
                    } else {
                        null
                    }
                } else {
                    try {
                        val applicationInfo = packageManager.getApplicationInfo(pushDistributor, 0)
                        val label = packageManager.getApplicationLabel(applicationInfo).toString()
                        val icon = packageManager.getApplicationIcon(applicationInfo)

                        PushDistributorInfo(pushDistributor, label, icon)
                    } catch (_: PackageManager.NameNotFoundException) {
                        // The app is not available for some reason, do not include the app data.
                        null
                    }
                }
            }

        updateState { state ->
            state.copy(
                selectedPushDistributor = selectedDistributor,
                defaultPushDistributor = defaultDistributor,
                pushDistributors = pushDistributors
            )
        }
    }

    // Copied from embedded-fcm-distributor
    private fun isPlayServicesAvailable(): Boolean {
        try {
            packageManager.getPackageInfo("com.google.android.gms", PackageManager.GET_ACTIVITIES)
            return true
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
    }

    private fun updateState(block: (State) -> State) {
        _uiState.value = block(_uiState.value)
    }
}
