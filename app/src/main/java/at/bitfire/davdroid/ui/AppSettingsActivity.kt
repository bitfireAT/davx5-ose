/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.SwitchPreferenceCompat
import at.bitfire.davdroid.App
import at.bitfire.davdroid.CustomCertificates
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.Settings
import java.net.URI
import java.net.URISyntaxException

class AppSettingsActivity: AppCompatActivity() {

    companion object {
        val EXTRA_SCROLL_TO = "scrollTo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragment = SettingsFragment()
            fragment.arguments = intent.extras
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit()
        }
    }


    class SettingsFragment: PreferenceFragmentCompat() {
        lateinit var dbHelper: ServiceDB.OpenHelper
        lateinit var settings: Settings

        override fun onCreate(savedInstanceState: Bundle?) {
            dbHelper = ServiceDB.OpenHelper(context)
            settings = Settings(dbHelper.readableDatabase)

            // will call onCreatePreferences, so settings should be already initialized
            super.onCreate(savedInstanceState)
        }

        override fun onDestroy() {
            super.onDestroy()
            dbHelper.close()
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.settings_app)

            val prefResetHints = findPreference("reset_hints")
            prefResetHints.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                resetHints()
                true
            }

            val prefOverrideProxy = findPreference("override_proxy") as SwitchPreferenceCompat
            prefOverrideProxy.isChecked = settings.getBoolean(App.OVERRIDE_PROXY, false)
            prefOverrideProxy.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                settings.putBoolean(App.OVERRIDE_PROXY, newValue as Boolean)
                true
            }

            val prefProxyHost = findPreference("proxy_host") as EditTextPreference
            val proxyHost = settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT)
            prefProxyHost.text = proxyHost
            prefProxyHost.summary = proxyHost
            prefProxyHost.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val host = newValue as String
                try {
                    URI(null, host, null, null)
                    settings.putString(App.OVERRIDE_PROXY_HOST, host)
                    prefProxyHost.summary = host
                    true
                } catch(e: URISyntaxException) {
                    Snackbar.make(view!!, e.localizedMessage, Snackbar.LENGTH_LONG).show()
                    false
                }
            }

            val prefProxyPort = findPreference("proxy_port") as EditTextPreference
            val proxyPort = settings.getString(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT.toString())
            prefProxyPort.text = proxyPort
            prefProxyPort.summary = proxyPort
            prefProxyPort.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                var port: Int
                try {
                    port = Integer.parseInt(newValue as String)
                } catch(e: NumberFormatException) {
                    port = App.OVERRIDE_PROXY_PORT_DEFAULT
                }
                settings.putInt(App.OVERRIDE_PROXY_PORT, port)
                prefProxyPort.text = port.toString()
                prefProxyPort.summary = port.toString()
                true
            }

            val prefDistrustSystemCerts = findPreference("distrust_system_certs") as SwitchPreferenceCompat
            if (CustomCertificates.certManager == null)
                prefDistrustSystemCerts.isVisible = false
            else
                prefDistrustSystemCerts.isChecked = settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false)
            prefDistrustSystemCerts.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                setDistrustSystemCerts(prefDistrustSystemCerts.isChecked)
                true
            }

            val prefResetCertificates = findPreference("reset_certificates")
            if (CustomCertificates.certManager == null)
                prefResetCertificates.isVisible = false
            prefResetCertificates.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                resetCertificates()
                true
            }

            val prefLogToExternalStorage = findPreference("log_to_external_storage") as SwitchPreferenceCompat
            prefLogToExternalStorage.isChecked = settings.getBoolean(App.LOG_TO_EXTERNAL_STORAGE, false)
            prefLogToExternalStorage.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                setExternalLogging(prefLogToExternalStorage.isChecked)
                true
            }

            arguments?.getString(EXTRA_SCROLL_TO)?.let { scrollToPreference(it) }
        }

        private fun resetHints() {
            settings.remove(StartupDialogFragment.HINT_BATTERY_OPTIMIZATIONS)
            settings.remove(StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED)
            settings.remove(StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED)
            Snackbar.make(view!!, R.string.app_settings_reset_hints_success, Snackbar.LENGTH_LONG).show()
        }

        private fun setDistrustSystemCerts(distrust: Boolean) {
            settings.putBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, distrust)

            // re-initialize certificate manager
            CustomCertificates.reinitCertManager(context)

            // reinitialize certificate manager of :sync process
            context.sendBroadcast(Intent(Settings.ReinitSettingsReceiver.ACTION_REINIT_SETTINGS))
        }

        private fun resetCertificates() {
            CustomCertificates.certManager?.resetCertificates()
            Snackbar.make(view!!, getString(R.string.app_settings_reset_certificates_success), Snackbar.LENGTH_LONG).show()
        }

        private fun setExternalLogging(externalLogging: Boolean) {
            settings.putBoolean(App.LOG_TO_EXTERNAL_STORAGE, externalLogging)

            // reinitialize logger of default process
            Logger.reinitLogger(context)

            // reinitialize logger of :sync process
            context.sendBroadcast(Intent(Settings.ReinitSettingsReceiver.ACTION_REINIT_SETTINGS))
        }
    }

}
