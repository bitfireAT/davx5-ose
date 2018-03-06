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
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.app.DialogFragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.app.AlertDialog
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.ISettings
import java.util.*
import java.util.logging.Level

class StartupDialogFragment: DialogFragment(), LoaderManager.LoaderCallbacks<ISettings> {

    enum class Mode {
        BATTERY_OPTIMIZATIONS,
        GOOGLE_PLAY_ACCOUNTS_REMOVED,
        OPENTASKS_NOT_INSTALLED,
        OSE_DONATE
    }

    companion object {

        private const val SETTING_NEXT_DONATION_POPUP = "time_nextDonationPopup"

        const val HINT_BATTERY_OPTIMIZATIONS = "hint_BatteryOptimizations"
        const val HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED = "hint_GooglePlayAccountsRemoved"
        const val HINT_OPENTASKS_NOT_INSTALLED = "hint_OpenTasksNotInstalled"

        const val ARGS_MODE = "mode"

        fun getStartupDialogs(context: Context, settings: ISettings): List<StartupDialogFragment> {
            val dialogs = LinkedList<StartupDialogFragment>()

            /* if (System.currentTimeMillis() > settings.getLong(SETTING_NEXT_DONATION_POPUP, 0))
                dialogs += StartupDialogFragment.instantiate(Mode.OSE_DONATE) */

            // store-specific information
            if (BuildConfig.FLAVOR == App.FLAVOR_GOOGLE_PLAY) {
                // Play store
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP &&         // only on Android <5
                    settings.getBoolean(HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED, true))   // and only when "Don't show again" hasn't been clicked yet
                    dialogs += StartupDialogFragment.instantiate(Mode.GOOGLE_PLAY_ACCOUNTS_REMOVED)
            }

            // battery optimization white-listing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && settings.getBoolean(HINT_BATTERY_OPTIMIZATIONS, true)) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID))
                    dialogs.add(StartupDialogFragment.instantiate(Mode.BATTERY_OPTIMIZATIONS))
            }

            // OpenTasks information
            if (!LocalTaskList.tasksProviderAvailable(context) && settings.getBoolean(HINT_OPENTASKS_NOT_INSTALLED, true))
                dialogs.add(StartupDialogFragment.instantiate(Mode.OPENTASKS_NOT_INSTALLED))

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


    var settings: ISettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(code: Int, args: Bundle?) =
            SettingsLoader(requireActivity())

    override fun onLoadFinished(loader: Loader<ISettings>, result: ISettings?) {
        settings = result
    }

    override fun onLoaderReset(loader: Loader<ISettings>) {
        settings = null
    }


    @SuppressLint("BatteryLife")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val activity = requireActivity()
        val mode = Mode.valueOf(arguments!!.getString(ARGS_MODE))
        return when (mode) {
            Mode.BATTERY_OPTIMIZATIONS ->
                AlertDialog.Builder(activity)
                        .setIcon(R.drawable.ic_info_dark)
                        .setTitle(R.string.startup_battery_optimization)
                        .setMessage(R.string.startup_battery_optimization_message)
                        .setPositiveButton(android.R.string.ok, { _, _ -> })
                        .setNeutralButton(R.string.startup_battery_optimization_disable, @TargetApi(Build.VERSION_CODES.M) { _, _ ->
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                                if (intent.resolveActivity(activity.packageManager) != null)
                                    activity.startActivity(intent)
                            })
                        .setNegativeButton(R.string.startup_dont_show_again, { _: DialogInterface, _: Int ->
                            settings?.putBoolean(HINT_BATTERY_OPTIMIZATIONS, false)
                        })
                        .create()

            Mode.GOOGLE_PLAY_ACCOUNTS_REMOVED -> {
                var icon: Drawable? = null
                try {
                    icon = activity.packageManager.getApplicationIcon("com.android.vending").current
                } catch(e: PackageManager.NameNotFoundException) {
                    Logger.log.log(Level.WARNING, "Can't load Play Store icon", e)
                }
                return AlertDialog.Builder(activity)
                        .setIcon(icon)
                        .setTitle(R.string.startup_google_play_accounts_removed)
                        .setMessage(R.string.startup_google_play_accounts_removed_message)
                        .setPositiveButton(android.R.string.ok, { _, _ -> })
                        .setNeutralButton(R.string.startup_google_play_accounts_removed_more_info, { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.homepage_url)).buildUpon()
                                    .appendPath("faq").appendPath("accounts-gone-after-reboot-or-update").build())
                            activity.startActivity(intent)
                        })
                        .setNegativeButton(R.string.startup_dont_show_again, { _, _ ->
                            settings?.putBoolean(HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED, false)
                        })
                        .create()
            }

            Mode.OPENTASKS_NOT_INSTALLED -> {
                val builder = StringBuilder(getString(R.string.startup_opentasks_not_installed_message))
                if (Build.VERSION.SDK_INT < 23)
                    builder.append("\n\n").append(getString(R.string.startup_opentasks_reinstall_davdroid))
                return AlertDialog.Builder(activity)
                        .setIcon(R.drawable.ic_playlist_add_check_dark)
                        .setTitle(R.string.startup_opentasks_not_installed)
                        .setMessage(builder.toString())
                        .setPositiveButton(android.R.string.ok, { _, _ -> })
                        .setNeutralButton(R.string.startup_opentasks_not_installed_install, { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.dmfs.tasks"))
                            if (intent.resolveActivity(activity.packageManager) != null)
                                activity.startActivity(intent)
                            else
                                Logger.log.warning("No market app available, can't install OpenTasks")
                        })
                        .setNegativeButton(R.string.startup_dont_show_again, { _: DialogInterface, _: Int ->
                            settings?.putBoolean(HINT_OPENTASKS_NOT_INSTALLED, false)
                        })
                        .create()
            }

            Mode.OSE_DONATE ->
                    return AlertDialog.Builder(activity)
                            .setIcon(R.mipmap.ic_launcher)
                            .setTitle(R.string.startup_donate)
                            .setMessage(R.string.startup_donate_message)
                            .setPositiveButton(R.string.startup_donate_now, { _, _ ->
                                val uri = Uri.parse(getString(R.string.homepage_url))
                                        .buildUpon()
                                        .appendEncodedPath("donate/")
                                        .build()
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                                settings?.putLong(SETTING_NEXT_DONATION_POPUP, System.currentTimeMillis() + 30 * 86400000L) // 30 days
                            })
                            .setNegativeButton(R.string.startup_donate_later, { _, _ ->
                                settings?.putLong(SETTING_NEXT_DONATION_POPUP, System.currentTimeMillis() + 14 * 86400000L) // 14 days
                            })
                            .create()

        }
    }


    class SettingsLoader(
            context: Context
    ): at.bitfire.davdroid.ui.SettingsLoader<ISettings?>(context) {

        override fun loadInBackground(): ISettings? {
            settings?.let { return it }
            return null
        }

    }

}
