/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncStatusObserver
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.security.KeyChain
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.*
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.SyncAdapterService
import at.bitfire.davdroid.ui.UiUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import com.google.android.material.snackbar.Snackbar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set") }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.settings_title, account.name)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null)
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DialogFragment.instantiate(this, AccountSettingsFragment::class.java.name, intent.extras))
                    .commit()
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }


    @AndroidEntryPoint
    class AccountSettingsFragment(): PreferenceFragmentCompat() {

        private val account by lazy { requireArguments().getParcelable<Account>(EXTRA_ACCOUNT)!! }
        @Inject lateinit var settings: SettingsManager

        @Inject lateinit var modelFactory: Model.Factory
        val model by viewModels<Model> {
            object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) =
                    modelFactory.create(account) as T
            }
        }


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
            // preference group: sync
            findPreference<ListPreference>(getString(R.string.settings_sync_interval_contacts_key))!!.let {
                model.syncIntervalContacts.observe(viewLifecycleOwner, { interval: Long? ->
                    if (interval != null) {
                        it.isEnabled = true
                        it.isVisible = true
                        it.value = interval.toString()
                        if (interval == AccountSettings.SYNC_INTERVAL_MANUALLY)
                            it.setSummary(R.string.settings_sync_summary_manually)
                        else
                            it.summary = getString(R.string.settings_sync_summary_periodically, interval / 60)
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
                            pref.isEnabled = false      // disable until updated setting is read from system again
                            model.updateSyncInterval(getString(R.string.address_books_authority), (newValue as String).toLong())
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }
            findPreference<ListPreference>(getString(R.string.settings_sync_interval_calendars_key))!!.let {
                model.syncIntervalCalendars.observe(viewLifecycleOwner, { interval: Long? ->
                    if (interval != null) {
                        it.isEnabled = true
                        it.isVisible = true
                        it.value = interval.toString()
                        if (interval == AccountSettings.SYNC_INTERVAL_MANUALLY)
                            it.setSummary(R.string.settings_sync_summary_manually)
                        else
                            it.summary = getString(R.string.settings_sync_summary_periodically, interval / 60)
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
                            pref.isEnabled = false
                            model.updateSyncInterval(CalendarContract.AUTHORITY, (newValue as String).toLong())
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }
            findPreference<ListPreference>(getString(R.string.settings_sync_interval_tasks_key))!!.let {
                model.syncIntervalTasks.observe(viewLifecycleOwner, { interval: Long? ->
                    val provider = model.tasksProvider
                    if (provider != null && interval != null) {
                        it.isEnabled = true
                        it.isVisible = true
                        it.value = interval.toString()
                        if (interval == AccountSettings.SYNC_INTERVAL_MANUALLY)
                            it.setSummary(R.string.settings_sync_summary_manually)
                        else
                            it.summary = getString(R.string.settings_sync_summary_periodically, interval / 60)
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
                            pref.isEnabled = false
                            model.updateSyncInterval(provider.authority, (newValue as String).toLong())
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }

            findPreference<SwitchPreferenceCompat>(getString(R.string.settings_sync_wifi_only_key))!!.let {
                model.syncWifiOnly.observe(viewLifecycleOwner, { wifiOnly ->
                    it.isEnabled = !settings.containsKey(AccountSettings.KEY_WIFI_ONLY)
                    it.isChecked = wifiOnly
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, wifiOnly ->
                        model.updateSyncWifiOnly(wifiOnly as Boolean)
                        false
                    }
                })
            }

            findPreference<EditTextPreference>(getString(R.string.settings_sync_wifi_only_ssids_key))!!.let {
                model.syncWifiOnly.observe(viewLifecycleOwner, { wifiOnly ->
                    it.isEnabled = wifiOnly && settings.isWritable(AccountSettings.KEY_WIFI_ONLY_SSIDS)
                })
                model.syncWifiOnlySSIDs.observe(viewLifecycleOwner, { onlySSIDs ->
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
                })
            }

            // preference group: authentication
            val prefUserName = findPreference<EditTextPreference>("username")!!
            val prefPassword = findPreference<EditTextPreference>("password")!!
            val prefCertAlias = findPreference<Preference>("certificate_alias")!!
            model.credentials.observe(viewLifecycleOwner, { credentials ->
                prefUserName.summary = credentials.userName
                prefUserName.text = credentials.userName
                prefUserName.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newUserName ->
                    model.updateCredentials(Credentials(newUserName as String, credentials.password, credentials.certificateAlias))
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
            })

            // preference group: CalDAV
            model.hasCalDav.observe(viewLifecycleOwner, { hasCalDav ->
                if (!hasCalDav)
                    findPreference<PreferenceGroup>(getString(R.string.settings_caldav_key))!!.isVisible = false
                else {
                    findPreference<PreferenceGroup>(getString(R.string.settings_caldav_key))!!.isVisible = true

                    // when model.hasCalDav is available, model.syncInterval* are also available
                    // (because hasCalDav is calculated from them)
                    val hasCalendars = model.syncIntervalCalendars.value != null

                    findPreference<EditTextPreference>(getString(R.string.settings_sync_time_range_past_key))!!.let { pref ->
                        if (hasCalendars)
                            model.timeRangePastDays.observe(viewLifecycleOwner, { pastDays ->
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
                            })
                        else
                            pref.isVisible = false
                    }

                    findPreference<EditTextPreference>(getString(R.string.settings_key_default_alarm))!!.let { pref ->
                        if (hasCalendars)
                            model.defaultAlarmMinBefore.observe(viewLifecycleOwner, { minBefore ->
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
                            })
                        else
                            pref.isVisible = false
                    }

                    findPreference<SwitchPreferenceCompat>(getString(R.string.settings_manage_calendar_colors_key))!!.let {
                        model.manageCalendarColors.observe(viewLifecycleOwner, { manageCalendarColors ->
                            it.isEnabled = !settings.containsKey(AccountSettings.KEY_MANAGE_CALENDAR_COLORS)
                            it.isChecked = manageCalendarColors
                            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                                model.updateManageCalendarColors(newValue as Boolean)
                                false
                            }
                        })
                    }

                    findPreference<SwitchPreferenceCompat>(getString(R.string.settings_event_colors_key))!!.let { pref ->
                        if (hasCalendars)
                            model.eventColors.observe(viewLifecycleOwner, { eventColors ->
                                pref.isVisible = true
                                pref.isEnabled = !settings.containsKey(AccountSettings.KEY_EVENT_COLORS)
                                pref.isChecked = eventColors
                                pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                                    model.updateEventColors(newValue as Boolean)
                                    false
                                }
                            })
                        else
                            pref.isVisible = false
                    }
                }
            })

            // preference group: CardDAV
            model.syncIntervalContacts.observe(viewLifecycleOwner, { contactsSyncInterval ->
                val hasCardDav = contactsSyncInterval != null
                if (!hasCardDav)
                    findPreference<PreferenceGroup>(getString(R.string.settings_carddav_key))!!.isVisible = false
                else {
                    findPreference<PreferenceGroup>(getString(R.string.settings_carddav_key))!!.isVisible = true
                    findPreference<ListPreference>(getString(R.string.settings_contact_group_method_key))!!.let {
                        model.contactGroupMethod.observe(viewLifecycleOwner, { groupMethod ->
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
                        })
                    }
                }
            })
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

    }


    class Model @AssistedInject constructor(
        @ApplicationContext val context: Context,
        val settings: SettingsManager,
        @Assisted val account: Account
    ): ViewModel(), SyncStatusObserver, SettingsManager.OnChangeListener {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        private var accountSettings: AccountSettings? = null

        private var statusChangeListener: Any? = null

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

        val credentials = MutableLiveData<Credentials>()

        val timeRangePastDays = MutableLiveData<Int>()
        val defaultAlarmMinBefore = MutableLiveData<Int>()
        val manageCalendarColors = MutableLiveData<Boolean>()
        val eventColors = MutableLiveData<Boolean>()

        val contactGroupMethod = MutableLiveData<GroupMethod>()


        init {
            accountSettings = AccountSettings(context, account)

            settings.addOnChangeListener(this)
            statusChangeListener = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)

            reload()
        }

        override fun onCleared() {
            super.onCleared()

            statusChangeListener?.let {
                ContentResolver.removeStatusChangeListener(it)
                statusChangeListener = null
            }
            settings.removeOnChangeListener(this)
        }

        override fun onStatusChanged(which: Int) {
            Logger.log.info("Sync settings changed")
            reload()
        }

        override fun onSettingsChanged() {
            Logger.log.info("Settings changed")
            reload()
        }

        fun reload() {
            val accountSettings = accountSettings ?: return

            syncIntervalContacts.postValue(accountSettings.getSyncInterval(context.getString(R.string.address_books_authority)))
            syncIntervalCalendars.postValue(accountSettings.getSyncInterval(CalendarContract.AUTHORITY))
            syncIntervalTasks.postValue(tasksProvider?.let { accountSettings.getSyncInterval(it.authority) })

            syncWifiOnly.postValue(accountSettings.getSyncWifiOnly())
            syncWifiOnlySSIDs.postValue(accountSettings.getSyncWifiOnlySSIDs())

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

            resync(context.getString(R.string.address_books_authority), fullResync = true)
        }

        /**
         * Initiates calendar re-synchronization.
         *
         * @param fullResync whether sync shall download all events again
         * (_true_: sets [SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC],
         * _false_: sets [ContentResolver.SYNC_EXTRAS_MANUAL])
         * @param tasks whether tasks shall be synchronized, too (false: only events, true: events and tasks)
         */
        private fun resyncCalendars(fullResync: Boolean, tasks: Boolean) {
            resync(CalendarContract.AUTHORITY, fullResync)
            if (tasks)
                resync(TaskProvider.ProviderName.OpenTasks.authority, fullResync)
        }

        private fun resync(authority: String, fullResync: Boolean) {
            val args = Bundle(1)
            args.putBoolean(if (fullResync)
                    SyncAdapterService.SYNC_EXTRAS_FULL_RESYNC
                else
                    SyncAdapterService.SYNC_EXTRAS_RESYNC, true)

            ContentResolver.requestSync(account, authority, args)
        }

    }

}
