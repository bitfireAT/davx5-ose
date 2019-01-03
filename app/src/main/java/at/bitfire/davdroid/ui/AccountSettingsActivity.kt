/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.Intent
import android.content.SyncStatusObserver
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.security.KeyChain
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.preference.*
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import org.apache.commons.lang3.StringUtils

class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private lateinit var account: Account


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.getParcelableExtra(EXTRA_ACCOUNT)
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


    class AccountSettingsFragment: PreferenceFragmentCompat(), SyncStatusObserver, Settings.OnChangeListener {
        private lateinit var settings: Settings

        lateinit var account: Account
        private var statusChangeListener: Any? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            settings = Settings.getInstance(requireActivity())
            account = arguments!!.getParcelable(EXTRA_ACCOUNT)!!
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings_account)
        }

        override fun onResume() {
            super.onResume()

            statusChangeListener = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
            settings.addOnChangeListener(this)

            reload()
        }

        override fun onPause() {
            super.onPause()
            statusChangeListener?.let {
                ContentResolver.removeStatusChangeListener(it)
                statusChangeListener = null
            }
            settings.removeOnChangeListener(this)
        }

        override fun onStatusChanged(which: Int) {
            Handler(Looper.getMainLooper()).post {
                reload()
            }
        }

        override fun onSettingsChanged()  = reload()

        fun reload() {
            val accountSettings = AccountSettings(requireActivity(), account)

            // preference group: authentication
            val prefUserName = findPreference("username") as EditTextPreference
            val prefPassword = findPreference("password") as EditTextPreference
            val prefCertAlias = findPreference("certificate_alias") as Preference

            val credentials = accountSettings.credentials()
            when (credentials.type) {
                Credentials.Type.UsernamePassword -> {
                    prefUserName.isVisible = true
                    prefUserName.summary = credentials.userName
                    prefUserName.text = credentials.userName
                    prefUserName.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.credentials(Credentials(newValue as String, credentials.password))
                        reload()
                        false
                    }

                    prefPassword.isVisible = true
                    prefPassword.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.credentials(Credentials(credentials.userName, newValue as String))
                        reload()
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
                            accountSettings.credentials(Credentials(certificateAlias = alias))
                            Handler(Looper.getMainLooper()).post {
                                reload()
                            }
                        }, null, null, null, -1, credentials.certificateAlias)
                        true
                    }
                }
            }

            // preference group: sync
            // those are null if the respective sync type is not available for this account:
            val syncIntervalContacts = accountSettings.getSyncInterval(getString(R.string.address_books_authority))
            val syncIntervalCalendars = accountSettings.getSyncInterval(CalendarContract.AUTHORITY)
            val syncIntervalTasks = accountSettings.getSyncInterval(TaskProvider.ProviderName.OpenTasks.authority)

            (findPreference("sync_interval_contacts") as ListPreference).let {
                if (syncIntervalContacts != null) {
                    it.isVisible = true
                    it.value = syncIntervalContacts.toString()
                    if (syncIntervalContacts == AccountSettings.SYNC_INTERVAL_MANUALLY)
                        it.setSummary(R.string.settings_sync_summary_manually)
                    else
                        it.summary = getString(R.string.settings_sync_summary_periodically, syncIntervalContacts / 60)
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.setSyncInterval(getString(R.string.address_books_authority), (newValue as String).toLong())
                        reload()
                        false
                    }
                } else
                    it.isVisible = false
            }

            (findPreference("sync_interval_calendars") as ListPreference).let {
                if (syncIntervalCalendars != null) {
                    it.isVisible = true
                    it.value = syncIntervalCalendars.toString()
                    if (syncIntervalCalendars == AccountSettings.SYNC_INTERVAL_MANUALLY)
                        it.setSummary(R.string.settings_sync_summary_manually)
                    else
                        it.summary = getString(R.string.settings_sync_summary_periodically, syncIntervalCalendars / 60)
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.setSyncInterval(CalendarContract.AUTHORITY, (newValue as String).toLong())
                        reload()
                        false
                    }
                } else
                    it.isVisible = false
            }

            (findPreference("sync_interval_tasks") as ListPreference).let {
                if (syncIntervalTasks != null) {
                    it.isVisible = true
                    it.value = syncIntervalTasks.toString()
                    if (syncIntervalTasks == AccountSettings.SYNC_INTERVAL_MANUALLY)
                        it.setSummary(R.string.settings_sync_summary_manually)
                    else
                        it.summary = getString(R.string.settings_sync_summary_periodically, syncIntervalTasks / 60)
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, (newValue as String).toLong())
                        reload()
                        false
                    }
                } else
                    it.isVisible = false
            }

            val prefWifiOnly = findPreference("sync_wifi_only") as SwitchPreferenceCompat
            prefWifiOnly.isEnabled = !settings.has(AccountSettings.KEY_WIFI_ONLY)
            prefWifiOnly.isChecked = accountSettings.getSyncWifiOnly()
            prefWifiOnly.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, wifiOnly ->
                accountSettings.setSyncWiFiOnly(wifiOnly as Boolean)
                reload()
                false
            }

            val prefWifiOnlySSIDs = findPreference("sync_wifi_only_ssids") as EditTextPreference
            val onlySSIDs = accountSettings.getSyncWifiOnlySSIDs()?.joinToString(", ")
            prefWifiOnlySSIDs.text = onlySSIDs
            if (onlySSIDs != null)
                prefWifiOnlySSIDs.summary = getString(R.string.settings_sync_wifi_only_ssids_on, onlySSIDs)
            else
                prefWifiOnlySSIDs.setSummary(R.string.settings_sync_wifi_only_ssids_off)
            prefWifiOnlySSIDs.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                accountSettings.setSyncWifiOnlySSIDs((newValue as String).split(',').mapNotNull { StringUtils.trimToNull(it) }.distinct())
                reload()
                false
            }

            // getting the WiFi name requires location permission (and active location services) since Android 8.1
            // see https://issuetracker.google.com/issues/70633700
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                accountSettings.getSyncWifiOnly() && onlySSIDs != null &&
                ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)

            // preference group: CardDAV
            (findPreference("contact_group_method") as ListPreference).let {
                if (syncIntervalContacts != null) {
                    it.isVisible = true
                    it.value = accountSettings.getGroupMethod().name
                    it.summary = it.entry
                    if (settings.has(AccountSettings.KEY_CONTACT_GROUP_METHOD))
                        it.isEnabled = false
                    else {
                        it.isEnabled = true
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, groupMethod ->
                            AlertDialog.Builder(requireActivity())
                                    .setIcon(R.drawable.ic_error_dark)
                                    .setTitle(R.string.settings_contact_group_method_change)
                                    .setMessage(R.string.settings_contact_group_method_change_reload_contacts)
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        // change group method
                                        accountSettings.setGroupMethod(GroupMethod.valueOf(groupMethod as String))
                                        reload()

                                        // reload all contacts
                                        val args = Bundle(1)
                                        args.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                                        ContentResolver.requestSync(account, getString(R.string.address_books_authority), args)
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            false
                        }
                    }
                } else
                    it.isVisible = false
            }

            // preference group: CalDAV
            (findPreference("time_range_past_days") as EditTextPreference).let {
                if (syncIntervalCalendars != null) {
                    it.isVisible = true
                    val pastDays = accountSettings.getTimeRangePastDays()
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
                        accountSettings.setTimeRangePastDays(if (days < 0) null else days)

                        // reset sync state of all calendars in this account to trigger a full sync
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                            requireContext().contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { provider ->
                                try {
                                    AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null).forEach { calendar ->
                                        calendar.lastSyncState = null
                                    }
                                } finally {
                                    @Suppress("DEPRECATION")
                                    if (Build.VERSION.SDK_INT >= 24)
                                        provider.close()
                                    else
                                        provider.release()
                                }
                            }
                        }

                        reload()
                        false
                    }
                } else
                    it.isVisible = false
            }

            (findPreference("manage_calendar_colors") as SwitchPreferenceCompat).let {
                if (syncIntervalCalendars != null || syncIntervalTasks != null) {
                    it.isVisible = true
                    it.isEnabled = !settings.has(AccountSettings.KEY_MANAGE_CALENDAR_COLORS)
                    it.isChecked = accountSettings.getManageCalendarColors()
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.setManageCalendarColors(newValue as Boolean)
                        reload()
                        false
                    }
                } else
                    it.isVisible = false
            }

            (findPreference("event_colors") as SwitchPreferenceCompat).let {
                if (syncIntervalCalendars != null) {
                    it.isVisible = true
                    it.isEnabled = !settings.has(AccountSettings.KEY_EVENT_COLORS)
                    it.isChecked = accountSettings.getEventColors()
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        if (newValue as Boolean) {
                            accountSettings.setEventColors(true)
                            reload()
                        } else
                            AlertDialog.Builder(requireActivity())
                                    .setIcon(R.drawable.ic_error_dark)
                                    .setTitle(R.string.settings_event_colors)
                                    .setMessage(R.string.settings_event_colors_off_confirm)
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        accountSettings.setEventColors(false)
                                        reload()
                                    }
                                    .show()
                        false
                    }
                } else
                    it.isVisible = false
            }
        }

    }

}
