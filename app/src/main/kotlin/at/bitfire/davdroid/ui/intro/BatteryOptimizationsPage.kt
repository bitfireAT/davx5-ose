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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.M2Theme
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
import org.apache.commons.text.WordUtils

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
        IntroPage_FromModel()
    }

    @Composable
    private fun IntroPage_FromModel(
        model: Model = viewModel()
    ) {
        val ignoreBatteryOptimizationsResultLauncher = rememberLauncherForActivityResult(IgnoreBatteryOptimizationsContract) {
            model.checkBatteryOptimizations()
        }

        val hintBatteryOptimizations by model.hintBatteryOptimizations.collectAsStateWithLifecycle(false)
        val shouldBeExempted = model.shouldBeExempted
        val isExempted = model.isExempted
        LaunchedEffect(shouldBeExempted, isExempted) {
            if (shouldBeExempted && !isExempted)
                ignoreBatteryOptimizationsResultLauncher.launch(BuildConfig.APPLICATION_ID)
        }

        val hintAutostartPermission by model.hintAutostartPermission.collectAsStateWithLifecycle(false)
        BatteryOptimizationsContent(
            dontShowBattery = hintBatteryOptimizations == false,
            onChangeDontShowBattery = model::updateHintBatteryOptimizations,
            isExempted = isExempted,
            shouldBeExempted = shouldBeExempted,
            onChangeShouldBeExempted = model::updateShouldBeExempted,
            dontShowAutostart = hintAutostartPermission == false,
            onChangeDontShowAutostart = model::updateHintAutostartPermission,
            manufacturerWarning = Model.manufacturerWarning
        )
    }


    @HiltViewModel
    class Model @Inject constructor(
        val context: Application,
        private val settings: SettingsManager
    ): ViewModel() {

        companion object {

            /**
             * Whether the request for whitelisting from battery optimizations shall be shown.
             * If this setting is true or null/not set, the notice shall be shown. Only if this
             * setting is false, the notice shall not be shown.
             */
            const val HINT_BATTERY_OPTIMIZATIONS = "hint_BatteryOptimizations"

            /**
             * Whether the autostart permission notice shall be shown. If this setting is true
             * or null/not set, the notice shall be shown. Only if this setting is false, the notice
             * shall not be shown.
             *
             * Type: Boolean
             */
            const val HINT_AUTOSTART_PERMISSION = "hint_AutostartPermissions"

            /**
             * List of manufacturers which are known to restrict background processes or otherwise
             * block synchronization.
             *
             * See https://www.davx5.com/faq/synchronization-is-not-run-as-expected for why this is evil.
             * See https://github.com/jaredrummler/AndroidDeviceNames/blob/master/json/ for manufacturer values.
             */
            private val evilManufacturers = arrayOf("asus", "huawei", "lenovo", "letv", "meizu", "nokia",
                    "oneplus", "oppo", "samsung", "sony", "vivo", "wiko", "xiaomi", "zte")

            /**
             * Whether the device has been produced by an evil manufacturer.
             *
             * Always true for debug builds (to test the UI).
             *
             * @see evilManufacturers
             */
            val manufacturerWarning =
                    (evilManufacturers.contains(Build.MANUFACTURER.lowercase(Locale.ROOT)) || BuildConfig.DEBUG)

            fun isExempted(context: Context) =
                context.getSystemService<PowerManager>()!!.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
        }

        var shouldBeExempted by mutableStateOf(true)
            private set
        var isExempted by mutableStateOf(false)
            private set

        val hintBatteryOptimizations = settings.getBooleanFlow(HINT_BATTERY_OPTIMIZATIONS)

        val hintAutostartPermission = settings.getBooleanFlow(HINT_AUTOSTART_PERMISSION)

        init {
            viewModelScope.launch {
                broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED)).collect {
                    checkBatteryOptimizations()
                }
            }
        }

        fun checkBatteryOptimizations() {
            val exempted = isExempted(context)
            isExempted = exempted
            shouldBeExempted = exempted

            // if DAVx5 is whitelisted, always show a reminder as soon as it's not whitelisted anymore
            if (exempted)
                settings.remove(HINT_BATTERY_OPTIMIZATIONS)
        }

        fun updateShouldBeExempted(value: Boolean) {
            shouldBeExempted = value
        }

        fun updateHintBatteryOptimizations(value: Boolean) {
            settings.putBoolean(HINT_BATTERY_OPTIMIZATIONS, value)
        }

        fun updateHintAutostartPermission(value: Boolean) {
            settings.putBoolean(HINT_AUTOSTART_PERMISSION, value)
        }

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

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun BatteryOptimizationsContent_Preview() {
    M2Theme {
        BatteryOptimizationsContent(
            dontShowBattery = true,
            onChangeDontShowBattery = {},
            isExempted = false,
            shouldBeExempted = true,
            onChangeShouldBeExempted = {},
            dontShowAutostart = false,
            onChangeDontShowAutostart = {},
            manufacturerWarning = true
        )
    }
}

@Composable
private fun BatteryOptimizationsContent(
    dontShowBattery: Boolean,
    onChangeDontShowBattery: (Boolean) -> Unit,
    isExempted: Boolean,
    shouldBeExempted: Boolean,
    onChangeShouldBeExempted: (Boolean) -> Unit,
    dontShowAutostart: Boolean,
    onChangeDontShowAutostart: (Boolean) -> Unit,
    manufacturerWarning: Boolean
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.intro_battery_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = shouldBeExempted,
                        onCheckedChange = {
                            // Only accept click events if not whitelisted
                            if (!isExempted) {
                                onChangeShouldBeExempted(it)
                            }
                        },
                        enabled = !dontShowBattery
                    )
                }
                Text(
                    text = stringResource(
                        R.string.intro_battery_text,
                        stringResource(R.string.app_name)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                AnimatedVisibility(visible = !isExempted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowBattery,
                            onCheckedChange = onChangeDontShowBattery,
                            enabled = !isExempted
                        )
                        Text(
                            text = stringResource(R.string.intro_battery_dont_show),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { onChangeDontShowBattery(!dontShowBattery) }
                        )
                    }
                }
            }
        }
        if (manufacturerWarning) {
            Card(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.intro_autostart_title,
                            WordUtils.capitalize(Build.MANUFACTURER)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.intro_autostart_text),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            uriHandler.openUri(
                                Constants.HOMEPAGE_URL.buildUpon()
                                    .appendPath(Constants.HOMEPAGE_PATH_FAQ)
                                    .appendPath(Constants.HOMEPAGE_PATH_FAQ_SYNC_NOT_RUN)
                                    .appendQueryParameter(
                                        "manufacturer",
                                        Build.MANUFACTURER.lowercase(Locale.ROOT)
                                    )
                                    .withStatParams("BatteryOptimizationsPage")
                                    .build().toString()
                            )
                        }
                    ) {
                        Text(stringResource(R.string.intro_more_info))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowAutostart,
                            onCheckedChange = onChangeDontShowAutostart
                        )
                        Text(
                            text = stringResource(R.string.intro_autostart_dont_show),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { onChangeDontShowAutostart(!dontShowAutostart) }
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(
                R.string.intro_leave_unchecked,
                stringResource(R.string.app_settings_reset_hints)
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(90.dp))
    }

}