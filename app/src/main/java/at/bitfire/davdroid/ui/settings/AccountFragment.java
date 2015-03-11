/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.settings;

import android.accounts.Account;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import lombok.Setter;

public class AccountFragment extends PreferenceFragment {
	final static String ARG_ACCOUNT = "account";

	Account account;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings_account_prefs);

		account = getArguments().getParcelable(ARG_ACCOUNT);
		readFromAccount();
	}

	public void readFromAccount() {
		final AccountSettings settings = new AccountSettings(getActivity(), account);

		final EditTextPreference prefUserName = (EditTextPreference)findPreference("username");
		prefUserName.setSummary(settings.getUserName());
		prefUserName.setText(settings.getUserName());
		prefUserName.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				settings.setUserName((String)newValue);
				readFromAccount();
				return true;
			}
		});

		final EditTextPreference prefPassword = (EditTextPreference)findPreference("password");
		prefPassword.setText(settings.getPassword());
		prefPassword.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				settings.setPassword((String)newValue);
				readFromAccount();
				return true;
			}
		});

		final CheckBoxPreference prefPreemptive = (CheckBoxPreference)findPreference("preemptive");
		prefPreemptive.setChecked(settings.getPreemptiveAuth());
		prefPreemptive.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				settings.setPreemptiveAuth((Boolean)newValue);
				readFromAccount();
				return true;
			}
		});

		final ListPreference prefSyncContacts = (ListPreference)findPreference("sync_interval_contacts");
		final Long syncIntervalContacts = settings.getContactsSyncInterval();
		if (syncIntervalContacts != null) {
			prefSyncContacts.setValue(syncIntervalContacts.toString());
			if (syncIntervalContacts == AccountSettings.SYNC_INTERVAL_MANUALLY)
				prefSyncContacts.setSummary(R.string.settings_sync_summary_manually);
			else
				prefSyncContacts.setSummary(getString(R.string.settings_sync_summary_periodically, syncIntervalContacts / 60));
			prefSyncContacts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					settings.setContactsSyncInterval(Long.parseLong((String)newValue));
					readFromAccount();
					return true;
				}
			});
		} else {
			prefSyncContacts.setEnabled(false);
			prefSyncContacts.setSummary(R.string.settings_sync_summary_not_available);
		}

		final ListPreference prefSyncCalendars = (ListPreference)findPreference("sync_interval_calendars");
		final Long syncIntervalCalendars = settings.getCalendarsSyncInterval();
		if (syncIntervalCalendars != null) {
			prefSyncCalendars.setValue(syncIntervalCalendars.toString());
			if (syncIntervalCalendars == AccountSettings.SYNC_INTERVAL_MANUALLY)
				prefSyncCalendars.setSummary(R.string.settings_sync_summary_manually);
			else
				prefSyncCalendars.setSummary(getString(R.string.settings_sync_summary_periodically, syncIntervalCalendars / 60));
			prefSyncCalendars.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					settings.setCalendarsSyncInterval(Long.parseLong((String)newValue));
					readFromAccount();
					return true;
				}
			});
		} else {
			prefSyncCalendars.setEnabled(false);
			prefSyncCalendars.setSummary(R.string.settings_sync_summary_not_available);
		}
	}
}
