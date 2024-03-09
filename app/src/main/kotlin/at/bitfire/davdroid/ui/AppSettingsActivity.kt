/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.Intent
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPage
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class AppSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_SCROLL_TO = "scrollTo"

        /**
         * Matches all language qualifiers with a region of three characters, which is not supported
         * by Java's Locale.
         * @see resourceQualifierToLanguageTag
         */
        private val langRegex = Regex(".*-.{3}")

        /**
         * Converts the language qualifier given from Android to Java Locale language tag.
         * @param lang The qualifier to convert. Example: `en`, `zh-rTW`...
         * @return A correct language code to be introduced into [java.util.Locale.forLanguageTag].
         */
        fun resourceQualifierToLanguageTag(lang: String): String {
            // If the language qualifier is correct, return it
            if (!lang.matches(langRegex)) return lang
            // Otherwise, fix it
            val hyphenIndex = lang.indexOf('-')
            // Remove the first character of the 3 (rGB -> GB, rTW -> TW)
            return lang.substring(0, hyphenIndex) + "-" + lang.substring(hyphenIndex + 2)
        }
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


    @AndroidEntryPoint
    class SettingsFragment: PreferenceFragmentCompat(), SettingsManager.OnChangeListener {

        @Inject lateinit var settings: SettingsManager

        val onBatteryOptimizationResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            loadSettings()
        }


        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.settings_app)

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

            findPreference<EditTextPreference>(Settings.PROXY_HOST)!!.apply {
                this.setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_TEXT_VARIATION_URI
                }
            }

            findPreference<EditTextPreference>(Settings.PROXY_PORT)!!.apply {
                this.setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }

            arguments?.getString(EXTRA_SCROLL_TO)?.let { key ->
                scrollToPreference(key)
            }
        }

        override fun onStart() {
            super.onStart()
            settings.addOnChangeListener(this)
            loadSettings()
        }

        override fun onStop() {
            super.onStop()
            settings.removeOnChangeListener(this)
        }

        @UiThread
        private fun loadSettings() {
            // debug settings
            findPreference<SwitchPreferenceCompat>(Settings.BATTERY_OPTIMIZATION)!!.apply {
                // battery optimization exists since Android 6 (API level 23)
                val powerManager = requireActivity().getSystemService<PowerManager>()!!
                val whitelisted = powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
                isChecked = whitelisted
                isEnabled = !whitelisted
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, nowChecked ->
                    if (nowChecked as Boolean)
                        onBatteryOptimizationResult.launch(Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                        ))
                    false
                }
            }

            // connection settings
            val proxyType = settings.getInt(Settings.PROXY_TYPE)
            findPreference<ListPreference>(Settings.PROXY_TYPE)!!.apply {
                setValueIndex(entryValues.indexOf(proxyType.toString()))
                summary = entry
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    val proxyType = (newValue as String).toInt()
                    settings.putInt(Settings.PROXY_TYPE, proxyType)
                    false
                }
            }

            findPreference<EditTextPreference>(Settings.PROXY_HOST)!!.apply {
                isVisible = proxyType != Settings.PROXY_TYPE_SYSTEM && proxyType != Settings.PROXY_TYPE_NONE
                isEnabled = settings.isWritable(Settings.PROXY_HOST)

                val proxyHost = settings.getString(Settings.PROXY_HOST)
                text = proxyHost
                summary = proxyHost
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    val host = newValue as String
                    try {
                        URI(null, host, null, null)
                        settings.putString(Settings.PROXY_HOST, host)
                        summary = host
                        false
                    } catch(e: URISyntaxException) {
                        Snackbar.make(requireView(), e.reason, Snackbar.LENGTH_LONG).show()
                        false
                    }
                }
            }

            findPreference<EditTextPreference>(Settings.PROXY_PORT)!!.apply {
                isVisible = proxyType != Settings.PROXY_TYPE_SYSTEM && proxyType != Settings.PROXY_TYPE_NONE
                isEnabled = settings.isWritable(Settings.PROXY_PORT)

                val proxyPort = settings.getInt(Settings.PROXY_PORT)
                text = proxyPort.toString()
                summary = proxyPort.toString()
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    try {
                        val port = (newValue as String).toInt()
                        if (port in 1..65535) {
                            settings.putInt(Settings.PROXY_PORT, port)
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

            // user interface settings
            findPreference<ListPreference>(Settings.PREFERRED_THEME)!!.apply {
                val mode = settings.getIntOrNull(Settings.PREFERRED_THEME) ?: Settings.PREFERRED_THEME_DEFAULT
                setValueIndex(entryValues.indexOf(mode.toString()))
                summary = entry

                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    val newMode = (newValue as String).toInt()
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    settings.putInt(Settings.PREFERRED_THEME, newMode)
                    false
                }
            }

            // integration settings
            findPreference<Preference>(Settings.PREFERRED_TASKS_PROVIDER)!!.apply {
                val pm = requireActivity().packageManager
                val taskProvider = TaskUtils.currentProvider(requireActivity())
                if (taskProvider != null) {
                    val tasksAppInfo = pm.getApplicationInfo(taskProvider.packageName, 0)
                    val inset = (24*resources.displayMetrics.density).roundToInt()  // 24dp
                    icon = InsetDrawable(
                            tasksAppInfo.loadIcon(pm),
                            0, inset, inset, inset
                    )
                    summary = getString(R.string.app_settings_tasks_provider_synchronizing_with, tasksAppInfo.loadLabel(pm))
                } else {
                    setIcon(R.drawable.ic_playlist_add_check)
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
                if (isAdded)
                    loadSettings()
            }
        }

        private fun resetHints() {
            settings.remove(BatteryOptimizationsPage.Model.HINT_BATTERY_OPTIMIZATIONS)
            settings.remove(BatteryOptimizationsPage.Model.HINT_AUTOSTART_PERMISSION)
            settings.remove(TasksModel.HINT_OPENTASKS_NOT_INSTALLED)
            //settings.remove(OpenSourceFragment.Model.SETTING_NEXT_DONATION_POPUP)
            Snackbar.make(requireView(), R.string.app_settings_reset_hints_success, Snackbar.LENGTH_LONG).show()
        }

        private fun resetCertificates() {
            CustomCertStore.getInstance(requireActivity()).clearUserDecisions()

            Snackbar.make(requireView(), getString(R.string.app_settings_reset_certificates_success), Snackbar.LENGTH_LONG).show()
        }

    }

}
