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
import at.bitfire.davdroid.push.PushRegistrationManager
import at.bitfire.davdroid.ui.push.PushSettingsContract.PushDistributorInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor
import javax.inject.Inject

@HiltViewModel
class PushSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pushRegistrationManager: PushRegistrationManager,
) : ViewModel() {
    private val pm = context.packageManager

    private val _uiState = MutableStateFlow(PushSettingsContract.State())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadPushDistributors()
        }
    }

    private fun loadPushDistributors() {
        when (val result = UnifiedPush.resolveDefaultDistributor(context)) {
            is ResolvedDistributor.Found -> _uiState.value = _uiState.value.copy(defaultPushDistributor = result.packageName, selectedPushDistributor = result.packageName)
            ResolvedDistributor.NoneAvailable -> Unit
            ResolvedDistributor.ToSelect -> Unit
        }

        val pushDistributors = pushRegistrationManager.getDistributors()
            .mapNotNull { pushDistributor ->
                if (pushDistributor == context.packageName) {
                    return@mapNotNull PushDistributorInfo(
                        packageName = pushDistributor,
                        appName = context.getString(R.string.app_settings_unifiedpush_distributor_fcm),
                        appIcon = AppCompatResources.getDrawable(context, R.drawable.product_logomark_cloud_messaging_full_color)
                    )
                }

                try {
                    val applicationInfo = pm.getApplicationInfo(pushDistributor, 0)
                    val label = pm.getApplicationLabel(applicationInfo).toString()
                    val icon = pm.getApplicationIcon(applicationInfo)
                    PushDistributorInfo(pushDistributor, label, icon)
                } catch (_: PackageManager.NameNotFoundException) {
                    // The app is not available for some reason, do not include the app data.
                    null
                }
            }

        _uiState.value = _uiState.value.copy(pushDistributors = pushDistributors)
    }

}

