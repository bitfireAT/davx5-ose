/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.util.PermissionUtils.WIFI_SSID_PERMISSIONS
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.logging.Logger

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
     * Returns a live state of whether all conditions to access the current WiFi's SSID are met:
     *
     * 1. location permissions ([WIFI_SSID_PERMISSIONS]) granted (Android 8.1+)
     * 2. location enabled (Android 9+)
     *
     * @return `true` if SSID can be obtained reliably; `false` otherwise (SSID will be "unknown" or something like that)
     */
    @Composable
    @OptIn(ExperimentalPermissionsApi::class)
    fun rememberCanAccessWifiSsid(): State<Boolean> {
        // before Android 8.1, SSIDs are always readable
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1)
            return remember { mutableStateOf(true) }

        val locationAvailableFlow =
            // Android 9+: dynamically check whether Location is enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                locationEnabledFlow(LocalContext.current)
            else
                // Android <9 doesn't require active Location to read the SSID
                flowOf(true)
        val locationAvailable by locationAvailableFlow.collectAsStateWithLifecycle(false)

        val permissions = rememberMultiplePermissionsState(WIFI_SSID_PERMISSIONS.toList())

        return remember {
            derivedStateOf {
                locationAvailable && permissions.allPermissionsGranted
            }
        }
    }

    private fun locationEnabledFlow(context: Context): Flow<Boolean> =
        broadcastReceiverFlow(
            context,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            null,
            immediate = true
        ).map {
            val locationManager = context.getSystemService<LocationManager>()!!
            LocationManagerCompat.isLocationEnabled(locationManager)
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

    fun showAppSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null))
        if (intent.resolveActivity(context.packageManager) != null)
            context.startActivity(intent)
        else
            Logger.getGlobal().warning("App settings Intent not resolvable")
    }

}