/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.security.KeyChain
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Task
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.syncadapter.Syncer
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.ActionCard
import at.bitfire.davdroid.ui.composable.EditTextInputDialog
import at.bitfire.davdroid.ui.composable.MultipleChoiceInputDialog
import at.bitfire.davdroid.ui.composable.Setting
import at.bitfire.davdroid.ui.composable.SettingsHeader
import at.bitfire.davdroid.ui.composable.SwitchSetting
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import javax.inject.Inject

@AndroidEntryPoint
class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"

        const val ACCOUNT_SETTINGS_HELP_URL = "https://manual.davx5.com/settings.html#account-settings"
    }

    private val account by lazy {
        intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = account.name

        setContent {
            AppTheme {
                val uriHandler = LocalUriHandler.current

                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onSupportNavigateUp() }) {
                                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                                }
                            },
                            title = { Text(account.name) },
                            actions = {
                                IconButton(onClick = {
                                    uriHandler.openUri(ACCOUNT_SETTINGS_HELP_URL)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    Box(Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())) {
                        AccountSettings_FromModel(
                            snackbarHostState = snackbarHostState,
                            model = model
                        )
                    }
                }
            }
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }


    @Composable
    fun AccountSettings_FromModel(
        snackbarHostState: SnackbarHostState,
        model: Model
    ) {
        Column(Modifier.padding(8.dp)) {
            SyncSettings(
                contactsSyncInterval = model.syncIntervalContacts.observeAsState().value,
                onUpdateContactsSyncInterval = { model.updateSyncInterval(getString(R.string.address_books_authority), it) },
                calendarSyncInterval = model.syncIntervalCalendars.observeAsState().value,
                onUpdateCalendarSyncInterval = { model.updateSyncInterval(CalendarContract.AUTHORITY, it) },
                taskSyncInterval = model.syncIntervalTasks.observeAsState().value,
                onUpdateTaskSyncInterval = { interval ->
                    model.tasksProvider?.let { model.updateSyncInterval(it.authority, interval) }
                },
                syncOnlyOnWifi = model.syncWifiOnly.observeAsState(false).value,
                onUpdateSyncOnlyOnWifi = { model.updateSyncWifiOnly(it) },
                onlyOnSsids = model.syncWifiOnlySSIDs.observeAsState().value,
                onUpdateOnlyOnSsids = { model.updateSyncWifiOnlySSIDs(it) },
                ignoreVpns = model.ignoreVpns.observeAsState(false).value,
                onUpdateIgnoreVpns = { model.updateIgnoreVpns(it) }
            )

            val credentials by model.credentials.observeAsState()
            credentials?.let {
                AuthenticationSettings(
                    snackbarHostState = snackbarHostState,
                    credentials = it,
                    onUpdateCredentials = { model.updateCredentials(it) }
                )
            }

            CalDavSettings(
                timeRangePastDays = model.timeRangePastDays.observeAsState().value,
                onUpdateTimeRangePastDays = { model.updateTimeRangePastDays(it) },
                defaultAlarmMinBefore = model.defaultAlarmMinBefore.observeAsState().value,
                onUpdateDefaultAlarmMinBefore = { model.updateDefaultAlarm(it) },
                manageCalendarColors = model.manageCalendarColors.observeAsState().value ?: false,
                onUpdateManageCalendarColors = { model.updateManageCalendarColors(it) },
                eventColors = model.eventColors.observeAsState().value ?: false,
                onUpdateEventColors = { model.updateEventColors(it) }
            )

            CardDavSettings(
                contactGroupMethod = model.contactGroupMethod.observeAsState(GroupMethod.GROUP_VCARDS).value,
                onUpdateContactGroupMethod = { model.updateContactGroupMethod(it) }
            )
        }
    }

    @Composable
    fun SyncSettings(
        contactsSyncInterval: Long?,
        onUpdateContactsSyncInterval: ((Long) -> Unit) = {},
        calendarSyncInterval: Long?,
        onUpdateCalendarSyncInterval: ((Long) -> Unit) = {},
        taskSyncInterval: Long?,
        onUpdateTaskSyncInterval: ((Long) -> Unit) = {},
        syncOnlyOnWifi: Boolean,
        onUpdateSyncOnlyOnWifi: (Boolean) -> Unit = {},
        onlyOnSsids: List<String>?,
        onUpdateOnlyOnSsids: (List<String>) -> Unit = {},
        ignoreVpns: Boolean,
        onUpdateIgnoreVpns: (Boolean) -> Unit = {}
    ) {
        val context = LocalContext.current

        Column {
            SettingsHeader(false) {
                Text(stringResource(R.string.settings_sync))
            }

            if (contactsSyncInterval != null)
                SyncIntervalSetting(
                    icon = Icons.Default.Contacts,
                    name = R.string.settings_sync_interval_contacts,
                    syncInterval = contactsSyncInterval,
                    onUpdateSyncInterval = onUpdateContactsSyncInterval
                )
            if (calendarSyncInterval != null)
                SyncIntervalSetting(
                    icon = Icons.Default.Event,
                    name = R.string.settings_sync_interval_calendars,
                    syncInterval = calendarSyncInterval,
                    onUpdateSyncInterval = onUpdateCalendarSyncInterval
                )
            if (taskSyncInterval != null)
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

            // TODO make canAccessWifiSsid live-capable
            val canAccessWifiSsid =
                if (LocalInspectionMode.current)
                    false
                else
                    PermissionUtils.canAccessWifiSsid(context)
            if (onlyOnSsids != null && !canAccessWifiSsid)
                ActionCard(
                    icon = Icons.Default.SyncProblem,
                    actionText = stringResource(R.string.settings_sync_wifi_only_ssids_permissions_action),
                    onAction = {
                        val intent = Intent(context, WifiPermissionsActivity::class.java)
                        intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, account)
                        startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.settings_sync_wifi_only_ssids_permissions_required))
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
        syncInterval: Long,
        onUpdateSyncInterval: (Long) -> Unit
    ) {
        var showSyncIntervalDialog by remember { mutableStateOf(false) }
        Setting(
            icon = icon,
            name = stringResource(name),
            summary =
                if (syncInterval == AccountSettings.SYNC_INTERVAL_MANUALLY)
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
    @Preview
    fun SyncSettings_Preview() {
        SyncSettings(
            contactsSyncInterval = 60*60,
            calendarSyncInterval = 4*60*60,
            taskSyncInterval = 2*60*60,
            syncOnlyOnWifi = false,
            onlyOnSsids = listOf("SSID1", "SSID2"),
            ignoreVpns = true
        )
    }

    @Composable
    fun AuthenticationSettings(
        credentials: Credentials,
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
        onUpdateCredentials: (Credentials) -> Unit = {}
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        Column(Modifier.padding(8.dp)) {
            SettingsHeader(false) {
                Text(stringResource(R.string.settings_authentication))
            }

            if (credentials.authState != null) {       // OAuth
                Setting(
                    icon = Icons.Default.Password,
                    name = stringResource(R.string.settings_oauth),
                    summary = stringResource(R.string.settings_oauth_summary),
                    onClick = {
                        // GoogleLoginFragment replacement
                    }
                )

            } else { // username/password
                if (credentials.username != null) {
                    var showUsernameDialog by remember { mutableStateOf(false) }
                    Setting(
                        icon = Icons.Default.AccountCircle,
                        name = stringResource(R.string.settings_username),
                        summary = credentials.username,
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
                }

                if (credentials.password != null) {
                    var showPasswordDialog by remember { mutableStateOf(false) }
                    Setting(
                        icon = Icons.Default.Password,
                        name = stringResource(R.string.settings_password),
                        summary = stringResource(R.string.settings_password_summary),
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
                                onUpdateCredentials(credentials.copy(password = newValue))
                            },
                            onDismiss = { showPasswordDialog = false }
                        )
                }

                // client certificate
                Setting(
                    icon = null,
                    name = stringResource(R.string.settings_certificate_alias),
                    summary = credentials.certificateAlias ?: stringResource(R.string.settings_certificate_alias_empty),
                    onClick = {
                        val activity = context as Activity
                        KeyChain.choosePrivateKeyAlias(activity, { newAlias ->
                            if (newAlias != null)
                                onUpdateCredentials(credentials.copy(certificateAlias = newAlias))
                            else
                                scope.launch {
                                    if (snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_certificate_alias_empty),
                                        actionLabel = context.getString(R.string.settings_certificate_install).uppercase()
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
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_ClientCertificate() {
        AuthenticationSettings(
            credentials = Credentials(certificateAlias = "alias")
        )
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_OAuth() {
        AuthenticationSettings(
            credentials = Credentials(authState = AuthState())
        )
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_UsernamePassword() {
        AuthenticationSettings(
            credentials = Credentials(username = "user", password = "password")
        )
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_UsernamePassword_ClientCertificate() {
        AuthenticationSettings(
            credentials = Credentials(username = "user", password = "password", certificateAlias = "alias")
        )
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
    @Preview
    fun CalDavSettings_Preview() {
        CalDavSettings(
            timeRangePastDays = 30,
            defaultAlarmMinBefore = 10,
            manageCalendarColors = true,
            eventColors = true
        )
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
    fun CardDavSettings_Preview() {
        CardDavSettings(
            contactGroupMethod = GroupMethod.GROUP_VCARDS
        )
    }


    class Model @AssistedInject constructor(
        val context: Application,
        val settings: SettingsManager,
        @Assisted val account: Account
    ): ViewModel(), SettingsManager.OnChangeListener {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        private var accountSettings: AccountSettings? = null

        // settings
        val syncIntervalContacts = MutableLiveData<Long>()
        val syncIntervalCalendars = MutableLiveData<Long>()

        val tasksProvider = TaskUtils.currentProvider(context)
        val syncIntervalTasks = MutableLiveData<Long>()

        val syncWifiOnly = MutableLiveData<Boolean>()
        val syncWifiOnlySSIDs = MutableLiveData<List<String>>()
        val ignoreVpns = MutableLiveData<Boolean>()

        val credentials = MutableLiveData<Credentials>()

        val timeRangePastDays = MutableLiveData<Int>()
        val defaultAlarmMinBefore = MutableLiveData<Int>()
        val manageCalendarColors = MutableLiveData<Boolean>()
        val eventColors = MutableLiveData<Boolean>()

        val contactGroupMethod = MutableLiveData<GroupMethod>()


        init {
            accountSettings = AccountSettings(context, account)

            settings.addOnChangeListener(this)

            reload()
        }

        override fun onCleared() {
            super.onCleared()
            settings.removeOnChangeListener(this)
        }

        override fun onSettingsChanged() {
            Logger.log.info("Settings changed")
            reload()
        }

        fun reload() {
            val accountSettings = accountSettings ?: return

            syncIntervalContacts.postValue(
                accountSettings.getSyncInterval(context.getString(R.string.address_books_authority))
            )
            syncIntervalCalendars.postValue(accountSettings.getSyncInterval(CalendarContract.AUTHORITY))
            syncIntervalTasks.postValue(tasksProvider?.let { accountSettings.getSyncInterval(it.authority) })

            syncWifiOnly.postValue(accountSettings.getSyncWifiOnly())
            syncWifiOnlySSIDs.postValue(accountSettings.getSyncWifiOnlySSIDs())
            ignoreVpns.postValue(accountSettings.getIgnoreVpns())

            credentials.postValue(accountSettings.credentials())

            timeRangePastDays.postValue(accountSettings.getTimeRangePastDays())
            defaultAlarmMinBefore.postValue(accountSettings.getDefaultAlarm())
            manageCalendarColors.postValue(accountSettings.getManageCalendarColors())
            eventColors.postValue(accountSettings.getEventColors())

            contactGroupMethod.postValue(accountSettings.getGroupMethod())
        }


        fun updateSyncInterval(authority: String, syncInterval: Long) {
            CoroutineScope(Dispatchers.Default).launch {
                accountSettings?.setSyncInterval(authority, syncInterval)
                reload()
            }
        }

        fun updateSyncWifiOnly(wifiOnly: Boolean) {
            accountSettings?.setSyncWiFiOnly(wifiOnly)
            reload()
        }

        fun updateSyncWifiOnlySSIDs(ssids: List<String>?) {
            accountSettings?.setSyncWifiOnlySSIDs(ssids)
            reload()
        }

        fun updateIgnoreVpns(ignoreVpns: Boolean) {
            accountSettings?.setIgnoreVpns(ignoreVpns)
            reload()
        }

        fun updateCredentials(credentials: Credentials) {
            accountSettings?.credentials(credentials)
            reload()
        }

        fun updateTimeRangePastDays(days: Int?) {
            accountSettings?.setTimeRangePastDays(days)
            reload()

            /* If the new setting is a certain number of days, no full resync is required,
            because every sync will cause a REPORT calendar-query with the given number of days.
            However, if the new setting is "all events", collection sync may/should be used, so
            the last sync-token has to be reset, which is done by setting fullResync=true.
             */
            resyncCalendars(fullResync = days == null, tasks = false)
        }

        fun updateDefaultAlarm(minBefore: Int?) {
            accountSettings?.setDefaultAlarm(minBefore)
            reload()

            resyncCalendars(fullResync = true, tasks = false)
        }

        fun updateManageCalendarColors(manage: Boolean) {
            accountSettings?.setManageCalendarColors(manage)
            reload()

            resyncCalendars(fullResync = false, tasks = true)
        }

        fun updateEventColors(manageColors: Boolean) {
            accountSettings?.setEventColors(manageColors)
            reload()

            resyncCalendars(fullResync = true, tasks = false)
        }

        fun updateContactGroupMethod(groupMethod: GroupMethod) {
            accountSettings?.setGroupMethod(groupMethod)
            reload()

            resync(
                authority = context.getString(R.string.address_books_authority),
                fullResync = true
            )
        }

        /**
         * Initiates calendar re-synchronization.
         *
         * @param fullResync whether sync shall download all events again
         * (_true_: sets [Syncer.SYNC_EXTRAS_FULL_RESYNC],
         * _false_: sets [Syncer.SYNC_EXTRAS_RESYNC])
         * @param tasks whether tasks shall be synchronized, too (false: only events, true: events and tasks)
         */
        private fun resyncCalendars(fullResync: Boolean, tasks: Boolean) {
            resync(CalendarContract.AUTHORITY, fullResync)
            if (tasks)
                resync(TaskProvider.ProviderName.OpenTasks.authority, fullResync)
        }

        /**
         * Initiates re-synchronization for given authority.
         *
         * @param authority authority to re-sync
         * @param fullResync whether sync shall download all events again
         * (_true_: sets [Syncer.SYNC_EXTRAS_FULL_RESYNC],
         * _false_: sets [Syncer.SYNC_EXTRAS_RESYNC])
         */
        private fun resync(authority: String, fullResync: Boolean) {
            val resync =
                if (fullResync)
                    OneTimeSyncWorker.FULL_RESYNC
                else
                    OneTimeSyncWorker.RESYNC
            OneTimeSyncWorker.enqueue(context, account, authority, resync = resync)
        }

    }

}