/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.Settings
import com.google.android.material.snackbar.Snackbar
import java.net.URI
import java.net.URISyntaxException

class AppSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_SCROLL_TO = "scrollTo"
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


    class SettingsFragment: PreferenceFragmentCompat(), Settings.OnChangeListener {

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.settings_app)
            loadSettings()

            // UI settings
            findPreference<Preference>("notification_settings")!!.apply {
                if (Build.VERSION.SDK_INT >= 26)
                    onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                        })
                        false
                    }
                else
                    isVisible = false
            }
            findPreference<Preference>("reset_hints")!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                resetHints()
                false
            }

            // security settings
            findPreference<SwitchPreferenceCompat>(Settings.DISTRUST_SYSTEM_CERTIFICATES)!!.apply {
                isVisible = BuildConfig.customCerts
                isEnabled = true
            }
            findPreference<Preference>("reset_certificates")!!.apply {
                isVisible = BuildConfig.customCerts
                isEnabled = true
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    resetCertificates()
                    false
                }
            }

            arguments?.getString(EXTRA_SCROLL_TO)?.let { key ->
                scrollToPreference(key)
            }
        }

        private fun loadSettings() {
            val settings = Settings.getInstance(requireActivity())
            
            // connection settings
            findPreference<SwitchPreferenceCompat>(Settings.OVERRIDE_PROXY)!!.apply {
                isChecked = settings.getBoolean(Settings.OVERRIDE_PROXY) ?: Settings.OVERRIDE_PROXY_DEFAULT
                isEnabled = settings.isWritable(Settings.OVERRIDE_PROXY)
            }

            findPreference<EditTextPreference>(Settings.OVERRIDE_PROXY_HOST)!!.apply {
                isEnabled = settings.isWritable(Settings.OVERRIDE_PROXY_HOST)
                val proxyHost = settings.getString(Settings.OVERRIDE_PROXY_HOST) ?: Settings.OVERRIDE_PROXY_HOST_DEFAULT
                text = proxyHost
                summary = proxyHost
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    val host = newValue as String
                    try {
                        URI(null, host, null, null)
                        settings.putString(Settings.OVERRIDE_PROXY_HOST, host)
                        summary = host
                        true
                    } catch(e: URISyntaxException) {
                        Snackbar.make(requireView(), e.localizedMessage, Snackbar.LENGTH_LONG).show()
                        false
                    }
                }
            }

            findPreference<EditTextPreference>(Settings.OVERRIDE_PROXY_PORT)!!.apply {
                isEnabled = settings.isWritable(Settings.OVERRIDE_PROXY_PORT)
                val proxyPort = settings.getInt(Settings.OVERRIDE_PROXY_PORT) ?: Settings.OVERRIDE_PROXY_PORT_DEFAULT
                text = proxyPort.toString()
                summary = proxyPort.toString()
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    try {
                        val port = Integer.parseInt(newValue as String)
                        if (port in 1..65535) {
                            settings.putInt(Settings.OVERRIDE_PROXY_PORT, port)
                            text = port.toString()
                            summary = port.toString()
                            true
                        } else
                            false
                    } catch(e: NumberFormatException) {
                        false
                    }
                }
            }

            // security settings
            findPreference<SwitchPreferenceCompat>(Settings.DISTRUST_SYSTEM_CERTIFICATES)!!
                    .isChecked = settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES) ?: Settings.DISTRUST_SYSTEM_CERTIFICATES_DEFAULT
        }

        override fun onSettingsChanged() {
            loadSettings()
        }


        private fun resetHints() {
            val settings = Settings.getInstance(requireActivity())
            settings.remove(StartupDialogFragment.HINT_AUTOSTART_PERMISSIONS)
            settings.remove(StartupDialogFragment.HINT_BATTERY_OPTIMIZATIONS)
            settings.remove(StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED)
            Snackbar.make(requireView(), R.string.app_settings_reset_hints_success, Snackbar.LENGTH_LONG).show()
        }

        private fun resetCertificates() {
            if (CustomCertManager.resetCertificates(requireActivity()))
                Snackbar.make(requireView(), getString(R.string.app_settings_reset_certificates_success), Snackbar.LENGTH_LONG).show()
        }

    }

}
