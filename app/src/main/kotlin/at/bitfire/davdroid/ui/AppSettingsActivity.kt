/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPage
import at.bitfire.davdroid.ui.intro.OpenSourcePage
import at.bitfire.davdroid.ui.widget.EditTextInputDialog
import at.bitfire.davdroid.ui.widget.MultipleChoiceInputDialog
import at.bitfire.davdroid.ui.widget.Setting
import at.bitfire.davdroid.ui.widget.SettingsHeader
import at.bitfire.davdroid.ui.widget.SwitchSetting
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppSettingsActivity: AppCompatActivity() {

    companion object {
        const val APP_SETTINGS_HELP_URL = "https://manual.davx5.com/settings.html#app-wide-settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                AppSettings()
            }
        }
    }

    @SuppressLint("BatteryLife")
    @Composable
    fun AppSettings(model: Model = viewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current

        val snackbarHostState = remember { SnackbarHostState() }
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onSupportNavigateUp() }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    },
                    title = { Text(stringResource(R.string.app_settings)) },
                    actions = {
                        IconButton(onClick = {
                            uriHandler.openUri(APP_SETTINGS_HELP_URL)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(Modifier.padding(8.dp)) {
                    AppSettings_Debugging(
                        verboseLogging = model.getPrefBoolean(Logger.LOG_TO_FILE).observeAsState().value ?: false,
                        onUpdateVerboseLogging = { model.putPrefBoolean(Logger.LOG_TO_FILE, it) },
                        batterySavingExempted = model.getBatterySavingExempted().observeAsState(false).value,
                        onExemptFromBatterySaving = {
                            startActivity(Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                            ))
                        },
                        onBatterySavingSettings = {
                            startActivity(Intent(
                                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                            ))
                        }
                    )

                    AppSettings_Connection(
                        proxyType = model.settings.getIntLive(Settings.PROXY_TYPE).observeAsState().value ?: Settings.PROXY_TYPE_NONE,
                        onProxyTypeUpdated = { model.settings.putInt(Settings.PROXY_TYPE, it) },
                        proxyHostName = model.settings.getStringLive(Settings.PROXY_HOST).observeAsState(null).value,
                        onProxyHostNameUpdated = { model.settings.putString(Settings.PROXY_HOST, it) },
                        proxyPort = model.settings.getIntLive(Settings.PROXY_PORT).observeAsState(null).value,
                        onProxyPortUpdated = { model.settings.putInt(Settings.PROXY_PORT, it) }
                    )

                    AppSettings_Security(
                        distrustSystemCerts = model.settings.getBooleanLive(Settings.DISTRUST_SYSTEM_CERTIFICATES).observeAsState().value ?: false,
                        onDistrustSystemCertsUpdated = { model.settings.putBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES, it) },
                        onResetCertificates = {
                            model.resetCertificates()

                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(getString(R.string.app_settings_reset_certificates_success))
                            }
                        }
                    )

                    AppSettings_UserInterface(
                        theme = model.settings.getIntLive(Settings.PREFERRED_THEME).observeAsState().value ?: Settings.PREFERRED_THEME_DEFAULT,
                        onThemeSelected = {
                            model.settings.putInt(Settings.PREFERRED_THEME, it)
                            UiUtils.updateTheme(context)
                        },
                        onResetHints = {
                            model.resetHints()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(getString(R.string.app_settings_reset_hints_success))
                            }
                        }
                    )

                    AppSettings_Integration(
                        taskProvider = TaskUtils.currentProviderLive(context).observeAsState().value
                    )
                }
            }
        }
    }

    @Composable
    fun AppSettings_Debugging(
        verboseLogging: Boolean,
        onUpdateVerboseLogging: (Boolean) -> Unit,
        batterySavingExempted: Boolean,
        onExemptFromBatterySaving: () -> Unit,
        onBatterySavingSettings: () -> Unit
    ) {
        val context = LocalContext.current

        SettingsHeader {
            Text(stringResource(R.string.app_settings_debug))
        }

        Setting(
            icon = Icons.Default.BugReport,
            name = stringResource(R.string.app_settings_show_debug_info),
            summary = stringResource(R.string.app_settings_show_debug_info_details)
        ) {
            context.startActivity(Intent(context, DebugInfoActivity::class.java))
        }

        SwitchSetting(
            icon = Icons.Default.Adb,
            checked = verboseLogging,
            name = stringResource(R.string.app_settings_logging),
            summaryOn = stringResource(R.string.app_settings_logging_on),
            summaryOff = stringResource(R.string.app_settings_logging_off)
        ) {
            onUpdateVerboseLogging(it)
        }

        SwitchSetting(
            checked = batterySavingExempted,
            icon = Icons.Default.SyncProblem.takeUnless { batterySavingExempted },
            name = stringResource(R.string.app_settings_battery_optimization),
            summaryOn = stringResource(R.string.app_settings_battery_optimization_exempted),
            summaryOff = stringResource(R.string.app_settings_battery_optimization_optimized)
        ) {
            if (batterySavingExempted)
                onBatterySavingSettings()
            else
                onExemptFromBatterySaving()
        }
    }

    @Composable
    @Preview
    fun AppSettings_Debugging_Preview() {
        Column {
            AppSettings_Debugging(
                verboseLogging = false,
                onUpdateVerboseLogging = {},
                batterySavingExempted = true,
                onExemptFromBatterySaving = {},
                onBatterySavingSettings = {}
            )
        }
    }

    @Composable
    fun AppSettings_Connection(
        proxyType: Int,
        onProxyTypeUpdated: (Int) -> Unit = {},
        proxyHostName: String? = null,
        onProxyHostNameUpdated: (String) -> Unit = {},
        proxyPort: Int? = null,
        onProxyPortUpdated: (Int) -> Unit = {}
    ) {
        SettingsHeader(divider = true) {
            Text(stringResource(R.string.app_settings_connection))
        }

        val proxyTypeNames = stringArrayResource(R.array.app_settings_proxy_types)
        val proxyTypeValues = stringArrayResource(R.array.app_settings_proxy_type_values).map { it.toInt() }
        var showProxyTypeInputDialog by remember { mutableStateOf(false) }
        Setting(
            name = stringResource(R.string.app_settings_proxy),
            summary = proxyTypeNames[proxyTypeValues.indexOf(proxyType)]
        ) {
            showProxyTypeInputDialog = true
        }
        if (showProxyTypeInputDialog)
            MultipleChoiceInputDialog(
                title = stringResource(R.string.app_settings_proxy),
                namesAndValues = proxyTypeNames.zip(proxyTypeValues.map { it.toString() }),
                initialValue = proxyType.toString(),
                onValueSelected = { newValue ->
                    onProxyTypeUpdated(newValue.toInt())
                },
                onDismiss = { showProxyTypeInputDialog = false }
            )

        var showProxyHostNameInputDialog by remember { mutableStateOf(false) }
        Setting(
            name = stringResource(R.string.app_settings_proxy_host),
            summary = proxyHostName
        ) {
            showProxyHostNameInputDialog = true
        }
        if (showProxyHostNameInputDialog)
            EditTextInputDialog(
                title = stringResource(R.string.app_settings_proxy_host),
                initialValue = proxyHostName,
                keyboardType = KeyboardType.Uri,
                onValueEntered = onProxyHostNameUpdated,
                onDismiss = { showProxyHostNameInputDialog = false }
            )

        var showProxyPortInputDialog by remember { mutableStateOf(false) }
        Setting(
            name = stringResource(R.string.app_settings_proxy_port),
            summary = proxyPort?.toString()
        ) {
            showProxyPortInputDialog = true
        }
        if (showProxyPortInputDialog)
            EditTextInputDialog(
                title = stringResource(R.string.app_settings_proxy_port),
                initialValue = proxyPort?.toString(),
                keyboardType = KeyboardType.Number,
                onValueEntered = {
                    try {
                        val newPort = it.toInt()
                        if (newPort in 1..65535)
                            onProxyPortUpdated(newPort)
                    } catch(_: NumberFormatException) {
                        // user entered invalid port number
                    }
                },
                onDismiss = { showProxyPortInputDialog = false }
            )
    }

    @Composable
    @Preview
    fun AppSettings_Connection_Preview() {
        Column {
            AppSettings_Connection(
                proxyType = Settings.PROXY_TYPE_HTTP
            )
        }
    }

    @Composable
    fun AppSettings_Security(
        distrustSystemCerts: Boolean,
        onDistrustSystemCertsUpdated: (Boolean) -> Unit = {},
        onResetCertificates: () -> Unit = {}
    ) {
        val context = LocalContext.current

        SettingsHeader(divider = true) {
            Text(stringResource(R.string.app_settings_security))
        }

        SwitchSetting(
            checked = distrustSystemCerts,
            name = stringResource(R.string.app_settings_distrust_system_certs),
            summaryOn = stringResource(R.string.app_settings_distrust_system_certs_on),
            summaryOff = stringResource(R.string.app_settings_distrust_system_certs_off)
        ) {
            onDistrustSystemCertsUpdated(it)
        }

        Setting(
            name = stringResource(R.string.app_settings_reset_certificates),
            summary = stringResource(R.string.app_settings_reset_certificates_summary),
            onClick = onResetCertificates
        )

        Setting(
            name = stringResource(R.string.app_settings_security_app_permissions),
            summary = stringResource(R.string.app_settings_security_app_permissions_summary),
            onClick = {
                context.startActivity(Intent(context, PermissionsActivity::class.java))
            }
        )
    }

    @Composable
    @Preview
    fun AppSettings_Security_Preview() {
        Column {
            AppSettings_Security(
                distrustSystemCerts = false
            )
        }
    }

    @Composable
    fun AppSettings_UserInterface(
        theme: Int,
        onThemeSelected: (Int) -> Unit = {},
        onResetHints: () -> Unit = {}
    ) {
        SettingsHeader(divider = true) {
            Text(stringResource(R.string.app_settings_user_interface))
        }

        if (Build.VERSION.SDK_INT >= 26)
            Setting(
                icon = Icons.Default.Notifications,
                name = stringResource(R.string.app_settings_notification_settings),
                summary = stringResource(R.string.app_settings_notification_settings_summary)
            ) {
                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                }
                startActivity(intent)
            }

        val themeNames = stringArrayResource(R.array.app_settings_theme_names)
        val themeValues = stringArrayResource(R.array.app_settings_theme_values).map { it.toInt() }
        var showThemeDialog by remember { mutableStateOf(false) }
        val themeValueIdx = themeValues.indexOf(theme).takeIf { it != -1 }
        Setting(
            icon = Icons.Default.InvertColors,
            name = stringResource(R.string.app_settings_theme_title),
            summary = themeValueIdx?.let { themeNames[it] }
        ) {
            showThemeDialog = true
        }
        if (showThemeDialog)
            MultipleChoiceInputDialog(
                title = stringResource(R.string.app_settings_theme_title),
                namesAndValues = themeNames.zip(themeValues.map { it.toString() }),
                initialValue = theme.toString(),
                onValueSelected = {
                    onThemeSelected(it.toInt())
                },
                onDismiss = { showThemeDialog = false }
            )

        Setting(
            name = stringResource(R.string.app_settings_reset_hints),
            summary = stringResource(R.string.app_settings_reset_hints_summary),
            onClick = onResetHints
        )
    }

    @Composable
    @Preview
    fun AppSettings_UserInterface_Preview() {
        Column {
            AppSettings_UserInterface(
                theme = Settings.PREFERRED_THEME_DEFAULT
            )
        }
    }

    @Composable
    fun AppSettings_Integration(
        taskProvider: TaskProvider.ProviderName? = null
    ) {
        val context = LocalContext.current

        SettingsHeader(divider = true) {
            Text(stringResource(R.string.app_settings_integration))
        }

        val pm = context.packageManager
        val appInfo = taskProvider?.packageName?.let { pkgName ->
            pm.getApplicationInfo(pkgName, 0)
        }
        val appName = appInfo?.loadLabel(pm)?.toString()
        Setting(
            name = {
                Text(stringResource(R.string.app_settings_tasks_provider))
            },
            icon = {
                if (appInfo != null) {
                    val icon = appInfo.loadIcon(pm)
                    Image(icon.toBitmap().asImageBitmap(), appName)
                }
            },
            summary = appName ?: stringResource(R.string.app_settings_tasks_provider_none)
        ) {
            context.startActivity(Intent(context, TasksActivity::class.java))
        }
    }

    @Composable
    @Preview
    fun AppSettings_Integration_Preview() {
        Column {
            AppSettings_Integration()
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        val context: Application,
        val settings: SettingsManager
    ) : ViewModel() {

        private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        fun getBatterySavingExempted(): LiveData<Boolean> = object : LiveData<Boolean>() {
            val receiver = object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    update()
                }
            }

            override fun onActive() {
                context.registerReceiver(receiver, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED))
                update()
            }

            override fun onInactive() {
                context.unregisterReceiver(receiver)
            }

            private fun update() {
                context.getSystemService<PowerManager>()?.let { powerManager ->
                    val exempted = powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
                    postValue(exempted)
                }
            }
        }

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
            settings.remove(BatteryOptimizationsPage.Model.HINT_BATTERY_OPTIMIZATIONS)
            settings.remove(BatteryOptimizationsPage.Model.HINT_AUTOSTART_PERMISSION)
            settings.remove(TasksActivity.Model.HINT_OPENTASKS_NOT_INSTALLED)
            settings.remove(OpenSourcePage.Model.SETTING_NEXT_DONATION_POPUP)
        }

    }

}