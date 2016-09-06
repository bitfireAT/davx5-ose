/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.SwitchPreferenceCompat;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.Settings;
import lombok.Cleanup;

public class AppSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }


    public static class SettingsFragment extends PreferenceFragmentCompat {
        ServiceDB.OpenHelper dbHelper;
        Settings settings;

        Preference
                prefResetHints,
                prefResetCertificates;
        SwitchPreferenceCompat
                prefDistrustSystemCerts,
                prefLogToExternalStorage;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            dbHelper = new ServiceDB.OpenHelper(getContext());
            settings = new Settings(dbHelper.getReadableDatabase());

            super.onCreate(savedInstanceState);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            dbHelper.close();
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.settings_app);

            prefResetHints = findPreference("reset_hints");

            prefDistrustSystemCerts = (SwitchPreferenceCompat)findPreference("distrust_system_certs");
            if (App.getCertManager() == null)
                prefDistrustSystemCerts.setVisible(false);
            else
                prefDistrustSystemCerts.setChecked(settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false));

            prefResetCertificates = findPreference("reset_certificates");
            if (App.getCertManager() == null)
                prefResetCertificates.setVisible(false);

            prefLogToExternalStorage = (SwitchPreferenceCompat)findPreference("log_to_external_storage");
            prefLogToExternalStorage.setChecked(settings.getBoolean(App.LOG_TO_EXTERNAL_STORAGE, false));
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            if (preference == prefResetHints)
                resetHints();
            else if (preference == prefDistrustSystemCerts)
                setDistrustSystemCerts(((SwitchPreferenceCompat)preference).isChecked());
            else if (preference == prefResetCertificates)
                resetCertificates();
            else if (preference == prefLogToExternalStorage)
                setExternalLogging(((SwitchPreferenceCompat)preference).isChecked());
            else
                return false;
            return true;
        }

        private void resetHints() {
            settings.remove(StartupDialogFragment.HINT_BATTERY_OPTIMIZATIONS);
            settings.remove(StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED);
            settings.remove(StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED);
            Snackbar.make(getView(), R.string.app_settings_reset_hints_success, Snackbar.LENGTH_LONG).show();
        }

        private void setDistrustSystemCerts(boolean distrust) {
            settings.putBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, distrust);

            // re-initialize certificate manager
            App app = (App)getContext().getApplicationContext();
            app.reinitCertManager();

            // reinitialize certificate manager of :sync process
            getContext().sendBroadcast(new Intent(App.ReinitSettingsReceiver.ACTION_REINIT_SETTINGS));
        }

        private void resetCertificates() {
            App.getCertManager().resetCertificates();
            Snackbar.make(getView(), getString(R.string.app_settings_reset_certificates_success), Snackbar.LENGTH_LONG).show();
        }

        private void setExternalLogging(boolean externalLogging) {
            settings.putBoolean(App.LOG_TO_EXTERNAL_STORAGE, externalLogging);

            // reinitialize logger of default process
            App app = (App)getContext().getApplicationContext();
            app.reinitLogger();

            // reinitialize logger of :sync process
            getContext().sendBroadcast(new Intent(App.ReinitSettingsReceiver.ACTION_REINIT_SETTINGS));
        }
    }

}
