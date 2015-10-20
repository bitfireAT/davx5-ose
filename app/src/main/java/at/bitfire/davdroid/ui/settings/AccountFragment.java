/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.settings;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.provider.CalendarContract;
import android.provider.ContactsContract;

import java.io.File;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.log.ExternalFileLogger;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import at.bitfire.ical4android.TaskProvider;

public class AccountFragment extends PreferenceFragment {
	final static String ARG_ACCOUNT = "account";

	Account account;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings_account_prefs);

		account = getArguments().getParcelable(ARG_ACCOUNT);
		refresh();
	}

	public void refresh() {
		final AccountSettings settings = new AccountSettings(getActivity(), account);

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
		prefPassword.setText(settings.password());
		prefPassword.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				settings.password((String) newValue);
                refresh(); return false;
			}
		});

		final SwitchPreference prefPreemptive = (SwitchPreference)findPreference("preemptive");
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

        // category: debug info

        final SwitchPreference prefLogExternalFile = (SwitchPreference)findPreference("log_external_file");
        prefLogExternalFile.setChecked(settings.logToExternalFile());
        File logDirectory = ExternalFileLogger.getDirectory(getActivity());
        prefLogExternalFile.setSummaryOn(logDirectory != null ?
                getString(R.string.settings_log_to_external_file_on, logDirectory.getPath()) :
                getString(R.string.settings_log_to_external_file_no_external_storage)
        );
        prefLogExternalFile.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean external = (Boolean) newValue;
                if (external) {
                    getFragmentManager().beginTransaction()
                            .add(LogExternalFileDialogFragment.newInstance(account), null)
                            .commit();
                    return false;
                } else {
                    settings.logToExternalFile(false);
                    refresh();
                    return false;
                }
            }
        });

        final SwitchPreference prefLogVerbose = (SwitchPreference)findPreference("log_verbose");
        prefLogVerbose.setChecked(settings.logVerbose());
        prefLogVerbose.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                settings.logVerbose((Boolean) newValue);
                refresh(); return false;
            }
        });

	}


    public static class LogExternalFileDialogFragment extends DialogFragment {
        private static final String
                KEY_ACCOUNT = "account";

        public static LogExternalFileDialogFragment newInstance(Account account) {
            Bundle args = new Bundle();
            args.putParcelable(KEY_ACCOUNT, account);
            LogExternalFileDialogFragment fragment = new LogExternalFileDialogFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public AlertDialog onCreateDialog(Bundle savedInstanceState) {
            final AccountSettings settings = new AccountSettings(getActivity(), (Account)getArguments().getParcelable(KEY_ACCOUNT));
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.settings_security_warning)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.settings_log_to_external_file_confirmation)
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            settings.logToExternalFile(false);
                            refresh();
                        }
                    })
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            settings.logToExternalFile(true);
                            refresh();
                        }
                    })
                    .create();
        }

        private void refresh() {
            AccountFragment fragment = (AccountFragment)getActivity().getFragmentManager().findFragmentByTag(SettingsActivity.TAG_ACCOUNT_SETTINGS);
            if (fragment != null)
                fragment.refresh();
        }
    }

}
