/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.Settings;
import at.bitfire.davdroid.resource.LocalTaskList;
import lombok.Cleanup;

public class StartupDialogFragment extends DialogFragment {
    public static final String
            HINT_BATTERY_OPTIMIZATIONS = "hint_BatteryOptimizations",
            HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED = "hint_GooglePlayAccountsRemoved",
            HINT_OPENTASKS_NOT_INSTALLED = "hint_OpenTasksNotInstalled";

    private static final String ARGS_MODE = "mode";

    enum Mode {
        BATTERY_OPTIMIZATIONS,
        DEVELOPMENT_VERSION,
        GOOGLE_PLAY_ACCOUNTS_REMOVED,
        OPENTASKS_NOT_INSTALLED
    }

    public static StartupDialogFragment[] getStartupDialogs(Context context) {
        List<StartupDialogFragment> dialogs = new LinkedList<>();

        @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(context);
        Settings settings  = new Settings(dbHelper.getReadableDatabase());

        if (BuildConfig.VERSION_NAME.contains("-alpha") || BuildConfig.VERSION_NAME.contains("-beta") || BuildConfig.VERSION_NAME.contains("-rc"))
            dialogs.add(StartupDialogFragment.instantiate(Mode.DEVELOPMENT_VERSION));
        else {
            // store-specific information
            if (BuildConfig.FLAVOR == App.FLAVOR_GOOGLE_PLAY) {
                // Play store
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP &&    // only on Android <5
                    settings.getBoolean(HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED, true))      // and only when "Don't show again" hasn't been clicked yet
                    dialogs.add(StartupDialogFragment.instantiate(Mode.GOOGLE_PLAY_ACCOUNTS_REMOVED));
            }
        }

        // battery optimization whitelisting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && settings.getBoolean(HINT_BATTERY_OPTIMIZATIONS, true)) {
            PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID))
                dialogs.add(StartupDialogFragment.instantiate(Mode.BATTERY_OPTIMIZATIONS));
        }

        // OpenTasks information
        if (!LocalTaskList.tasksProviderAvailable(context) && settings.getBoolean(HINT_OPENTASKS_NOT_INSTALLED, true))
            dialogs.add(StartupDialogFragment.instantiate(Mode.OPENTASKS_NOT_INSTALLED));

        Collections.reverse(dialogs);
        return dialogs.toArray(new StartupDialogFragment[dialogs.size()]);
    }

    public static StartupDialogFragment instantiate(Mode mode) {
        StartupDialogFragment frag = new StartupDialogFragment();
        Bundle args = new Bundle(1);
        args.putString(ARGS_MODE, mode.name());
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    @TargetApi(Build.VERSION_CODES.M)
    @SuppressLint("BatteryLife")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(false);

        Mode mode = Mode.valueOf(getArguments().getString(ARGS_MODE));
        switch (mode) {
            case BATTERY_OPTIMIZATIONS:
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.startup_battery_optimization)
                        .setMessage(R.string.startup_battery_optimization_message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNeutralButton(R.string.startup_battery_optimization_disable, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                                if (intent.resolveActivity(getContext().getPackageManager()) != null)
                                    getContext().startActivity(intent);
                            }
                        })
                        .setNegativeButton(R.string.startup_dont_show_again, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());
                                Settings settings = new Settings(dbHelper.getWritableDatabase());
                                settings.putBoolean(HINT_BATTERY_OPTIMIZATIONS, false);
                            }
                        })
                        .create();

            case DEVELOPMENT_VERSION:
                return new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.startup_development_version)
                        .setMessage(getString(R.string.startup_development_version_message, getString(R.string.app_name)))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNeutralButton(R.string.startup_development_version_give_feedback, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(getString(R.string.startup_development_version_feedback_url))));
                            }
                        })
                        .create();

            case GOOGLE_PLAY_ACCOUNTS_REMOVED:
                Drawable icon = null;
                try {
                    icon = getContext().getPackageManager().getApplicationIcon("com.android.vending").getCurrent();
                } catch (PackageManager.NameNotFoundException e) {
                    App.log.log(Level.WARNING, "Can't load Play Store icon", e);
                }
                return new AlertDialog.Builder(getActivity())
                        .setIcon(icon)
                        .setTitle(R.string.startup_google_play_accounts_removed)
                        .setMessage(R.string.startup_google_play_accounts_removed_message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNeutralButton(R.string.startup_google_play_accounts_removed_more_info, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.navigation_drawer_faq_url)));
                                getContext().startActivity(intent);
                            }
                        })
                        .setNegativeButton(R.string.startup_dont_show_again, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());
                                Settings settings = new Settings(dbHelper.getWritableDatabase());
                                settings.putBoolean(HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED, false);
                            }
                        })
                        .create();

            case OPENTASKS_NOT_INSTALLED:
                StringBuilder builder = new StringBuilder(getString(R.string.startup_opentasks_not_installed_message));
                if (Build.VERSION.SDK_INT < 23)
                    builder.append("\n\n").append(getString(R.string.startup_opentasks_reinstall_davdroid));
                return new AlertDialog.Builder(getActivity())
                        .setIcon(R.drawable.ic_alarm_on_dark)
                        .setTitle(R.string.startup_opentasks_not_installed)
                        .setMessage(builder.toString())
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNeutralButton(R.string.startup_opentasks_not_installed_install, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.dmfs.tasks"));
                                if (intent.resolveActivity(getContext().getPackageManager()) != null)
                                    getContext().startActivity(intent);
                                else
                                    App.log.warning("No market app available, can't install OpenTasks");
                            }
                        })
                        .setNegativeButton(R.string.startup_dont_show_again, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());
                                Settings settings = new Settings(dbHelper.getWritableDatabase());
                                settings.putBoolean(HINT_OPENTASKS_NOT_INSTALLED, false);
                            }
                        })
                        .create();
        }

        throw new IllegalArgumentException(/* illegal mode argument */);
    }

    private static String installedFrom(Context context) {
        try {
            return context.getPackageManager().getInstallerPackageName(context.getPackageName());
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

}
