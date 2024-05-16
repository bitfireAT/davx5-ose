/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.BuildConfig
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppSettingsActivity: AppCompatActivity() {

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
                onBatterSavingSettings = {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                onStartTasksApp = {
                    startActivity(Intent(this, TasksActivity::class.java))
                },
                onNavUp = { onSupportNavigateUp() },
                onShowNotificationSettings = {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                        }
                    )
                }
            )
        }
    }
}