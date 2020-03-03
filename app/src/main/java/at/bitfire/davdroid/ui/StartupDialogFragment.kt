/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.fragment.app.DialogFragment
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class StartupDialogFragment: DialogFragment() {

    enum class Mode {
        AUTOSTART_PERMISSIONS,
        BATTERY_OPTIMIZATIONS,
        OPENTASKS_NOT_INSTALLED,
        OSE_DONATE
    }

    companion object {

        private const val SETTING_NEXT_DONATION_POPUP = "time_nextDonationPopup"

        const val HINT_AUTOSTART_PERMISSIONS = "hint_AutostartPermissions"
        // see https://github.com/jaredrummler/AndroidDeviceNames/blob/master/json/ for manufacturer values
        private val autostartManufacturers = arrayOf("huawei", "letv", "oneplus", "vivo", "xiaomi", "zte")

        const val HINT_BATTERY_OPTIMIZATIONS = "hint_BatteryOptimizations"
        const val HINT_OPENTASKS_NOT_INSTALLED = "hint_OpenTasksNotInstalled"

        const val ARGS_MODE = "mode"

        fun getStartupDialogs(context: Context): List<StartupDialogFragment> {
            val dialogs = LinkedList<StartupDialogFragment>()
            val settings = Settings.getInstance(context)

            if (System.currentTimeMillis() > settings.getLong(SETTING_NEXT_DONATION_POPUP) ?: 0)
                dialogs += StartupDialogFragment.instantiate(Mode.OSE_DONATE)

            // battery optimization white-listing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && settings.getBoolean(HINT_BATTERY_OPTIMIZATIONS) != false) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID))
                    dialogs.add(instantiate(Mode.BATTERY_OPTIMIZATIONS))
            }

            // vendor-specific auto-start information
            if (autostartManufacturers.contains(Build.MANUFACTURER.toLowerCase(Locale.ROOT)) && settings.getBoolean(HINT_AUTOSTART_PERMISSIONS) != false)
                dialogs.add(instantiate(Mode.AUTOSTART_PERMISSIONS))

            // OpenTasks information
            if (true /* don't show in other flavors */)
                if (!LocalTaskList.tasksProviderAvailable(context) && settings.getBoolean(HINT_OPENTASKS_NOT_INSTALLED) != false)
                    dialogs.add(instantiate(Mode.OPENTASKS_NOT_INSTALLED))

            return dialogs.reversed()
        }

        fun instantiate(mode: Mode): StartupDialogFragment {
            val frag = StartupDialogFragment()
            val args = Bundle(1)
            args.putString(ARGS_MODE, mode.name)
            frag.arguments = args
            return frag
        }

    }

    
    @SuppressLint("BatteryLife")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val settings = Settings.getInstance(requireActivity())
        val activity = requireActivity()
        return when (Mode.valueOf(requireArguments().getString(ARGS_MODE)!!)) {
            Mode.AUTOSTART_PERMISSIONS ->
                MaterialAlertDialogBuilder(activity)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.startup_autostart_permission)
                        .setMessage(getString(R.string.startup_autostart_permission_message, Build.MANUFACTURER))
                        .setPositiveButton(R.string.startup_more_info) { _, _ ->
                            UiUtils.launchUri(requireActivity(), App.homepageUrl(requireActivity()).buildUpon()
                                    .appendPath("faq").appendPath("synchronization-is-not-run-as-expected").build())
                        }
                        .setNeutralButton(R.string.startup_not_now) { _, _ -> }
                        .setNegativeButton(R.string.startup_dont_show_again) { _, _ ->
                            settings.putBoolean(HINT_AUTOSTART_PERMISSIONS, false)
                        }
                        .create()

            Mode.BATTERY_OPTIMIZATIONS ->
                MaterialAlertDialogBuilder(activity)
                        .setIcon(R.drawable.ic_info_dark)
                        .setTitle(R.string.startup_battery_optimization)
                        .setMessage(R.string.startup_battery_optimization_message)
                        .setPositiveButton(R.string.startup_battery_optimization_disable) @TargetApi(Build.VERSION_CODES.M) { _, _ ->
                            UiUtils.launchUri(requireActivity(), Uri.parse("package:" + BuildConfig.APPLICATION_ID),
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        }
                        .setNeutralButton(R.string.startup_not_now) { _, _ -> }
                        .setNegativeButton(R.string.startup_dont_show_again) { _: DialogInterface, _: Int ->
                            settings.putBoolean(HINT_BATTERY_OPTIMIZATIONS, false)
                        }
                        .create()

            Mode.OPENTASKS_NOT_INSTALLED -> {
                val builder = StringBuilder(getString(R.string.startup_opentasks_not_installed_message))
                if (Build.VERSION.SDK_INT < 23)
                    builder.append("\n\n").append(getString(R.string.startup_opentasks_reinstall_davx5))
                return MaterialAlertDialogBuilder(activity)
                        .setIcon(R.drawable.ic_playlist_add_check_dark)
                        .setTitle(R.string.startup_opentasks_not_installed)
                        .setMessage(builder.toString())
                        .setPositiveButton(R.string.startup_opentasks_not_installed_install) { _, _ ->
                            if (!UiUtils.launchUri(requireActivity(), Uri.parse("market://details?id=org.dmfs.tasks"), toastInstallBrowser = false))
                                Logger.log.warning("No market app available, can't install OpenTasks")
                        }
                        .setNeutralButton(R.string.startup_not_now) { _, _ -> }
                        .setNegativeButton(R.string.startup_dont_show_again) { _: DialogInterface, _: Int ->
                            settings.putBoolean(HINT_OPENTASKS_NOT_INSTALLED, false)
                        }
                        .create()
            }

            Mode.OSE_DONATE ->
                    return MaterialAlertDialogBuilder(activity)
                            .setIcon(R.mipmap.ic_launcher)
                            .setTitle(R.string.startup_donate)
                            .setMessage(R.string.startup_donate_message)
                            .setPositiveButton(R.string.startup_donate_now) { _, _ ->
                                UiUtils.launchUri(requireActivity(), App.homepageUrl(requireActivity()).buildUpon()
                                        .appendPath("donate")
                                        .build())
                                settings.putLong(SETTING_NEXT_DONATION_POPUP, System.currentTimeMillis() + 30 * 86400000L) // 30 days
                            }
                            .setNegativeButton(R.string.startup_donate_later) { _, _ ->
                                settings.putLong(SETTING_NEXT_DONATION_POPUP, System.currentTimeMillis() + 14 * 86400000L) // 14 days
                            }
                            .create()

        }
    }

}
