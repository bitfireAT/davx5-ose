/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsFragment
import at.bitfire.davdroid.ui.intro.OpenSourceFragment
import at.bitfire.ical4android.TaskProvider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URISyntaxException
import kotlin.math.roundToInt

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


    class SettingsFragment: PreferenceFragmentCompat(), SettingsManager.OnChangeListener {

        val settings by lazy { SettingsManager.getInstance(requireActivity()) }


        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.settings_app)
            loadSettings()

            settings.addOnChangeListener(this)

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
            findPreference<Preference>("reset_certificates")!!.apply {
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    resetCertificates()
                    false
                }
            }

            arguments?.getString(EXTRA_SCROLL_TO)?.let { key ->
                scrollToPreference(key)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            settings.removeOnChangeListener(this)
        }

        @UiThread
        private fun loadSettings() {
            // connection settings
            findPreference<SwitchPreferenceCompat>(Settings.OVERRIDE_PROXY)!!.apply {
                isChecked = settings.getBoolean(Settings.OVERRIDE_PROXY)
                isEnabled = settings.isWritable(Settings.OVERRIDE_PROXY)
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    settings.putBoolean(Settings.OVERRIDE_PROXY, newValue as Boolean)
                    false
                }
            }

            findPreference<EditTextPreference>(Settings.OVERRIDE_PROXY_HOST)!!.apply {
                isEnabled = settings.isWritable(Settings.OVERRIDE_PROXY_HOST)
                val proxyHost = settings.getString(Settings.OVERRIDE_PROXY_HOST)
                text = proxyHost
                summary = proxyHost
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    val host = newValue as String
                    try {
                        URI(null, host, null, null)
                        settings.putString(Settings.OVERRIDE_PROXY_HOST, host)
                        summary = host
                        false
                    } catch(e: URISyntaxException) {
                        Snackbar.make(requireView(), e.localizedMessage, Snackbar.LENGTH_LONG).show()
                        false
                    }
                }
            }

            findPreference<EditTextPreference>(Settings.OVERRIDE_PROXY_PORT)!!.apply {
                isEnabled = settings.isWritable(Settings.OVERRIDE_PROXY_PORT)
                val proxyPort = settings.getInt(Settings.OVERRIDE_PROXY_PORT)
                text = proxyPort.toString()
                summary = proxyPort.toString()
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    try {
                        val port = Integer.parseInt(newValue as String)
                        if (port in 1..65535) {
                            settings.putInt(Settings.OVERRIDE_PROXY_PORT, port)
                            text = port.toString()
                            summary = port.toString()
                            false
                        } else
                            false
                    } catch(e: NumberFormatException) {
                        false
                    }
                }
            }

            // security settings
            findPreference<SwitchPreferenceCompat>(Settings.DISTRUST_SYSTEM_CERTIFICATES)!!
                    .isChecked = settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES)

            // integration settings
            findPreference<Preference>(Settings.PREFERRED_TASKS_PROVIDER)!!.apply {
                val pm = context.packageManager
                val taskProvider = TaskUtils.currentProvider(context)
                if (taskProvider != null) {
                    val tasksAppInfo = pm.getApplicationInfo(taskProvider.packageName, 0)
                    val inset = (24*resources.displayMetrics.density).roundToInt()  // 24dp
                    icon = InsetDrawable(
                            tasksAppInfo.loadIcon(pm),
                            0, inset, inset, inset
                    )
                    summary = getString(R.string.app_settings_tasks_provider_synchronizing_with, tasksAppInfo.loadLabel(pm))
                } else {
                    setIcon(R.drawable.ic_playlist_add_check_dark)
                    setSummary(R.string.app_settings_tasks_provider_none)
                }
                setOnPreferenceClickListener {
                    startActivity(Intent(requireActivity(), TasksActivity::class.java))
                    false
                }
            }
        }

        override fun onSettingsChanged() {
            // loadSettings must run in UI thread
            CoroutineScope(Dispatchers.Main).launch {
                loadSettings()
            }
        }


        private fun resetHints() {
            val settings = SettingsManager.getInstance(requireActivity())
            settings.remove(BatteryOptimizationsFragment.Model.HINT_BATTERY_OPTIMIZATIONS)
            settings.remove(BatteryOptimizationsFragment.Model.HINT_AUTOSTART_PERMISSION)
            settings.remove(TasksFragment.Model.HINT_OPENTASKS_NOT_INSTALLED)
            settings.remove(OpenSourceFragment.Model.SETTING_NEXT_DONATION_POPUP)
            Snackbar.make(requireView(), R.string.app_settings_reset_hints_success, Snackbar.LENGTH_LONG).show()
        }

        private fun resetCertificates() {
            if (CustomCertManager.resetCertificates(requireActivity()))
                Snackbar.make(requireView(), getString(R.string.app_settings_reset_certificates_success), Snackbar.LENGTH_LONG).show()
        }

    }

}
