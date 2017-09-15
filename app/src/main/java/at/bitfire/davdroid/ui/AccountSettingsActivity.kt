/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncStatusObserver
import android.os.Bundle
import android.provider.CalendarContract
import android.support.v4.app.DialogFragment
import android.support.v4.app.LoaderManager
import android.support.v4.app.NavUtils
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.*
import android.support.v7.preference.Preference.OnPreferenceChangeListener
import android.view.MenuItem
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import org.apache.commons.lang3.StringUtils

class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        val EXTRA_ACCOUNT = "account"
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


    class AccountSettingsFragment: PreferenceFragmentCompat(), LoaderManager.LoaderCallbacks<AccountSettings> {
        lateinit var account: Account

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            account = arguments.getParcelable(EXTRA_ACCOUNT)
            loaderManager.initLoader(0, arguments, this)
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.settings_account)
        }

        override fun onCreateLoader(id: Int, args: Bundle) =
                AccountSettingsLoader(context, args.getParcelable(EXTRA_ACCOUNT))

        override fun onLoadFinished(loader: Loader<AccountSettings>, settings: AccountSettings?) {
            if (settings == null) {
                activity.finish()
                return
            }

            // category: authentication
            val prefUserName = findPreference("username") as EditTextPreference
            prefUserName.summary = settings.username()
            prefUserName.text = settings.username()
            prefUserName.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                settings.username(newValue as String)
                loaderManager.restartLoader(0, arguments, this)
                false
            }

            val prefPassword = findPreference("password") as EditTextPreference
            prefPassword.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                settings.password(newValue as String)
                loaderManager.restartLoader(0, arguments, this)
                false
            }

            // category: synchronization
            val prefSyncContacts = findPreference("sync_interval_contacts") as ListPreference
            val syncIntervalContacts = settings.getSyncInterval(getString(R.string.address_books_authority))
            if (syncIntervalContacts != null) {
                prefSyncContacts.value = syncIntervalContacts.toString()
                if (syncIntervalContacts == AccountSettings.SYNC_INTERVAL_MANUALLY)
                    prefSyncContacts.setSummary(R.string.settings_sync_summary_manually)
                else
                    prefSyncContacts.summary = getString(R.string.settings_sync_summary_periodically, syncIntervalContacts / 60)
                prefSyncContacts.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                    settings.setSyncInterval(getString(R.string.address_books_authority), (newValue as String).toLong())
                    loaderManager.restartLoader(0, arguments, this)
                    false
                }
            } else
                prefSyncContacts.isVisible = false

            val prefSyncCalendars = findPreference("sync_interval_calendars") as ListPreference
            val syncIntervalCalendars = settings.getSyncInterval(CalendarContract.AUTHORITY)
            if (syncIntervalCalendars != null) {
                prefSyncCalendars.value = syncIntervalCalendars.toString()
                if (syncIntervalCalendars == AccountSettings.SYNC_INTERVAL_MANUALLY)
                    prefSyncCalendars.setSummary(R.string.settings_sync_summary_manually)
                else
                    prefSyncCalendars.summary = getString(R.string.settings_sync_summary_periodically, syncIntervalCalendars / 60)
                prefSyncCalendars.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    settings.setSyncInterval(CalendarContract.AUTHORITY, (newValue as String).toLong())
                    loaderManager.restartLoader(0, arguments, this)
                    false
                }
            } else
                prefSyncCalendars.isVisible = false

            val prefSyncTasks = findPreference("sync_interval_tasks") as ListPreference
            val syncIntervalTasks = settings.getSyncInterval(TaskProvider.ProviderName.OpenTasks.authority)
            if (syncIntervalTasks != null) {
                prefSyncTasks.value = syncIntervalTasks.toString()
                if (syncIntervalTasks == AccountSettings.SYNC_INTERVAL_MANUALLY)
                    prefSyncTasks.setSummary(R.string.settings_sync_summary_manually)
                else
                    prefSyncTasks.summary = getString(R.string.settings_sync_summary_periodically, syncIntervalTasks / 60)
                prefSyncTasks.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    settings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, (newValue as String).toLong())
                    loaderManager.restartLoader(0, arguments, this)
                    false
                }
            } else
                prefSyncTasks.isVisible = false

            val prefWifiOnly = findPreference("sync_wifi_only") as SwitchPreferenceCompat
            prefWifiOnly.isChecked = settings.getSyncWifiOnly()
            prefWifiOnly.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, wifiOnly ->
                settings.setSyncWiFiOnly(wifiOnly as Boolean)
                loaderManager.restartLoader(0, arguments, this)
                false
            }

            val prefWifiOnlySSIDs = findPreference("sync_wifi_only_ssids") as EditTextPreference
            val onlySSIDs = settings.getSyncWifiOnlySSIDs()?.joinToString(", ")
            prefWifiOnlySSIDs.text = onlySSIDs
            if (onlySSIDs != null)
                prefWifiOnlySSIDs.summary = getString(R.string.settings_sync_wifi_only_ssids_on, onlySSIDs)
            else
                prefWifiOnlySSIDs.setSummary(R.string.settings_sync_wifi_only_ssids_off)
            prefWifiOnlySSIDs.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                settings.setSyncWifiOnlySSIDs((newValue as String).split(',').map { StringUtils.trimToNull(it) }.filterNotNull().distinct())
                loaderManager.restartLoader(0, arguments, this)
                false
            }

            // category: CardDAV
            val prefGroupMethod = findPreference("contact_group_method") as ListPreference
            if (syncIntervalContacts != null) {
                prefGroupMethod.value = settings.getGroupMethod().name
                prefGroupMethod.summary = prefGroupMethod.entry
                prefGroupMethod.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, groupMethod ->
                    settings.setGroupMethod(GroupMethod.valueOf(groupMethod as String))
                    loaderManager.restartLoader(0, arguments, this)
                    false
                }
            } else
                prefGroupMethod.isEnabled = false

            // category: CalDAV
            val prefTimeRangePastDays = findPreference("time_range_past_days") as EditTextPreference
            if (syncIntervalCalendars != null) {
                val pastDays = settings.getTimeRangePastDays()
                if (pastDays != null) {
                    prefTimeRangePastDays.text = pastDays.toString()
                    prefTimeRangePastDays.summary = resources.getQuantityString(R.plurals.settings_sync_time_range_past_days, pastDays, pastDays)
                } else {
                    prefTimeRangePastDays.text = null
                    prefTimeRangePastDays.setSummary(R.string.settings_sync_time_range_past_none)
                }
                prefTimeRangePastDays.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    var days: Int
                    try {
                        days = (newValue as String).toInt()
                    } catch(ignored: NumberFormatException) {
                        days = -1
                    }
                    settings.setTimeRangePastDays(if (days < 0) null else days)
                    loaderManager.restartLoader(0, arguments, this)
                    false
                }
            } else
                prefTimeRangePastDays.isEnabled = false

            val prefManageColors = findPreference("manage_calendar_colors") as SwitchPreferenceCompat
            if (syncIntervalCalendars != null || syncIntervalTasks != null) {
                prefManageColors.isChecked = settings.getManageCalendarColors()
                prefManageColors.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    settings.setManageCalendarColors(newValue as Boolean)
                    loaderManager.restartLoader(0, arguments, this)
                    false
                }
            } else
                prefManageColors.isEnabled = false

            val prefEventColors = findPreference("event_colors") as SwitchPreferenceCompat
            prefEventColors.isChecked = settings.getEventColors()
            prefEventColors.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    settings.setEventColors(true)
                    loaderManager.restartLoader(0, arguments, this)
                } else
                    AlertDialog.Builder(activity)
                            .setIcon(R.drawable.ic_error_dark)
                            .setTitle(R.string.settings_event_colors)
                            .setMessage(R.string.settings_event_colors_off_confirm)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok, { _, _ ->
                                settings.setEventColors(false)
                                loaderManager.restartLoader(0, arguments, this)
                            })
                            .show()
                false
            }

        }

        override fun onLoaderReset(loader: Loader<AccountSettings>) {
        }

    }


    class AccountSettingsLoader(
            context: Context,
            val account: Account
    ): AsyncTaskLoader<AccountSettings>(context), SyncStatusObserver {

        lateinit var listenerHandle: Any

        override fun onStartLoading() {
            forceLoad()
            listenerHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
        }

        override fun onStopLoading() =
            ContentResolver.removeStatusChangeListener(listenerHandle)

        override fun abandon() = onStopLoading()

        override fun loadInBackground() =
                try {
                    AccountSettings(context, account)
                } catch(e: InvalidAccountException) {
                    null
                }

        override fun onStatusChanged(which: Int) {
            Logger.log.fine("Reloading account settings")
            forceLoad()
        }

    }

}
