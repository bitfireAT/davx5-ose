/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPage.Model.Companion.HINT_AUTOSTART_PERMISSION
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPage.Model.Companion.HINT_BATTERY_OPTIMIZATIONS
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

class BatteryOptimizationsPage: IntroPage {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BatteryOptimizationsPageEntryPoint {
        fun settingsManager(): SettingsManager
    }

    override fun getShowPolicy(application: Application): IntroPage.ShowPolicy {
        val settingsManager = EntryPointAccessors.fromApplication(application, BatteryOptimizationsPageEntryPoint::class.java).settingsManager()

        // show fragment when:
        // 1. DAVx5 is not whitelisted yet and "don't show anymore" has not been clicked, and/or
        // 2a. evil manufacturer AND
        // 2b. "don't show anymore" has not been clicked
        return if (
            (!Model.isExempted(application) && settingsManager.getBooleanOrNull(HINT_BATTERY_OPTIMIZATIONS) != false) ||
            (Model.manufacturerWarning && settingsManager.getBooleanOrNull(HINT_AUTOSTART_PERMISSION) != false)
        )
            IntroPage.ShowPolicy.SHOW_ALWAYS
        else
            IntroPage.ShowPolicy.DONT_SHOW
    }

    @Composable
    override fun ComposePage() {
        BatteryOptimizationsPageContent()
    }


    @SuppressLint("BatteryLife")
    object IgnoreBatteryOptimizationsContract: ActivityResultContract<String, Unit?>() {
        override fun createIntent(context: Context, input: String): Intent {
            return Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$input")
            )
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Unit? {
            return null
        }
    }

}
