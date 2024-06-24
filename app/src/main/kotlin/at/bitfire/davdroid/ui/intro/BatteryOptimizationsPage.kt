/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPageModel.Companion.HINT_AUTOSTART_PERMISSION
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPageModel.Companion.HINT_BATTERY_OPTIMIZATIONS
import javax.inject.Inject

class BatteryOptimizationsPage @Inject constructor(
    private val application: Application,
    private val settingsManager: SettingsManager
): IntroPage {

    override fun getShowPolicy(): IntroPage.ShowPolicy {
        // show fragment when:
        // 1. DAVx5 is not whitelisted yet and "don't show anymore" has not been clicked, and/or
        // 2a. evil manufacturer AND
        // 2b. "don't show anymore" has not been clicked
        return if (
            (!BatteryOptimizationsPageModel.isExempted(application) && settingsManager.getBooleanOrNull(HINT_BATTERY_OPTIMIZATIONS) != false) ||
            (BatteryOptimizationsPageModel.manufacturerWarning && settingsManager.getBooleanOrNull(HINT_AUTOSTART_PERMISSION) != false)
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
