/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Task
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.syncadapter.Syncer
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.widget.EditTextInputDialog
import at.bitfire.davdroid.ui.widget.MultipleChoiceInputDialog
import at.bitfire.davdroid.ui.widget.Setting
import at.bitfire.davdroid.ui.widget.SettingsHeader
import at.bitfire.davdroid.ui.widget.SwitchSetting
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onNavigateUp() }) {
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
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        AccountSettings_FromModel(model)
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
    fun AccountSettings_FromModel(model: Model) {
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
        Column(Modifier.padding(8.dp)) {
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
                    }
                )

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
            summary = stringResource(R.string.settings_sync_summary_periodically, syncInterval / 60),
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


    /*@AndroidEntryPoint
    class AccountSettingsFragment : PreferenceFragmentCompat() {

        private val account by lazy { requireArguments().getParcelable<Account>(EXTRA_ACCOUNT)!! }
        @Inject lateinit var settings: SettingsManager


        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings_account)

            findPreference<EditTextPreference>(getString(R.string.settings_password_key))!!.setOnBindEditTextListener { password ->
                password.inputType = InputType.TYPE_CLASS_TEXT.or(InputType.TYPE_TEXT_VARIATION_PASSWORD)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            try {
                initSettings()
            } catch (e: InvalidAccountException) {
                Toast.makeText(context, R.string.account_invalid, Toast.LENGTH_LONG).show()
                requireActivity().finish()
            }
        }

        override fun onResume() {
            super.onResume()
            checkWifiPermissions()
        }

        private fun initSettings() {
            findPreference<EditTextPreference>(getString(R.string.settings_sync_wifi_only_ssids_key))!!.let {
                model.syncWifiOnly.observe(viewLifecycleOwner) { wifiOnly ->
                    it.isEnabled = wifiOnly && settings.isWritable(AccountSettings.KEY_WIFI_ONLY_SSIDS)
                }
                model.syncWifiOnlySSIDs.observe(viewLifecycleOwner) { onlySSIDs ->
                    checkWifiPermissions()

                    if (onlySSIDs != null) {
                        it.text = onlySSIDs.joinToString(", ")
                        it.summary = getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                R.string.settings_sync_wifi_only_ssids_on_location_services
                                else R.string.settings_sync_wifi_only_ssids_on, onlySSIDs.joinToString(", "))
                    } else {
                        it.text = ""
                        it.setSummary(R.string.settings_sync_wifi_only_ssids_off)
                    }
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        val newOnlySSIDs = (newValue as String)
                                .split(',')
                                .mapNotNull { StringUtils.trimToNull(it) }
                                .distinct()
                        model.updateSyncWifiOnlySSIDs(newOnlySSIDs)
                        false
                    }
                }
            }

            findPreference<SwitchPreferenceCompat>(getString(R.string.settings_ignore_vpns_key))!!.let {
                model.ignoreVpns.observe(viewLifecycleOwner) { ignoreVpns ->
                    it.isEnabled = true
                    it.isChecked = ignoreVpns
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, prefValue ->
                        model.updateIgnoreVpns(prefValue as Boolean)
                        false
                    }
                }
            }

            // preference group: authentication
            val prefUserName = findPreference<EditTextPreference>(getString(R.string.settings_username_key))!!
            val prefPassword = findPreference<EditTextPreference>(getString(R.string.settings_password_key))!!
            val prefCertAlias = findPreference<Preference>(getString(R.string.settings_certificate_alias_key))!!
            val prefOAuth = findPreference<Preference>(getString(R.string.settings_oauth_key))!!

            model.credentials.observe(viewLifecycleOwner) { credentials ->
                if (credentials.authState != null) {
                    // using OAuth, hide other settings
                    prefOAuth.isVisible = true
                    prefUserName.isVisible = false
                    prefPassword.isVisible = false
                    prefCertAlias.isVisible = false

                    prefOAuth.setOnPreferenceClickListener {
                        parentFragmentManager.beginTransaction()
                            .replace(android.R.id.content, GoogleLoginFragment(account.name), null)
                            .addToBackStack(null)
                            .commit()
                        true
                    }
                } else {
                    // not using OAuth, hide OAuth setting, show the others
                    prefOAuth.isVisible = false
                    prefUserName.isVisible = true
                    prefPassword.isVisible = true
                    prefCertAlias.isVisible = true

                    prefUserName.summary = credentials.userName
                    prefUserName.text = credentials.userName
                    prefUserName.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newUserName ->
                        val newUserNameOrNull = StringUtils.trimToNull(newUserName as String)
                        model.updateCredentials(Credentials(
                            userName = newUserNameOrNull,
                            password = credentials.password,
                            certificateAlias = credentials.certificateAlias)
                        )
                        false
                    }

                    if (credentials.userName != null) {
                        prefPassword.isVisible = true
                        prefPassword.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newPassword ->
                            model.updateCredentials(Credentials(credentials.userName, newPassword as String, credentials.certificateAlias))
                            false
                        }
                    } else
                        prefPassword.isVisible = false

                    prefCertAlias.summary = credentials.certificateAlias ?: getString(R.string.settings_certificate_alias_empty)
                    prefCertAlias.setOnPreferenceClickListener {
                        KeyChain.choosePrivateKeyAlias(requireActivity(), { newAlias ->
                            model.updateCredentials(Credentials(credentials.userName, credentials.password, newAlias))
                        }, null, null, null, -1, credentials.certificateAlias)
                        true
                    }
                }
            }

            // preference group: CalDAV
            model.hasCalDav.observe(viewLifecycleOwner) { hasCalDav ->
                if (!hasCalDav)
                    findPreference<PreferenceGroup>(getString(R.string.settings_caldav_key))!!.isVisible = false
                else {
                    findPreference<PreferenceGroup>(getString(R.string.settings_caldav_key))!!.isVisible = true

                    // when model.hasCalDav is available, model.syncInterval* are also available
                    // (because hasCalDav is calculated from them)
                    val hasCalendars = model.syncIntervalCalendars.value != null

                    findPreference<EditTextPreference>(getString(R.string.settings_sync_time_range_past_key))!!.let { pref ->
                        if (hasCalendars)
                            model.timeRangePastDays.observe(viewLifecycleOwner) { pastDays ->
                                if (model.syncIntervalCalendars.value != null) {
                                    pref.isVisible = true
                                    if (pastDays != null) {
                                        pref.text = pastDays.toString()
                                        pref.summary = resources.getQuantityString(R.plurals.settings_sync_time_range_past_days, pastDays, pastDays)
                                    } else {
                                        pref.text = null
                                        pref.setSummary(R.string.settings_sync_time_range_past_none)
                                    }
                                    pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                                        val days = try {
                                            (newValue as String).toInt()
                                        } catch(e: NumberFormatException) {
                                            -1
                                        }
                                        model.updateTimeRangePastDays(if (days < 0) null else days)
                                        false
                                    }
                                } else
                                    pref.isVisible = false
                            }
                        else
                            pref.isVisible = false
                    }

                    findPreference<EditTextPreference>(getString(R.string.settings_key_default_alarm))!!.let { pref ->
                        if (hasCalendars)
                            model.defaultAlarmMinBefore.observe(viewLifecycleOwner) { minBefore ->
                                pref.isVisible = true
                                if (minBefore != null) {
                                    pref.text = minBefore.toString()
                                    pref.summary = resources.getQuantityString(R.plurals.settings_default_alarm_on, minBefore, minBefore)
                                } else {
                                    pref.text = null
                                    pref.summary = getString(R.string.settings_default_alarm_off)
                                }
                                pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                                    val minBefore = try {
                                        (newValue as String).toInt()
                                    } catch (e: java.lang.NumberFormatException) {
                                        null
                                    }
                                    model.updateDefaultAlarm(minBefore)
                                    false
                                }
                            }
                        else
                            pref.isVisible = false
                    }

                    findPreference<SwitchPreferenceCompat>(getString(R.string.settings_manage_calendar_colors_key))!!.let {
                        model.manageCalendarColors.observe(viewLifecycleOwner) { manageCalendarColors ->
                            it.isEnabled = !settings.containsKey(AccountSettings.KEY_MANAGE_CALENDAR_COLORS)
                            it.isChecked = manageCalendarColors
                            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                                model.updateManageCalendarColors(newValue as Boolean)
                                false
                            }
                        }
                    }

                    findPreference<SwitchPreferenceCompat>(getString(R.string.settings_event_colors_key))!!.let { pref ->
                        if (hasCalendars)
                            model.eventColors.observe(viewLifecycleOwner) { eventColors ->
                                pref.isVisible = true
                                pref.isEnabled = !settings.containsKey(AccountSettings.KEY_EVENT_COLORS)
                                pref.isChecked = eventColors
                                pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                                    model.updateEventColors(newValue as Boolean)
                                    false
                                }
                            }
                        else
                            pref.isVisible = false
                    }
                }
            }

            // preference group: CardDAV
            model.syncIntervalContacts.observe(viewLifecycleOwner) { contactsSyncInterval ->
                val hasCardDav = contactsSyncInterval != null
                if (!hasCardDav)
                    findPreference<PreferenceGroup>(getString(R.string.settings_carddav_key))!!.isVisible = false
                else {
                    findPreference<PreferenceGroup>(getString(R.string.settings_carddav_key))!!.isVisible = true
                    findPreference<ListPreference>(getString(R.string.settings_contact_group_method_key))!!.let {
                        model.contactGroupMethod.observe(viewLifecycleOwner) { groupMethod ->
                            if (model.syncIntervalContacts.value != null) {
                                it.isVisible = true
                                it.value = groupMethod.name
                                it.summary = it.entry
                                if (settings.containsKey(AccountSettings.KEY_CONTACT_GROUP_METHOD))
                                    it.isEnabled = false
                                else {
                                    it.isEnabled = true
                                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, groupMethod ->
                                        model.updateContactGroupMethod(GroupMethod.valueOf(groupMethod as String))
                                        false
                                    }
                                }
                            } else
                                it.isVisible = false
                        }
                    }
                }
            }
        }

        @SuppressLint("WrongConstant")
        private fun checkWifiPermissions() {
            if (model.syncWifiOnlySSIDs.value != null && !PermissionUtils.canAccessWifiSsid(requireActivity()))
                Snackbar.make(requireView(), R.string.settings_sync_wifi_only_ssids_permissions_required, UiUtils.SNACKBAR_LENGTH_VERY_LONG)
                        .setAction(R.string.settings_sync_wifi_only_ssids_permissions_action) {
                            val intent = Intent(requireActivity(), WifiPermissionsActivity::class.java)
                            intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, account)
                            startActivity(intent)
                        }
                    .show()
        }

    }*/


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
        val hasCalDav = object: MediatorLiveData<Boolean>() {
            init {
                addSource(syncIntervalCalendars) { calculate() }
                addSource(syncIntervalTasks) { calculate() }
            }
            private fun calculate() {
                value = syncIntervalCalendars.value != null || syncIntervalTasks.value != null
            }
        }

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
            val resync = if (fullResync) SyncWorker.FULL_RESYNC else SyncWorker.RESYNC
            SyncWorker.enqueue(context, account, authority, expedited = true, resync = resync)
        }

    }

}
