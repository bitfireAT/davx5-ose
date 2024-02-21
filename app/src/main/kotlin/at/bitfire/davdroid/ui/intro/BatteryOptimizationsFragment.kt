/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsFragment.Model.Companion.HINT_AUTOSTART_PERMISSION
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsFragment.Model.Companion.HINT_BATTERY_OPTIMIZATIONS
import at.bitfire.davdroid.ui.widget.SafeAndroidUriHandler
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.multibindings.IntoSet
import org.apache.commons.text.WordUtils
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class BatteryOptimizationsFragment: Fragment() {

    val model by viewModels<Model>()

    private val ignoreBatteryOptimizationsResultLauncher =
        registerForActivityResult(IgnoreBatteryOptimizationsContract) {
            model.checkWhitelisted()
        }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    val hintBatteryOptimizations by model.hintBatteryOptimizations.observeAsState()
                    val hintAutostartPermission by model.hintAutostartPermission.observeAsState()
                    val shouldBeWhitelisted by model.shouldBeWhitelisted.observeAsState(false)
                    val isWhitelisted by model.isWhitelisted.observeAsState(false)

                    LaunchedEffect(shouldBeWhitelisted, isWhitelisted) {
                        if (shouldBeWhitelisted && !isWhitelisted)
                            ignoreBatteryOptimizationsResultLauncher.launch(BuildConfig.APPLICATION_ID)
                    }

                    val uriHandler = SafeAndroidUriHandler(LocalContext.current)
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        Content(
                            dontShowBattery = hintBatteryOptimizations == false,
                            onChangeDontShowBattery = {
                                model.hintBatteryOptimizations.value = !it
                            },
                            isWhitelisted = isWhitelisted,
                            shouldBeWhitelisted = shouldBeWhitelisted,
                            onChangeShouldBeWhitelisted = model.shouldBeWhitelisted::postValue,
                            dontShowAutostart = hintAutostartPermission == false,
                            onChangeDontShowAutostart = {
                                model.hintAutostartPermission.value = !it
                            },
                            manufacturerWarning = Model.manufacturerWarning
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkWhitelisted()
    }


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        val settings: SettingsManager
    ): AndroidViewModel(application) {

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

            fun isWhitelisted(context: Context) =
                context.getSystemService<PowerManager>()!!.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
        }

        val shouldBeWhitelisted = MutableLiveData<Boolean>()
        val isWhitelisted = MutableLiveData<Boolean>()
        val hintBatteryOptimizations = settings.getBooleanLive(HINT_BATTERY_OPTIMIZATIONS)

        val hintAutostartPermission = settings.getBooleanLive(HINT_AUTOSTART_PERMISSION)

        fun checkWhitelisted() {
            val whitelisted = isWhitelisted(getApplication())
            isWhitelisted.value = whitelisted
            shouldBeWhitelisted.value = whitelisted

            // if DAVx5 is whitelisted, always show a reminder as soon as it's not whitelisted anymore
            if (whitelisted)
                settings.remove(HINT_BATTERY_OPTIMIZATIONS)
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


    @Module
    @InstallIn(ActivityComponent::class)
    abstract class BatteryOptimizationsFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: Factory): IntroFragmentFactory
    }

    class Factory @Inject constructor(
        val settingsManager: SettingsManager
    ): IntroFragmentFactory {

        override fun getOrder(context: Context) =
            // show fragment when:
            // 1. DAVx5 is not whitelisted yet and "don't show anymore" has not been clicked, and/or
            // 2a. evil manufacturer AND
            // 2b. "don't show anymore" has not been clicked
            if (
                    (!Model.isWhitelisted(context) && settingsManager.getBooleanOrNull(HINT_BATTERY_OPTIMIZATIONS) != false) ||
                    (Model.manufacturerWarning && settingsManager.getBooleanOrNull(HINT_AUTOSTART_PERMISSION) != false)
            )
                100
            else
                IntroFragmentFactory.DONT_SHOW

        override fun create() = BatteryOptimizationsFragment()
    }

}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun Content_Preview() {
    MdcTheme {
        Content(
            dontShowBattery = true,
            onChangeDontShowBattery = {},
            isWhitelisted = false,
            shouldBeWhitelisted = true,
            onChangeShouldBeWhitelisted = {},
            dontShowAutostart = false,
            onChangeDontShowAutostart = {},
            manufacturerWarning = true
        )
    }
}

@Composable
private fun Content(
    dontShowBattery: Boolean,
    onChangeDontShowBattery: (Boolean) -> Unit,
    isWhitelisted: Boolean,
    shouldBeWhitelisted: Boolean,
    onChangeShouldBeWhitelisted: (Boolean) -> Unit,
    dontShowAutostart: Boolean,
    onChangeDontShowAutostart: (Boolean) -> Unit,
    manufacturerWarning: Boolean
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Card {
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
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = shouldBeWhitelisted,
                        onCheckedChange = {
                            // Only accept click events if not whitelisted
                            if (!isWhitelisted) {
                                onChangeShouldBeWhitelisted(it)
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
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = 12.dp)
                )
                AnimatedVisibility(visible = !isWhitelisted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowBattery,
                            onCheckedChange = onChangeDontShowBattery,
                            enabled = !isWhitelisted
                        )
                        Text(
                            text = stringResource(R.string.intro_battery_dont_show),
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier
                                .clickable { onChangeDontShowBattery(!dontShowBattery) }
                        )
                    }
                }
            }
        }
        if (manufacturerWarning) {
            Card(
                modifier = Modifier.padding(top = 8.dp)
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
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.intro_autostart_text),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            uriHandler.openUri(
                                App.homepageUrl(context)
                                    .buildUpon()
                                    .appendPath("faq")
                                    .appendPath("synchronization-is-not-run-as-expected")
                                    .appendQueryParameter(
                                        "manufacturer",
                                        Build.MANUFACTURER.lowercase(Locale.ROOT)
                                    )
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
                            style = MaterialTheme.typography.caption,
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
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(90.dp))
    }
}
