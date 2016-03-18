/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.widget.Toast;

import java.security.KeyStoreException;
import java.util.Enumeration;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.R;
import de.duenndns.ssl.MemorizingTrustManager;

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
        Preference  prefResetHints,
                    prefResetCertificates;

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.settings_app);
            prefResetHints = findPreference("reset_hints");
            prefResetCertificates = findPreference("reset_certificates");

            getPreferenceManager().setSharedPreferencesName(App.PREF_FILE);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            if (preference == prefResetHints)
                resetHints();
            else if (preference == prefResetCertificates)
                resetCertificates();
            else
                return false;
            return true;
        }

        private void resetHints() {
            // TODO
        }

        private void resetCertificates() {
            MemorizingTrustManager mtm = App.getMemorizingTrustManager();

            int deleted = 0;
            Enumeration<String> iterator = mtm.getCertificates();
            while (iterator.hasMoreElements())
                try {
                    mtm.deleteCertificate(iterator.nextElement());
                    deleted++;
                } catch (KeyStoreException e) {
                    App.log.log(Level.SEVERE, "Couldn't distrust certificate", e);
                }
            Snackbar.make(getView(), getString(R.string.app_settings_reset_trusted_certificates_success, deleted), Snackbar.LENGTH_LONG).show();
        }
    }

}
