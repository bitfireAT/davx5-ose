/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.BuildConfig
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppSettingsActivity: AppCompatActivity() {

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppSettingsScreen(
                onExemptFromBatterySaving = {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                        )
                    )
                },
                onBatterySavingSettings = {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                onNavTasksScreen = {
                    startActivity(Intent(this, TasksActivity::class.java))
                },
                onShowNotificationSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                            }
                        )
                },
                onNavUp = ::onSupportNavigateUp
            )
        }
    }

}