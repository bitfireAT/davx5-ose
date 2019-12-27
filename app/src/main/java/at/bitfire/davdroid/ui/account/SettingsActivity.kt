/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.content.SyncStatusObserver
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.security.KeyChain
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import androidx.preference.*
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.syncadapter.SyncAdapterService
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.apache.commons.lang3.StringUtils

class SettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private lateinit var account: Account


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.getParcelableExtra(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
        title = getString(R.string.settings_title, account.name)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null)
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DialogFragment.instantiate(this, AccountSettingsFragment::class.java.name, intent.extras))
                    .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            if (item.itemId == android.R.id.home) {
                val intent = Intent(this, AccountActivity::class.java)
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                NavUtils.navigateUpTo(this, intent)
                true
            } else
                false


    class AccountSettingsFragment: PreferenceFragmentCompat() {
        private lateinit var settings: Settings
        lateinit var account: Account

        private lateinit var model: Model

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            settings = Settings.getInstance(requireActivity())
            account = arguments!!.getParcelable(EXTRA_ACCOUNT)!!

            model = ViewModelProviders.of(this).get(Model::class.java)
            model.initialize(account)

            initSettings()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings_account)
        }

        private fun initSettings() {
            //val accountSettings = AccountSettings(requireActivity(), account)

            // preference group: sync
            findPreference<ListPreference>(getString(R.string.settings_sync_interval_contacts_key))!!.let {
                model.syncIntervalContacts.observe(this, Observer { interval ->
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
                            model.updateSyncInterval(getString(R.string.address_books_authority), (newValue as String).toLong())
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }
            findPreference<ListPreference>(getString(R.string.settings_sync_interval_calendars_key))!!.let {
                model.syncIntervalCalendars.observe(this, Observer { interval ->
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
                model.syncIntervalTasks.observe(this, Observer { interval ->
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
                            model.updateSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, (newValue as String).toLong())
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }

            findPreference<SwitchPreferenceCompat>(getString(R.string.settings_sync_wifi_only_key))!!.let {
                model.syncWifiOnly.observe(this, Observer { wifiOnly ->
                    it.isEnabled = !settings.has(AccountSettings.KEY_WIFI_ONLY)
                    it.isChecked = wifiOnly
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, wifiOnly ->
                        model.updateSyncWifiOnly(wifiOnly as Boolean)
                        false
                    }
                })
            }

            findPreference<EditTextPreference>("sync_wifi_only_ssids")!!.let {
                model.syncWifiOnlySSIDs.observe(this, Observer { onlySSIDs ->
                    if (onlySSIDs != null) {
                        it.text = onlySSIDs.joinToString(", ")
                        it.summary = getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
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

            model.askForPermissions.observe(this, Observer { permissions ->
                if (permissions.any { ContextCompat.checkSelfPermission(requireActivity(), it) != PackageManager.PERMISSION_GRANTED }) {
                    if (permissions.any { shouldShowRequestPermissionRationale(it) })
                        // show rationale before requesting permissions
                        MaterialAlertDialogBuilder(requireActivity())
                                .setIcon(R.drawable.ic_network_wifi_dark)
                                .setTitle(R.string.settings_sync_wifi_only_ssids)
                                .setMessage(R.string.settings_sync_wifi_only_ssids_location_permission)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    requestPermissions(permissions.toTypedArray(), 0)
                                }
                                .setNeutralButton(R.string.settings_more_info_faq) { _, _ ->
                                    val faqUrl = App.homepageUrl(requireActivity()).buildUpon()
                                            .appendPath("faq").appendPath("wifi-ssid-restriction-location-permission")
                                            .build()
                                    val intent = Intent(Intent.ACTION_VIEW, faqUrl)
                                    startActivity(Intent.createChooser(intent, null))
                                }
                                .show()
                    else
                        // request permissions without rationale
                        requestPermissions(permissions.toTypedArray(), 0)
                }
            })

            // preference group: authentication
            val prefUserName = findPreference<EditTextPreference>("username")!!
            val prefPassword = findPreference<EditTextPreference>("password")!!
            val prefCertAlias = findPreference<Preference>("certificate_alias")!!
            model.credentials.observe(this, Observer { credentials ->
                when (credentials.type) {
                    Credentials.Type.UsernamePassword -> {
                        prefUserName.isVisible = true
                        prefUserName.summary = credentials.userName
                        prefUserName.text = credentials.userName
                        prefUserName.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                            model.updateCredentials(Credentials(newValue as String, credentials.password))
                            false
                        }

                        prefPassword.isVisible = true
                        prefPassword.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                            model.updateCredentials(Credentials(credentials.userName, newValue as String))
                            false
                        }

                        prefCertAlias.isVisible = false
                    }
                    Credentials.Type.ClientCertificate -> {
                        prefUserName.isVisible = false
                        prefPassword.isVisible = false

                        prefCertAlias.isVisible = true
                        prefCertAlias.summary = credentials.certificateAlias
                        prefCertAlias.setOnPreferenceClickListener {
                            KeyChain.choosePrivateKeyAlias(requireActivity(), { alias ->
                                model.updateCredentials(Credentials(certificateAlias = alias))
                            }, null, null, null, -1, credentials.certificateAlias)
                            true
                        }
                    }
                }
            })

            // preference group: CalDAV
            findPreference<EditTextPreference>(getString(R.string.settings_sync_time_range_past_key))!!.let {
                model.timeRangePastDays.observe(this, Observer { pastDays ->
                    if (model.syncIntervalCalendars.value != null) {
                        it.isVisible = true
                        if (pastDays != null) {
                            it.text = pastDays.toString()
                            it.summary = resources.getQuantityString(R.plurals.settings_sync_time_range_past_days, pastDays, pastDays)
                        } else {
                            it.text = null
                            it.setSummary(R.string.settings_sync_time_range_past_none)
                        }
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                            val days = try {
                                (newValue as String).toInt()
                            } catch(e: NumberFormatException) {
                                -1
                            }
                            model.updateTimeRangePastDays(if (days < 0) null else days)
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }

            findPreference<EditTextPreference>(getString(R.string.settings_key_default_alarm))!!.let {
                model.defaultAlarmMinBefore.observe(this, Observer { minBefore ->
                    if (minBefore != null) {
                        it.text = minBefore.toString()
                        it.summary = resources.getQuantityString(R.plurals.settings_default_alarm_on, minBefore, minBefore)
                    } else {
                        it.text = null
                        it.summary = getString(R.string.settings_default_alarm_off)
                    }
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        val minBefore = try {
                            (newValue as String).toInt()
                        } catch (e: java.lang.NumberFormatException) {
                            null
                        }
                        model.updateDefaultAlarm(minBefore)
                        false
                    }
                })
            }

            findPreference<SwitchPreferenceCompat>(getString(R.string.settings_manage_calendar_colors_key))!!.let {
                model.manageCalendarColors.observe(this, Observer { manageCalendarColors ->
                    if (model.syncIntervalCalendars.value != null || model.syncIntervalTasks.value != null) {
                        it.isVisible = true
                        it.isEnabled = !settings.has(AccountSettings.KEY_MANAGE_CALENDAR_COLORS)
                        it.isChecked = manageCalendarColors
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                            model.updateManageCalendarColors(newValue as Boolean)
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }

            findPreference<SwitchPreferenceCompat>(getString(R.string.settings_event_colors_key))!!.let {
                model.eventColors.observe(this, Observer { eventColors ->
                    if (model.syncIntervalCalendars.value != null) {
                        it.isVisible = true
                        it.isEnabled = !settings.has(AccountSettings.KEY_EVENT_COLORS)
                        it.isChecked = eventColors
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                            model.updateEventColors(newValue as Boolean)
                            false
                        }
                    } else
                        it.isVisible = false
                })
            }

            // preference group: CardDAV
            findPreference<ListPreference>(getString(R.string.settings_contact_group_method_key))!!.let {
                model.contactGroupMethod.observe(this, Observer { groupMethod ->
                    if (model.syncIntervalContacts.value != null) {
                        it.isVisible = true
                        it.value = groupMethod.name
                        it.summary = it.entry
                        if (settings.has(AccountSettings.KEY_CONTACT_GROUP_METHOD))
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

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)

            if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
                // location permission denied, reset SSID restriction
                model.updateSyncWifiOnlySSIDs(null)

                MaterialAlertDialogBuilder(requireActivity())
                        .setIcon(R.drawable.ic_network_wifi_dark)
                        .setTitle(R.string.settings_sync_wifi_only_ssids)
                        .setMessage(R.string.settings_sync_wifi_only_ssids_location_permission)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .setNeutralButton(R.string.settings_more_info_faq) { _, _ ->
                            val faqUrl = App.homepageUrl(requireActivity()).buildUpon()
                                    .appendPath("faq").appendPath("wifi-ssid-restriction-location-permission")
                                    .build()
                            val intent = Intent(Intent.ACTION_VIEW, faqUrl)
                            startActivity(Intent.createChooser(intent, null))
                        }
                        .show()
            }
        }

    }


    class Model(app: Application): AndroidViewModel(app), SyncStatusObserver, Settings.OnChangeListener {

        private var account: Account? = null
        private var accountSettings: AccountSettings? = null

        private val settings = Settings.getInstance(app)
        private var statusChangeListener: Any? = null

        // settings
        val syncIntervalContacts = MutableLiveData<Long>()
        val syncIntervalCalendars = MutableLiveData<Long>()
        val syncIntervalTasks = MutableLiveData<Long>()
        val syncWifiOnly = MutableLiveData<Boolean>()
        val syncWifiOnlySSIDs = MutableLiveData<List<String>>()

        val credentials = MutableLiveData<Credentials>()

        val timeRangePastDays = MutableLiveData<Int>()
        val defaultAlarmMinBefore = MutableLiveData<Int>()
        val manageCalendarColors = MutableLiveData<Boolean>()
        val eventColors = MutableLiveData<Boolean>()

        val contactGroupMethod = MutableLiveData<GroupMethod>()

        // derived values
        val askForPermissions = object: MediatorLiveData<List<String>>() {
            init {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    addSource(syncWifiOnly) { calculate() }
                    addSource(syncWifiOnlySSIDs) { calculate() }
                }
            }
            private fun calculate() {
                val wifiOnly = syncWifiOnly.value ?: return
                val wifiOnlySSIDs = syncWifiOnlySSIDs.value ?: return

                val permissions = mutableListOf<String>()
                if (wifiOnly && wifiOnlySSIDs.isNotEmpty()) {
                    // Android 8.1+: getting the WiFi name requires location permission (and active location services)
                    permissions += Manifest.permission.ACCESS_FINE_LOCATION

                    // Android 10+: getting the Wifi name in the background (= while syncing) requires extra permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        permissions += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                }

                if (permissions != value)
                    postValue(permissions)
            }
        }


        fun initialize(account: Account) {
            if (this.account != null)
                // already initialized
                return

            this.account = account
            accountSettings = AccountSettings(getApplication(), account)

            settings.addOnChangeListener(this)
            statusChangeListener = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)

            reload()
        }

        override fun onCleared() {
            super.onCleared()

            statusChangeListener?.let {
                ContentResolver.removeStatusChangeListener(it)
            }
            settings.removeOnChangeListener(this)
        }

        override fun onStatusChanged(which: Int) {
            reload()
        }

        override fun onSettingsChanged() {
            reload()
        }

        private fun reload() {
            val accountSettings = accountSettings ?: return
            val context = getApplication<Application>()

            syncIntervalContacts.postValue(accountSettings.getSyncInterval(context.getString(R.string.address_books_authority)))
            syncIntervalCalendars.postValue(accountSettings.getSyncInterval(CalendarContract.AUTHORITY))
            syncIntervalTasks.postValue(accountSettings.getSyncInterval(TaskProvider.ProviderName.OpenTasks.authority))
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
            accountSettings?.setSyncInterval(authority, syncInterval)
            reload()
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

            resyncCalendars(fullResync = false, tasks = false)
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

            resync(getApplication<Application>().getString(R.string.address_books_authority), fullResync = true)
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
