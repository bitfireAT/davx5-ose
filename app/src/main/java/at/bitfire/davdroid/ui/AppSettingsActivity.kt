/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.settings.ISettingsObserver
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


    class SettingsFragment: PreferenceFragmentCompat() {

        val observer = object: ISettingsObserver.Stub() {
            override fun onSettingsChanged() {
                loadSettings()
            }
        }

        var settings: ISettings? = null
        var settingsSvc: ServiceConnection? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val serviceConn = object: ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    settings = ISettings.Stub.asInterface(binder)
                    settings?.registerObserver(observer)
                    loadSettings()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    settings?.unregisterObserver(observer)
                    settings = null
                }
            }
            if (activity!!.bindService(Intent(activity, Settings::class.java), serviceConn, Context.BIND_AUTO_CREATE))
                settingsSvc = serviceConn
        }

        override fun onDestroy() {
            super.onDestroy()
            settingsSvc?.let { activity!!.unbindService(it) }
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.settings_app)

            // UI settings
            val prefResetHints = findPreference("reset_hints")
            prefResetHints.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                resetHints()
                false
            }

            // security settings
            val prefDistrustSystemCerts = findPreference(App.DISTRUST_SYSTEM_CERTIFICATES)
            prefDistrustSystemCerts.isVisible = BuildConfig.customCerts
            prefDistrustSystemCerts.isEnabled = true

            val prefResetCertificates = findPreference("reset_certificates")
            prefResetCertificates.isVisible = BuildConfig.customCerts
            prefResetCertificates.isEnabled = true
            prefResetCertificates.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                resetCertificates()
                false
            }

            arguments?.getString(EXTRA_SCROLL_TO)?.let { scrollToPreference(it) }
        }

        private fun loadSettings() {
            val settings = requireNotNull(settings)

            // connection settings
            val prefOverrideProxy = findPreference(App.OVERRIDE_PROXY) as SwitchPreferenceCompat
            prefOverrideProxy.isChecked = settings.getBoolean(App.OVERRIDE_PROXY, false)
            prefOverrideProxy.isEnabled = settings.isWritable(App.OVERRIDE_PROXY)

            val prefProxyHost = findPreference(App.OVERRIDE_PROXY_HOST) as EditTextPreference
            prefProxyHost.isEnabled = settings.isWritable(App.OVERRIDE_PROXY_HOST)
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

            val prefProxyPort = findPreference(App.OVERRIDE_PROXY_PORT) as EditTextPreference
            prefProxyHost.isEnabled = settings.isWritable(App.OVERRIDE_PROXY_PORT)
            val proxyPort = settings.getInt(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
            prefProxyPort.text = proxyPort.toString()
            prefProxyPort.summary = proxyPort.toString()
            prefProxyPort.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                try {
                    val port = Integer.parseInt(newValue as String)
                    if (port in 1..65535) {
                        settings.putInt(App.OVERRIDE_PROXY_PORT, port)
                        prefProxyPort.text = port.toString()
                        prefProxyPort.summary = port.toString()
                        true
                    } else
                        false
                } catch(e: NumberFormatException) {
                    false
                }
            }

            // security settings
            val prefDistrustSystemCerts = findPreference(App.DISTRUST_SYSTEM_CERTIFICATES) as SwitchPreferenceCompat
            prefDistrustSystemCerts.isChecked = settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false)

            // debugging settings
            val prefLogToExternalStorage = findPreference(Logger.LOG_TO_EXTERNAL_STORAGE) as SwitchPreferenceCompat
            prefLogToExternalStorage.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val context = activity!!
                Logger.initialize(context)

                // kill a potential :sync process, so that the new logger settings will be used
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.runningAppProcesses.forEach {
                    if (it.pid != Process.myPid()) {
                        Logger.log.info("Killing ${it.processName} process, pid = ${it.pid}")
                        Process.killProcess(it.pid)
                    }
                }
                true
            }
        }

        private fun resetHints() {
            settings?.remove(StartupDialogFragment.HINT_AUTOSTART_PERMISSIONS)
            settings?.remove(StartupDialogFragment.HINT_BATTERY_OPTIMIZATIONS)
            settings?.remove(StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED)
            settings?.remove(StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED)
            Snackbar.make(view!!, R.string.app_settings_reset_hints_success, Snackbar.LENGTH_LONG).show()
        }

        private fun resetCertificates() {
            if (CustomCertManager.resetCertificates(activity!!))
                Snackbar.make(view!!, getString(R.string.app_settings_reset_certificates_success), Snackbar.LENGTH_LONG).show()
        }

    }

}
