/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.ui.PermissionsActivity
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.MutableStateFlow

object PermissionUtils {

    /** There's an undocumented intent that is sent when the battery optimization whitelist changes. */
    const val ACTION_POWER_SAVE_WHITELIST_CHANGED = "android.os.action.POWER_SAVE_WHITELIST_CHANGED"

    val CONTACT_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )
    val CALENDAR_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    val WIFI_SSID_PERMISSIONS =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            else ->
                arrayOf()
        }

    /**
     * Checks whether all conditions to access the current WiFi's SSID are met:
     *
     * 1. location permissions ([WIFI_SSID_PERMISSIONS]) granted (Android 8.1+)
     * 2. location enabled (Android 9+)
     *
     * @return *true* if SSID can be obtained; *false* if the SSID will be <unknown> or something like that
     */
    fun canAccessWifiSsid(context: Context): Boolean {
        // before Android 8.1, SSIDs are always readable
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1)
            return true

        val locationAvailable =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                    true    // Android <9 doesn't require active location services
                else
                    context.getSystemService<LocationManager>()?.let { locationManager ->
                        LocationManagerCompat.isLocationEnabled(locationManager)
                    } ?: /* location feature not available on this device */ false

        return  havePermissions(context, WIFI_SSID_PERMISSIONS) &&
                locationAvailable
    }

    /**
     * Checks whether all conditions to access the current WiFi's SSID are met:
     *
     * 1. location permissions ([WIFI_SSID_PERMISSIONS]) granted (Android 8.1+)
     * 2. location enabled (Android 9+)
     *
     * @return *true* if SSID can be obtained; *false* if the SSID will be <unknown> or something like that
     */
    @Composable
    @ExperimentalPermissionsApi
    fun canAccessWifiSsidLive(): State<Boolean> {
        // If preview, cannot access WiFi SSID
        if (LocalInspectionMode.current)
            return remember { derivedStateOf { false } }

        // before Android 8.1, SSIDs are always readable
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1)
            return remember { derivedStateOf { true } }

        val context = LocalContext.current

        val locationAvailable = MutableStateFlow(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            fun updateState(context: Context) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                locationAvailable.tryEmit(isGpsEnabled || isNetworkEnabled)
            }

            val br = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    intent?.action?.let { act ->
                        if (act.matches("android.location.PROVIDERS_CHANGED".toRegex())) {
                            updateState(context)
                        }
                    }
                }
            }
            DisposableEffect(Unit) {
                val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
                context.registerReceiver(br, filter)

                // Update the initial state
                updateState(context)

                onDispose { context.unregisterReceiver(br) }
            }
        } else {
            LaunchedEffect(Unit) {
                // Android <9 doesn't require active location services
                locationAvailable.tryEmit(true)
            }
        }

        val permissions = rememberMultiplePermissionsState(
            permissions = WIFI_SSID_PERMISSIONS.toList()
        )
        return produceState(
            initialValue = false,
            permissions.allPermissionsGranted,
            locationAvailable
        ) {
            val granted = permissions.allPermissionsGranted
            val location = locationAvailable.value
            value = granted && location
        }
    }

    /**
     * Checks whether at least one of the given permissions is granted.
     *
     * @param context context to check
     * @param permissions array of permissions to check
     *
     * @return whether at least one of [permissions] is granted
     */
    fun haveAnyPermission(context: Context, permissions: Array<String>) =
            permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Checks whether all given permissions are granted.
     *
     * @param context context to check
     * @param permissions array of permissions to check
     *
     * @return whether all [permissions] are granted
     */
    fun havePermissions(context: Context, permissions: Array<String>) =
            permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Shows a notification about missing permissions.
     *
     * @param context notification context
     * @param intent will be set as content Intent; if null, an Intent to launch PermissionsActivity will be used
     */
    fun notifyPermissions(context: Context, intent: Intent?) {
        val contentIntent = intent ?: Intent(context, PermissionsActivity::class.java)
        val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
                .setSmallIcon(R.drawable.ic_sync_problem_notify)
                .setContentTitle(context.getString(R.string.sync_error_permissions))
                .setContentText(context.getString(R.string.sync_error_permissions_text))
                .setContentIntent(PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context)
                .notifyIfPossible(NotificationUtils.NOTIFY_PERMISSIONS, notify)
    }

    fun showAppSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
        if (intent.resolveActivity(context.packageManager) != null)
            context.startActivity(intent)
        else
            Logger.log.warning("App settings Intent not resolvable")
    }

}