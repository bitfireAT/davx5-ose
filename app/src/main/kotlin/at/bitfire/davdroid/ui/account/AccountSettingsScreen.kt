/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Activity
import android.security.KeyChain
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Task
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.ActionCard
import at.bitfire.davdroid.ui.composable.EditTextInputDialog
import at.bitfire.davdroid.ui.composable.MultipleChoiceInputDialog
import at.bitfire.davdroid.ui.composable.Setting
import at.bitfire.davdroid.ui.composable.SettingsHeader
import at.bitfire.davdroid.ui.composable.SwitchSetting
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.vcard4android.GroupMethod
import kotlinx.coroutines.launch

@Composable
fun AccountSettingsScreen(
    onNavUp: () -> Unit,
    account: Account,
    onNavWifiPermissionsScreen: () -> Unit
) {
    val model = hiltViewModel { factory: AccountSettingsModel.Factory ->
        factory.create(account)
    }
    val uiState by model.uiState.collectAsState()
    val canAccessWifiSsid by PermissionUtils.rememberCanAccessWifiSsid()

    // contract to open the browser for re-authentication
    val authRequestContract = rememberLauncherForActivityResult(model.authorizationContract()) { authResponse ->
        if (authResponse != null)
            model.authenticate(authResponse)
        else
            model.authCodeFailed()
    }

    AppTheme {
        AccountSettingsScreen(
            accountName = account.name,
            onNavUp = onNavUp,
            status = uiState.status,

            // Sync settings
            canAccessWifiSsid = canAccessWifiSsid,
            onSyncWifiOnlyPermissionsAction = onNavWifiPermissionsScreen,
            hasContactsSync = uiState.hasContactsSync,
            contactsSyncInterval = uiState.syncIntervalContacts,
            onUpdateContactsSyncInterval = model::updateContactsSyncInterval,
            hasCalendarsSync = uiState.hasCalendarsSync,
            calendarSyncInterval = uiState.syncIntervalCalendars,
            onUpdateCalendarSyncInterval = model::updateCalendarSyncInterval,
            hasTasksSync = uiState.hasTasksSync,
            tasksSyncInterval = uiState.syncIntervalTasks,
            onUpdateTasksSyncInterval = model::updateTasksSyncInterval,
            syncOnlyOnWifi = uiState.syncWifiOnly,
            onUpdateSyncOnlyOnWifi = model::updateSyncWifiOnly,
            onlyOnSsids = uiState.syncWifiOnlySSIDs,
            onUpdateOnlyOnSsids = model::updateSyncWifiOnlySSIDs,
            ignoreVpns = uiState.ignoreVpns,
            onUpdateIgnoreVpns = model::updateIgnoreVpns,

            // Authentication Settings
            credentials = uiState.credentials,
            onUpdateCredentials = model::updateCredentials,
            onAuthenticateOAuth = {
                val request = model.newAuthorizationRequest()
                if (request != null)
                    authRequestContract.launch(request)
            },
            isCredentialsUpdateAllowed = uiState.allowCredentialsChange,

            // CalDav Settings
            timeRangePastDays = uiState.timeRangePastDays,
            onUpdateTimeRangePastDays = model::updateTimeRangePastDays,
            defaultAlarmMinBefore = uiState.defaultAlarmMinBefore,
            onUpdateDefaultAlarmMinBefore = model::updateDefaultAlarm,
            manageCalendarColors = uiState.manageCalendarColors,
            onUpdateManageCalendarColors = model::updateManageCalendarColors,
            eventColors = uiState.eventColors,
            onUpdateEventColors = model::updateEventColors,

            // CardDav Settings
            contactGroupMethod = uiState.contactGroupMethod,
            onUpdateContactGroupMethod = model::updateContactGroupMethod,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onNavUp: () -> Unit,
    accountName: String,
    status: String? = null,

    // Sync settings
    canAccessWifiSsid: Boolean,
    onSyncWifiOnlyPermissionsAction: () -> Unit,
    hasContactsSync: Boolean,
    contactsSyncInterval: Long?,
    onUpdateContactsSyncInterval: ((Long) -> Unit) = {},
    hasCalendarsSync: Boolean,
    calendarSyncInterval: Long?,
    onUpdateCalendarSyncInterval: ((Long) -> Unit) = {},
    hasTasksSync: Boolean,
    tasksSyncInterval: Long?,
    onUpdateTasksSyncInterval: ((Long) -> Unit) = {},
    syncOnlyOnWifi: Boolean,
    onUpdateSyncOnlyOnWifi: (Boolean) -> Unit = {},
    onlyOnSsids: List<String>?,
    onUpdateOnlyOnSsids: (List<String>) -> Unit = {},
    ignoreVpns: Boolean,
    onUpdateIgnoreVpns: (Boolean) -> Unit = {},

    // Authentication Settings
    credentials: Credentials?,
    onUpdateCredentials: (Credentials) -> Unit = {},
    onAuthenticateOAuth: () -> Unit = {},
    isCredentialsUpdateAllowed: Boolean,

    // CalDav Settings
    timeRangePastDays: Int?,
    onUpdateTimeRangePastDays: (Int?) -> Unit = {},
    defaultAlarmMinBefore: Int?,
    onUpdateDefaultAlarmMinBefore: (Int?) -> Unit = {},
    manageCalendarColors: Boolean,
    onUpdateManageCalendarColors: (Boolean) -> Unit = {},
    eventColors: Boolean,
    onUpdateEventColors: (Boolean) -> Unit = {},

    // CardDav Settings
    contactGroupMethod: GroupMethod,
    onUpdateContactGroupMethod: (GroupMethod) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        if (status != null)
            snackbarHostState.showSnackbar(status)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavUp) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                },
                title = { Text(accountName) },
                actions = {
                    IconButton(onClick = {
                        val settingsUri = Constants.MANUAL_URL.buildUpon()
                            .appendPath(Constants.MANUAL_PATH_SETTINGS)
                            .fragment(Constants.MANUAL_FRAGMENT_ACCOUNT_SETTINGS)
                            .build()
                        uriHandler.openUri(settingsUri.toString())
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            AccountSettings_FromModel(
                snackbarHostState = snackbarHostState,

                // Sync settings
                canAccessWifiSsid = canAccessWifiSsid,
                onSyncWifiOnlyPermissionsAction = onSyncWifiOnlyPermissionsAction,
                hasContactsSync = hasContactsSync,
                contactsSyncInterval = contactsSyncInterval,
                onUpdateContactsSyncInterval = onUpdateContactsSyncInterval,
                hasCalendarsSync = hasCalendarsSync,
                calendarSyncInterval = calendarSyncInterval,
                onUpdateCalendarSyncInterval = onUpdateCalendarSyncInterval,
                hasTasksSync = hasTasksSync,
                taskSyncInterval = tasksSyncInterval,
                onUpdateTaskSyncInterval = onUpdateTasksSyncInterval,
                syncOnlyOnWifi = syncOnlyOnWifi,
                onUpdateSyncOnlyOnWifi = onUpdateSyncOnlyOnWifi,
                onlyOnSsids = onlyOnSsids,
                onUpdateOnlyOnSsids = onUpdateOnlyOnSsids,
                ignoreVpns = ignoreVpns,
                onUpdateIgnoreVpns = onUpdateIgnoreVpns,

                // Authentication Settings
                credentials = credentials,
                onUpdateCredentials = onUpdateCredentials,
                onAuthenticateOAuth = onAuthenticateOAuth,
                isCredentialsUpdateAllowed = isCredentialsUpdateAllowed,

                // CalDav Settings
                timeRangePastDays = timeRangePastDays,
                onUpdateTimeRangePastDays = onUpdateTimeRangePastDays,
                defaultAlarmMinBefore = defaultAlarmMinBefore,
                onUpdateDefaultAlarmMinBefore = onUpdateDefaultAlarmMinBefore,
                manageCalendarColors = manageCalendarColors,
                onUpdateManageCalendarColors = onUpdateManageCalendarColors,
                eventColors = eventColors,
                onUpdateEventColors = onUpdateEventColors,

                // CardDav Settings
                contactGroupMethod = contactGroupMethod,
                onUpdateContactGroupMethod = onUpdateContactGroupMethod
            )
        }
    }
}

@Composable
fun AccountSettings_FromModel(
    snackbarHostState: SnackbarHostState,

    // Sync settings
    canAccessWifiSsid: Boolean,
    onSyncWifiOnlyPermissionsAction: () -> Unit,
    hasContactsSync: Boolean,
    contactsSyncInterval: Long?,
    onUpdateContactsSyncInterval: ((Long) -> Unit) = {},
    hasCalendarsSync: Boolean,
    calendarSyncInterval: Long?,
    onUpdateCalendarSyncInterval: ((Long) -> Unit) = {},
    hasTasksSync: Boolean,
    taskSyncInterval: Long?,
    onUpdateTaskSyncInterval: ((Long) -> Unit) = {},
    syncOnlyOnWifi: Boolean,
    onUpdateSyncOnlyOnWifi: (Boolean) -> Unit = {},
    onlyOnSsids: List<String>?,
    onUpdateOnlyOnSsids: (List<String>) -> Unit = {},
    ignoreVpns: Boolean,
    onUpdateIgnoreVpns: (Boolean) -> Unit = {},

    // Authentication Settings
    credentials: Credentials?,
    onUpdateCredentials: (Credentials) -> Unit = {},
    onAuthenticateOAuth: () -> Unit = {},
    isCredentialsUpdateAllowed: Boolean,

    // CalDav Settings
    timeRangePastDays: Int?,
    onUpdateTimeRangePastDays: (Int?) -> Unit = {},
    defaultAlarmMinBefore: Int?,
    onUpdateDefaultAlarmMinBefore: (Int?) -> Unit = {},
    manageCalendarColors: Boolean,
    onUpdateManageCalendarColors: (Boolean) -> Unit = {},
    eventColors: Boolean,
    onUpdateEventColors: (Boolean) -> Unit = {},

    // CardDav Settings
    contactGroupMethod: GroupMethod,
    onUpdateContactGroupMethod: (GroupMethod) -> Unit = {},
) {
    Column(Modifier.padding(8.dp)) {
        SyncSettings(
            canAccessWifiSsid = canAccessWifiSsid,
            onSyncWifiOnlyPermissionsAction = onSyncWifiOnlyPermissionsAction,
            hasContactsSync = hasContactsSync,
            contactsSyncInterval = contactsSyncInterval,
            onUpdateContactsSyncInterval = onUpdateContactsSyncInterval,
            hasCalendarsSync = hasCalendarsSync,
            calendarSyncInterval = calendarSyncInterval,
            onUpdateCalendarSyncInterval = onUpdateCalendarSyncInterval,
            hasTasksSync = hasTasksSync,
            taskSyncInterval = taskSyncInterval,
            onUpdateTaskSyncInterval = onUpdateTaskSyncInterval,
            syncOnlyOnWifi = syncOnlyOnWifi,
            onUpdateSyncOnlyOnWifi = onUpdateSyncOnlyOnWifi,
            onlyOnSsids = onlyOnSsids,
            onUpdateOnlyOnSsids = onUpdateOnlyOnSsids,
            ignoreVpns = ignoreVpns,
            onUpdateIgnoreVpns = onUpdateIgnoreVpns
        )

        credentials?.let {
            AuthenticationSettings(
                snackbarHostState = snackbarHostState,
                credentials = credentials,
                isEnabled = isCredentialsUpdateAllowed,
                onUpdateCredentials = onUpdateCredentials,
                onAuthenticateOAuth = onAuthenticateOAuth
            )
        }

        CalDavSettings(
            timeRangePastDays = timeRangePastDays,
            onUpdateTimeRangePastDays = onUpdateTimeRangePastDays,
            defaultAlarmMinBefore = defaultAlarmMinBefore,
            onUpdateDefaultAlarmMinBefore = onUpdateDefaultAlarmMinBefore,
            manageCalendarColors = manageCalendarColors,
            onUpdateManageCalendarColors = onUpdateManageCalendarColors,
            eventColors = eventColors,
            onUpdateEventColors = onUpdateEventColors,
        )

        CardDavSettings(
            contactGroupMethod = contactGroupMethod,
            onUpdateContactGroupMethod = onUpdateContactGroupMethod
        )
    }
}

@Composable
fun SyncSettings(
    canAccessWifiSsid: Boolean,
    onSyncWifiOnlyPermissionsAction: () -> Unit,
    hasContactsSync: Boolean,
    contactsSyncInterval: Long?,
    onUpdateContactsSyncInterval: ((Long) -> Unit) = {},
    hasCalendarsSync: Boolean,
    calendarSyncInterval: Long?,
    onUpdateCalendarSyncInterval: ((Long) -> Unit) = {},
    hasTasksSync: Boolean,
    taskSyncInterval: Long?,
    onUpdateTaskSyncInterval: ((Long) -> Unit) = {},
    syncOnlyOnWifi: Boolean,
    onUpdateSyncOnlyOnWifi: (Boolean) -> Unit = {},
    onlyOnSsids: List<String>?,
    onUpdateOnlyOnSsids: (List<String>) -> Unit = {},
    ignoreVpns: Boolean,
    onUpdateIgnoreVpns: (Boolean) -> Unit = {}
) {
    Column {
        SettingsHeader(false) {
            Text(stringResource(R.string.settings_sync))
        }

        if (hasContactsSync)
            SyncIntervalSetting(
                icon = Icons.Default.Contacts,
                name = R.string.settings_sync_interval_contacts,
                syncInterval = contactsSyncInterval,
                onUpdateSyncInterval = onUpdateContactsSyncInterval
            )
        if (hasCalendarsSync)
            SyncIntervalSetting(
                icon = Icons.Default.Event,
                name = R.string.settings_sync_interval_calendars,
                syncInterval = calendarSyncInterval,
                onUpdateSyncInterval = onUpdateCalendarSyncInterval
            )
        if (hasTasksSync)
            SyncIntervalSetting(
                icon = Icons.Outlined.Task,
                name = R.string.settings_sync_interval_tasks,
                syncInterval = taskSyncInterval,
                onUpdateSyncInterval = onUpdateTaskSyncInterval
            )

        SwitchSetting(
            icon = Icons.Default.Wifi,
            name = stringResource(R.string.settings_sync_wifi_only),
            summaryOn = stringResource(R.string.settings_sync_wifi_only_on),
            summaryOff = stringResource(R.string.settings_sync_wifi_only_off),
            checked = syncOnlyOnWifi,
            onCheckedChange = onUpdateSyncOnlyOnWifi
        )

        var showWifiOnlySsidsDialog by remember { mutableStateOf(false) }
        Setting(
            icon = null,
            name = stringResource(R.string.settings_sync_wifi_only_ssids),
            enabled = syncOnlyOnWifi,
            summary =
            if (onlyOnSsids != null)
                stringResource(R.string.settings_sync_wifi_only_ssids_on, onlyOnSsids.joinToString(", "))
            else
                stringResource(R.string.settings_sync_wifi_only_ssids_off),
            onClick = {
                showWifiOnlySsidsDialog = true
            }
        )
        if (showWifiOnlySsidsDialog)
            EditTextInputDialog(
                title = stringResource(R.string.settings_sync_wifi_only_ssids_message),
                initialValue = onlyOnSsids?.joinToString(", ") ?: "",
                onValueEntered = { newValue ->
                    val newSsids = newValue.split(',')
                        .map { it.trim() }
                        .distinct()
                    onUpdateOnlyOnSsids(newSsids)
                    showWifiOnlySsidsDialog = false
                },
                onDismiss = { showWifiOnlySsidsDialog = false }
            )

        if (LocalInspectionMode.current || onlyOnSsids != null)
            ActionCard(
                icon = if (!canAccessWifiSsid) Icons.Default.SyncProblem else Icons.Default.Info,
                actionText = stringResource(R.string.settings_sync_wifi_only_ssids_permissions_action),
                onAction = onSyncWifiOnlyPermissionsAction
            ) {
                Column {
                    if (!canAccessWifiSsid)
                        Text(stringResource(R.string.settings_sync_wifi_only_ssids_permissions_required))
                    Text(
                        stringResource(
                            R.string.wifi_permissions_background_location_disclaimer, stringResource(
                                R.string.app_name)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

        SwitchSetting(
            icon = null,
            name = stringResource(R.string.settings_ignore_vpns),
            summaryOn = stringResource(R.string.settings_ignore_vpns_on),
            summaryOff = stringResource(R.string.settings_ignore_vpns_off),
            checked = ignoreVpns,
            onCheckedChange = onUpdateIgnoreVpns
        )
    }
}

@Composable
fun SyncIntervalSetting(
    icon: ImageVector,
    @StringRes name: Int,
    syncInterval: Long?,
    onUpdateSyncInterval: (Long) -> Unit
) {
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    Setting(
        icon = icon,
        name = stringResource(name),
        summary =
        if (syncInterval == null)
            stringResource(R.string.settings_sync_summary_manually)
        else
            stringResource(R.string.settings_sync_summary_periodically, syncInterval / 60),
        onClick = {
            showSyncIntervalDialog = true
        }
    )
    if (showSyncIntervalDialog) {
        val syncIntervalNames = stringArrayResource(R.array.settings_sync_interval_names)
        val syncIntervalSeconds = stringArrayResource(R.array.settings_sync_interval_seconds)
        MultipleChoiceInputDialog(
            title = stringResource(name),
            namesAndValues = syncIntervalNames.zip(syncIntervalSeconds),
            initialValue = syncInterval.toString(),
            onValueSelected = { newValue ->
                try {
                    val seconds = newValue.toLong()
                    onUpdateSyncInterval(seconds)
                } catch (_: NumberFormatException) {
                }
                showSyncIntervalDialog = false
            },
            onDismiss = {
                showSyncIntervalDialog = false
            }
        )
    }
}

@Composable
fun AuthenticationSettings(
    credentials: Credentials,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    isEnabled: Boolean = true,
    onUpdateCredentials: (Credentials) -> Unit = {},
    onAuthenticateOAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (credentials.authState != null || credentials.username != null || credentials.password != null || credentials.certificateAlias != null)
        Column {
            SettingsHeader(false) {
                Text(stringResource(R.string.settings_authentication))
            }

            // username/password
            if (credentials.username != null || credentials.password != null) {
                var showUsernameDialog by remember { mutableStateOf(false) }
                Setting(
                    icon = Icons.Default.AccountCircle,
                    name = stringResource(R.string.settings_username),
                    summary = credentials.username,
                    enabled = isEnabled,
                    onClick = {
                        showUsernameDialog = true
                    }
                )
                if (showUsernameDialog)
                    EditTextInputDialog(
                        title = stringResource(R.string.settings_username),
                        initialValue = credentials.username,
                        onValueEntered = { newValue ->
                            onUpdateCredentials(credentials.copy(username = newValue))
                        },
                        onDismiss = { showUsernameDialog = false }
                    )

                var showPasswordDialog by remember { mutableStateOf(false) }
                Setting(
                    icon = Icons.Default.Password,
                    name = stringResource(R.string.settings_password),
                    summary = stringResource(R.string.settings_password_summary),
                    enabled = isEnabled,
                    onClick = {
                        showPasswordDialog = true
                    }
                )
                if (showPasswordDialog)
                    EditTextInputDialog(
                        title = stringResource(R.string.settings_password),
                        inputLabel = stringResource(R.string.settings_new_password),
                        initialValue = null, // Do not show the existing password
                        passwordField = true,
                        onValueEntered = { newValue ->
                            onUpdateCredentials(credentials.copy(password = newValue.toCharArray()))
                        },
                        onDismiss = { showPasswordDialog = false }
                    )
            }

            // OAuth
            if (credentials.authState != null) {
                Setting(
                    icon = Icons.Default.Password,
                    name = stringResource(R.string.settings_reauthorize_oauth),
                    summary = stringResource(R.string.settings_reauthorize_oauth_summary),
                    enabled = isEnabled,
                    onClick = onAuthenticateOAuth
                )
            }

            // client certificate
            Setting(
                icon = null,
                name = stringResource(R.string.settings_certificate_alias),
                summary = credentials.certificateAlias ?: stringResource(R.string.settings_certificate_alias_empty),
                enabled = isEnabled,
                onClick = {
                    val activity = context as Activity
                    KeyChain.choosePrivateKeyAlias(activity, { newAlias ->
                        if (newAlias != null)
                            onUpdateCredentials(credentials.copy(certificateAlias = newAlias))
                        else
                            scope.launch {
                                if (snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_certificate_alias_empty),
                                        actionLabel = context.getString(R.string.settings_certificate_install)
                                    ) == SnackbarResult.ActionPerformed) {
                                    val intent = KeyChain.createInstallIntent()
                                    if (intent.resolveActivity(context.packageManager) != null)
                                        context.startActivity(intent)
                                }
                            }
                    }, null, null, null, -1, credentials.certificateAlias)
                }
            )
    }
}

@Composable
fun CalDavSettings(
    timeRangePastDays: Int?,
    onUpdateTimeRangePastDays: (Int?) -> Unit = {},
    defaultAlarmMinBefore: Int?,
    onUpdateDefaultAlarmMinBefore: (Int?) -> Unit = {},
    manageCalendarColors: Boolean,
    onUpdateManageCalendarColors: (Boolean) -> Unit = {},
    eventColors: Boolean,
    onUpdateEventColors: (Boolean) -> Unit = {}
) {
    Column {
        SettingsHeader {
            Text(stringResource(R.string.settings_caldav))
        }

        var showTimeRangePastDialog by remember { mutableStateOf(false) }
        Setting(
            icon = Icons.Default.History,
            name = stringResource(R.string.settings_sync_time_range_past),
            summary =
            if (timeRangePastDays != null)
                pluralStringResource(R.plurals.settings_sync_time_range_past_days, timeRangePastDays, timeRangePastDays)
            else
                stringResource(R.string.settings_sync_time_range_past_none),
            onClick = {
                showTimeRangePastDialog = true
            }
        )
        if (showTimeRangePastDialog)
            EditTextInputDialog(
                title = stringResource(R.string.settings_sync_time_range_past_message),
                initialValue = timeRangePastDays?.toString() ?: "",
                onValueEntered = { newValue ->
                    val days = try {
                        newValue.toInt()
                    } catch (_: NumberFormatException) {
                        null
                    }
                    onUpdateTimeRangePastDays(days)
                    showTimeRangePastDialog = false
                },
                onDismiss = { showTimeRangePastDialog = false }
            )

        var showDefaultAlarmDialog by remember { mutableStateOf(false) }
        Setting(
            icon = null,
            name = stringResource(R.string.settings_default_alarm),
            summary =
            if (defaultAlarmMinBefore != null)
                pluralStringResource(R.plurals.settings_default_alarm_on, defaultAlarmMinBefore, defaultAlarmMinBefore)
            else
                stringResource(R.string.settings_default_alarm_off),
            onClick = {
                showDefaultAlarmDialog = true
            }
        )
        if (showDefaultAlarmDialog)
            EditTextInputDialog(
                title = stringResource(R.string.settings_default_alarm_message),
                initialValue = defaultAlarmMinBefore?.toString() ?: "",
                onValueEntered = { newValue ->
                    val minBefore = try {
                        newValue.toInt()
                    } catch (_: NumberFormatException) {
                        null
                    }
                    onUpdateDefaultAlarmMinBefore(minBefore)
                    showDefaultAlarmDialog = false
                },
                onDismiss = { showDefaultAlarmDialog = false }
            )

        SwitchSetting(
            icon = null,
            name = stringResource(R.string.settings_manage_calendar_colors),
            summaryOn = stringResource(R.string.settings_manage_calendar_colors_on),
            summaryOff = stringResource(R.string.settings_manage_calendar_colors_off),
            checked = manageCalendarColors,
            onCheckedChange = onUpdateManageCalendarColors
        )

        SwitchSetting(
            icon = null,
            name = stringResource(R.string.settings_event_colors),
            summaryOn = stringResource(R.string.settings_event_colors_on),
            summaryOff = stringResource(R.string.settings_event_colors_off),
            checked = eventColors,
            onCheckedChange = onUpdateEventColors
        )
    }
}

@Composable
fun CardDavSettings(
    contactGroupMethod: GroupMethod,
    onUpdateContactGroupMethod: (GroupMethod) -> Unit = {}
) {
    Column {
        SettingsHeader {
            Text(stringResource(R.string.settings_carddav))
        }

        val groupMethodNames = stringArrayResource(R.array.settings_contact_group_method_entries)
        val groupMethodValues = stringArrayResource(R.array.settings_contact_group_method_values)
        var showGroupMethodDialog by remember { mutableStateOf(false) }
        Setting(
            icon = Icons.Default.Contacts,
            name = stringResource(R.string.settings_contact_group_method),
            summary = groupMethodNames[groupMethodValues.indexOf(contactGroupMethod.name)],
            onClick = {
                showGroupMethodDialog = true
            }
        )
        if (showGroupMethodDialog)
            MultipleChoiceInputDialog(
                title = stringResource(R.string.settings_contact_group_method),
                namesAndValues = groupMethodNames.zip(groupMethodValues),
                initialValue = contactGroupMethod.name,
                onValueSelected = { newValue ->
                    onUpdateContactGroupMethod(GroupMethod.valueOf(newValue))
                    showGroupMethodDialog = false
                },
                onDismiss = { showGroupMethodDialog = false }
            )
    }
}

@Composable
@Preview
fun AccountSettingsScreen_Preview() {
    AppTheme {
        AccountSettingsScreen(
            accountName = "Account Name Here",
            onNavUp = {},
            status = "Some Status",

            // Sync settings
            canAccessWifiSsid = true,
            onSyncWifiOnlyPermissionsAction = {},
            hasContactsSync = true,
            contactsSyncInterval = 80000L,
            onUpdateContactsSyncInterval = {},
            hasCalendarsSync = true,
            calendarSyncInterval = 50000L,
            onUpdateCalendarSyncInterval = {},
            hasTasksSync = true,
            tasksSyncInterval = 900000L,
            onUpdateTasksSyncInterval = {},
            syncOnlyOnWifi = true,
            onUpdateSyncOnlyOnWifi = {},
            onlyOnSsids = listOf("HeyWifi", "Another"),
            onUpdateOnlyOnSsids = {},
            ignoreVpns = true,
            onUpdateIgnoreVpns = {},

            // Authentication Settings
            credentials = Credentials(username = "test", password = "test".toCharArray()),
            onUpdateCredentials = {},
            isCredentialsUpdateAllowed = true,

            // CalDav Settings
            timeRangePastDays = 365,
            onUpdateTimeRangePastDays = {},
            defaultAlarmMinBefore = 585,
            onUpdateDefaultAlarmMinBefore = {},
            manageCalendarColors = false,
            onUpdateManageCalendarColors = {},
            eventColors = false,
            onUpdateEventColors = {},

            // CardDav Settings
            contactGroupMethod = GroupMethod.GROUP_VCARDS,
            onUpdateContactGroupMethod = {}
        )
    }
}