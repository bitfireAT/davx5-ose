package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.composable.EditTextInputDialog
import at.bitfire.davdroid.ui.composable.MultipleChoiceInputDialog
import at.bitfire.davdroid.ui.composable.Setting
import at.bitfire.davdroid.ui.composable.SettingsHeader
import at.bitfire.davdroid.ui.composable.SwitchSetting
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(
    onExemptFromBatterySaving: () -> Unit,
    onBatterSavingSettings: () -> Unit,
    onStartTasksApp: () -> Unit,
    onNavUp: () -> Unit,
    onShowNotificationSettings: () -> Unit
) {
    val model: AppSettingsActivity.Model = viewModel()
    val context = LocalContext.current

    AppTheme {
        AppSettingsScreen(
            verboseLogging = model.getPrefBoolean(Logger.LOG_TO_FILE).observeAsState().value ?: false,
            onUpdateVerboseLogging = { model.putPrefBoolean(Logger.LOG_TO_FILE, it) },
            batterySavingExempted = model.batterySavingExempted.collectAsStateWithLifecycle().value,
            onExemptFromBatterySaving = onExemptFromBatterySaving,
            onBatterySavingSettings = onBatterSavingSettings,

            // AppSettings Connection
            proxyType = model.settings.getIntFlow(Settings.PROXY_TYPE).collectAsStateWithLifecycle(null).value ?: Settings.PROXY_TYPE_NONE,
            onProxyTypeUpdated = { model.settings.putInt(Settings.PROXY_TYPE, it) },
            proxyHostName = model.settings.getStringFlow(Settings.PROXY_HOST).collectAsStateWithLifecycle(null).value,
            onProxyHostNameUpdated = { model.settings.putString(Settings.PROXY_HOST, it) },
            proxyPort = model.settings.getIntFlow(Settings.PROXY_PORT).collectAsStateWithLifecycle(null).value,
            onProxyPortUpdated = { model.settings.putInt(Settings.PROXY_PORT, it) },

            // AppSettings Security
            distrustSystemCerts = model.settings.getBooleanFlow(Settings.DISTRUST_SYSTEM_CERTIFICATES).collectAsStateWithLifecycle(null).value ?: false,
            onDistrustSystemCertsUpdated = { model.settings.putBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES, it) },
            onResetCertificates = { model.resetCertificates() },

            // AppSettings UserInterface
            theme = model.settings.getIntFlow(Settings.PREFERRED_THEME).collectAsStateWithLifecycle(null).value ?: Settings.PREFERRED_THEME_DEFAULT,
            onThemeSelected = {
                model.settings.putInt(Settings.PREFERRED_THEME, it)
                UiUtils.updateTheme(context)
            },
            onResetHints = { model.resetHints() },

            // AppSettings Integration
            tasksAppName = model.appName.collectAsStateWithLifecycle(null).value ?: stringResource(R.string.app_settings_tasks_provider_none),
            tasksAppIcon = model.icon.collectAsStateWithLifecycle(null).value,
            onStartTasksApp = onStartTasksApp,

            onShowNotificationSettings = onShowNotificationSettings,
            onNavUp = onNavUp
        )
    }
}

@SuppressLint("BatteryLife")
@Composable
fun AppSettingsScreen(
    verboseLogging: Boolean,
    onUpdateVerboseLogging: (Boolean) -> Unit,
    batterySavingExempted: Boolean,
    onExemptFromBatterySaving: () -> Unit,
    onBatterySavingSettings: () -> Unit,

    // AppSettings connection
    proxyType: Int,
    onProxyTypeUpdated: (Int) -> Unit,
    proxyHostName: String?,
    onProxyHostNameUpdated: (String) -> Unit,
    proxyPort: Int?,
    onProxyPortUpdated: (Int) -> Unit,

    // AppSettings security
    distrustSystemCerts: Boolean,
    onDistrustSystemCertsUpdated: (Boolean) -> Unit,
    onResetCertificates: () -> Unit,

    // AppSettings UserInterface
    theme: Int,
    onThemeSelected: (Int) -> Unit,
    onResetHints: () -> Unit,

    // AppSettings Integration
    tasksAppName: String,
    tasksAppIcon: Drawable?,
    onStartTasksApp: () -> Unit,

    onShowNotificationSettings: () -> Unit,
    onNavUp: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavUp) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                title = { Text(stringResource(R.string.app_settings)) },
                actions = {
                    IconButton(onClick = {
                        uriHandler.openUri(AppSettingsActivity.APP_SETTINGS_HELP_URL)
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
                    verboseLogging = verboseLogging,
                    onUpdateVerboseLogging = onUpdateVerboseLogging,
                    batterySavingExempted = batterySavingExempted,
                    onExemptFromBatterySaving = onExemptFromBatterySaving,
                    onBatterySavingSettings = onBatterySavingSettings
                )

                AppSettings_Connection(
                    proxyType = proxyType,
                    onProxyTypeUpdated = onProxyTypeUpdated,
                    proxyHostName = proxyHostName,
                    onProxyHostNameUpdated = onProxyHostNameUpdated,
                    proxyPort = proxyPort,
                    onProxyPortUpdated = onProxyPortUpdated,
                )

                val resetCertificatesSuccessMessage = stringResource(R.string.app_settings_reset_certificates_success)
                AppSettings_Security(
                    distrustSystemCerts = distrustSystemCerts,
                    onDistrustSystemCertsUpdated = onDistrustSystemCertsUpdated,
                    onResetCertificates = {
                        onResetCertificates()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(resetCertificatesSuccessMessage)
                        }
                    }
                )

                val resetHintsSuccessMessage = stringResource(R.string.app_settings_reset_hints_success)
                AppSettings_UserInterface(
                    theme = theme,
                    onThemeSelected = onThemeSelected,
                    onResetHints = {
                        onResetHints()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(resetHintsSuccessMessage)
                        }
                    },
                    onShowNotificationSettings = onShowNotificationSettings
                )

                AppSettings_Integration(
                    appName = tasksAppName,
                    icon = tasksAppIcon,
                    onStartTasksApp = onStartTasksApp
                )
            }
        }
    }
}

@Composable
@Preview
fun AppSettingsScreen_Preview() {
    AppTheme {
        AppSettingsScreen(
            verboseLogging = true,
            batterySavingExempted = true,
            proxyType = 0,
            proxyHostName = "true",
            proxyPort = 0,
            distrustSystemCerts = true,
            theme = 0,
            onUpdateVerboseLogging = {},
            onProxyHostNameUpdated = {},
            onExemptFromBatterySaving = {},
            onBatterySavingSettings = {},
            onProxyTypeUpdated = {},
            onProxyPortUpdated = {},
            onDistrustSystemCertsUpdated = {},
            onResetCertificates = {},
            onThemeSelected = {},
            onResetHints = {},
            tasksAppName = "No tasks app",
            tasksAppIcon = null,
            onStartTasksApp = {},
            onShowNotificationSettings = {},
            onNavUp = {},
        )
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

    if (proxyType !in listOf(Settings.PROXY_TYPE_SYSTEM, Settings.PROXY_TYPE_NONE)) {
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
                    } catch (_: NumberFormatException) {
                        // user entered invalid port number
                    }
                },
                onDismiss = { showProxyPortInputDialog = false }
            )
    }
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
    onResetHints: () -> Unit = {},
    onShowNotificationSettings: () -> Unit = {}
) {
    SettingsHeader(divider = true) {
        Text(stringResource(R.string.app_settings_user_interface))
    }

    if (Build.VERSION.SDK_INT >= 26)
        Setting(
            icon = Icons.Default.Notifications,
            name = stringResource(R.string.app_settings_notification_settings),
            summary = stringResource(R.string.app_settings_notification_settings_summary),
            onClick = onShowNotificationSettings
        )

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
    appName: String,
    icon: Drawable? = null,
    onStartTasksApp: () -> Unit = {}
) {
    SettingsHeader(divider = true) {
        Text(stringResource(R.string.app_settings_integration))
    }
    Setting(
        name = {
            Text(stringResource(R.string.app_settings_tasks_provider))
        },
        icon = {
               icon?.let {
                   Image(icon.toBitmap().asImageBitmap(), appName)
               }
        },
        summary = appName,
        onClick = onStartTasksApp
    )
}

@Composable
@Preview
fun AppSettings_Integration_Preview() {
    Column {
        AppSettings_Integration(
            "No Tasks app"
        )
    }
}
