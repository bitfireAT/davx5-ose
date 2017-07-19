/*
 * Copyright © Ricki Hirner (bitfire web engineering).
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
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.SwitchPreferenceCompat;

import java.net.URI;
import java.net.URISyntaxException;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.Settings;

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
                prefOverrideProxy,
                prefDistrustSystemCerts,
                prefLogToExternalStorage;

        EditTextPreference
                prefProxyHost,
                prefProxyPort;

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

            prefOverrideProxy = (SwitchPreferenceCompat)findPreference("override_proxy");
            prefOverrideProxy.setChecked(settings.getBoolean(App.OVERRIDE_PROXY, false));
            prefOverrideProxy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    settings.putBoolean(App.OVERRIDE_PROXY, (boolean)newValue);
                    return true;
                }
            });

            prefProxyHost = (EditTextPreference)findPreference("proxy_host");
            String proxyHost = settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT);
            prefProxyHost.setText(proxyHost);
            prefProxyHost.setSummary(proxyHost);
            prefProxyHost.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String host = (String)newValue;
                    try {
                        URI uri = new URI(null, host, null, null);
                    } catch(URISyntaxException e) {
                        Snackbar.make(getView(), e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    settings.putString(App.OVERRIDE_PROXY_HOST, host);
                    prefProxyHost.setSummary(host);
                    return true;
                }
            });

            prefProxyPort = (EditTextPreference)findPreference("proxy_port");
            String proxyPort = settings.getString(App.OVERRIDE_PROXY_PORT, String.valueOf(App.OVERRIDE_PROXY_PORT_DEFAULT));
            prefProxyPort.setText(proxyPort);
            prefProxyPort.setSummary(proxyPort);
            prefProxyPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int port;
                    try {
                        port = Integer.parseInt((String)newValue);
                    } catch(NumberFormatException e) {
                        port = App.OVERRIDE_PROXY_PORT_DEFAULT;
                    }
                    settings.putInt(App.OVERRIDE_PROXY_PORT, port);
                    prefProxyPort.setText(String.valueOf(port));
                    prefProxyPort.setSummary(String.valueOf(port));
                    return true;
                }
            });

            prefDistrustSystemCerts = (SwitchPreferenceCompat)findPreference("distrust_system_certs");
            App app = (App)getContext().getApplicationContext();
            if (app.getCertManager() == null)
                prefDistrustSystemCerts.setVisible(false);
            else
                prefDistrustSystemCerts.setChecked(settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false));

            prefResetCertificates = findPreference("reset_certificates");
            if (app.getCertManager() == null)
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
            ((App)getContext().getApplicationContext()).getCertManager().resetCertificates();
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
