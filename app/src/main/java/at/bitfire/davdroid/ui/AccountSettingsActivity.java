/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.text.TextUtils;
import android.view.MenuItem;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.ical4android.TaskProvider;

public class AccountSettingsActivity extends AppCompatActivity {
    public final static String EXTRA_ACCOUNT = "account";

    private Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getParcelableExtra(EXTRA_ACCOUNT);
        setTitle(getString(R.string.settings_title, account.name));

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, AccountSettingsFragment.instantiate(this, AccountSettingsFragment.class.getName(), getIntent().getExtras()))
                    .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, AccountActivity.class);
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account);
            NavUtils.navigateUpTo(this, intent);
            return true;
        } else
            return false;
    }


    public static class AccountSettingsFragment extends PreferenceFragmentCompat {
        Account account;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            account = getArguments().getParcelable(EXTRA_ACCOUNT);
            refresh();
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.settings_account);
        }

        public void refresh() {
            final AccountSettings settings;

            try {
                settings = new AccountSettings(getActivity(), account);
            }  catch(InvalidAccountException e) {
                App.log.log(Level.INFO, "Account is invalid or doesn't exist (anymore)", e);
                getActivity().finish();
                return;
            }

            // category: authentication
            final EditTextPreference prefUserName = (EditTextPreference)findPreference("username");
            prefUserName.setSummary(settings.username());
            prefUserName.setText(settings.username());
            prefUserName.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    settings.username((String) newValue);
                    refresh(); return false;
                }
            });

            final EditTextPreference prefPassword = (EditTextPreference)findPreference("password");
            prefPassword.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    settings.password((String) newValue);
                    refresh(); return false;
                }
            });

            final SwitchPreferenceCompat prefPreemptive = (SwitchPreferenceCompat)findPreference("preemptive");
            prefPreemptive.setChecked(settings.preemptiveAuth());
            prefPreemptive.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    settings.preemptiveAuth((Boolean) newValue);
                    refresh(); return false;
                }
            });

            // category: synchronization
            final ListPreference prefSyncContacts = (ListPreference)findPreference("sync_interval_contacts");
            final Long syncIntervalContacts = settings.getSyncInterval(ContactsContract.AUTHORITY);
            if (syncIntervalContacts != null) {
                prefSyncContacts.setValue(syncIntervalContacts.toString());
                if (syncIntervalContacts == AccountSettings.SYNC_INTERVAL_MANUALLY)
                    prefSyncContacts.setSummary(R.string.settings_sync_summary_manually);
                else
                    prefSyncContacts.setSummary(getString(R.string.settings_sync_summary_periodically, syncIntervalContacts / 60));
                prefSyncContacts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        settings.setSyncInterval(ContactsContract.AUTHORITY, Long.parseLong((String) newValue));
                        refresh(); return false;
                    }
                });
            } else {
                prefSyncContacts.setEnabled(false);
                prefSyncContacts.setSummary(R.string.settings_sync_summary_not_available);
            }

            final ListPreference prefSyncCalendars = (ListPreference)findPreference("sync_interval_calendars");
            final Long syncIntervalCalendars = settings.getSyncInterval(CalendarContract.AUTHORITY);
            if (syncIntervalCalendars != null) {
                prefSyncCalendars.setValue(syncIntervalCalendars.toString());
                if (syncIntervalCalendars == AccountSettings.SYNC_INTERVAL_MANUALLY)
                    prefSyncCalendars.setSummary(R.string.settings_sync_summary_manually);
                else
                    prefSyncCalendars.setSummary(getString(R.string.settings_sync_summary_periodically, syncIntervalCalendars / 60));
                prefSyncCalendars.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        settings.setSyncInterval(CalendarContract.AUTHORITY, Long.parseLong((String) newValue));
                        refresh(); return false;
                    }
                });
            } else {
                prefSyncCalendars.setEnabled(false);
                prefSyncCalendars.setSummary(R.string.settings_sync_summary_not_available);
            }

            final ListPreference prefSyncTasks = (ListPreference)findPreference("sync_interval_tasks");
            final Long syncIntervalTasks = settings.getSyncInterval(TaskProvider.ProviderName.OpenTasks.authority);
            if (syncIntervalTasks != null) {
                prefSyncTasks.setValue(syncIntervalTasks.toString());
                if (syncIntervalTasks == AccountSettings.SYNC_INTERVAL_MANUALLY)
                    prefSyncTasks.setSummary(R.string.settings_sync_summary_manually);
                else
                    prefSyncTasks.setSummary(getString(R.string.settings_sync_summary_periodically, syncIntervalTasks / 60));
                prefSyncTasks.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        settings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, Long.parseLong((String) newValue));
                        refresh(); return false;
                    }
                });
            } else {
                prefSyncTasks.setEnabled(false);
                prefSyncTasks.setSummary(R.string.settings_sync_summary_not_available);
            }

            final SwitchPreferenceCompat prefWifiOnly = (SwitchPreferenceCompat)findPreference("sync_wifi_only");
            prefWifiOnly.setChecked(settings.getSyncWifiOnly());
            prefWifiOnly.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object wifiOnly) {
                    settings.setSyncWiFiOnly((Boolean)wifiOnly);
                    refresh(); return false;
                }
            });

            final EditTextPreference prefWifiOnlySSID = (EditTextPreference)findPreference("sync_wifi_only_ssid");
            final String onlySSID = settings.getSyncWifiOnlySSID();
            prefWifiOnlySSID.setText(onlySSID);
            if (onlySSID != null)
                prefWifiOnlySSID.setSummary(getString(R.string.settings_sync_wifi_only_ssid_on, onlySSID));
            else
                prefWifiOnlySSID.setSummary(R.string.settings_sync_wifi_only_ssid_off);
            prefWifiOnlySSID.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String ssid = (String)newValue;
                    settings.setSyncWifiOnlySSID(!TextUtils.isEmpty(ssid) ? ssid : null);
                    refresh(); return false;
                }
            });

            // category: CalDAV
            final EditTextPreference prefTimeRangePastDays = (EditTextPreference)findPreference("caldav_time_range_past_days");
            Integer pastDays =  settings.getTimeRangePastDays();
            if (pastDays != null) {
                prefTimeRangePastDays.setText(pastDays.toString());
                prefTimeRangePastDays.setSummary(getResources().getQuantityString(R.plurals.settings_sync_time_range_past_days, pastDays, pastDays));
            } else {
                prefTimeRangePastDays.setText(null);
                prefTimeRangePastDays.setSummary(R.string.settings_sync_time_range_past_none);
            }
            prefTimeRangePastDays.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int days;
                    try {
                        days = Integer.parseInt((String)newValue);
                    } catch(NumberFormatException ignored) {
                        days = -1;
                    }
                    settings.setTimeRangePastDays(days < 0 ? null : days);
                    refresh(); return false;
                }
            });

            final SwitchPreferenceCompat prefManageColors = (SwitchPreferenceCompat)findPreference("manage_calendar_colors");
            prefManageColors.setChecked(settings.getManageCalendarColors());
            prefManageColors.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    settings.setManageCalendarColors((Boolean)newValue);
                    refresh(); return false;
                }
            });

        }

    }
}
