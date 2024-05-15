package at.bitfire.davdroid.ui.account

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.PermissionSwitchRow
import at.bitfire.davdroid.util.PermissionUtils

@Composable
fun WifiPermissionsScreen(
    backgroundPermissionOptionLabel: String,
    locationServiceEnabled: Boolean,
    onEnableLocationService: (Boolean) -> Unit,
    onNavUp: () -> Unit
) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                stringResource(R.string.navigate_up)
                            )
                        }
                    },
                    title = { Text(stringResource(R.string.wifi_permissions_label)) },
                    actions = {
                        val uriHandler = LocalUriHandler.current
                        IconButton(onClick = {
                            uriHandler.openUri(
                                Constants.HOMEPAGE_URL.buildUpon()
                                    .appendPath(Constants.HOMEPAGE_PATH_FAQ)
                                    .appendPath(Constants.HOMEPAGE_PATH_FAQ_LOCATION_PERMISSION)
                                    .withStatParams("WifiPermissionsActivity")
                                    .build().toString()
                            )
                        }) {
                            Icon(Icons.AutoMirrored.Default.Help, stringResource(R.string.help))
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                WifiPermissionsScreenContent(
                    backgroundPermissionOptionLabel = backgroundPermissionOptionLabel,
                    locationServiceEnabled = locationServiceEnabled,
                    onEnableLocationService = onEnableLocationService
                )
            }
        }
    }
}

@Composable
fun WifiPermissionsScreenContent(
    backgroundPermissionOptionLabel: String,
    locationServiceEnabled: Boolean,
    onEnableLocationService: (Boolean) -> Unit
) {
    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.wifi_permissions_intro),
            style = MaterialTheme.typography.body1
        )

        // Android 8.1+: location permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
            LocationPermission(
                modifier = Modifier.padding(top = 16.dp)
            )

        // Android 10+: background location permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            BackgroundLocationPermission(
                backgroundPermissionOptionLabel = backgroundPermissionOptionLabel,
                modifier = Modifier.padding(top = 16.dp)
            )

        // Android 9+: location service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            LocationService(
                locationServiceEnabled = locationServiceEnabled,
                modifier = Modifier.padding(top = 16.dp),
                onEnableLocationService = onEnableLocationService
            )

        // If permissions have actively been denied
        Text(
            stringResource(R.string.permissions_app_settings_hint),
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(top = 16.dp)
        )
        val context = LocalContext.current
        OutlinedButton(
            onClick = { PermissionUtils.showAppSettings(context) }
        ) {
            Text(stringResource(R.string.permissions_app_settings))
        }

        Divider(Modifier.padding(vertical = 16.dp))

        // Disclaimer
        Row {
            Text(
                stringResource(
                    R.string.wifi_permissions_background_location_disclaimer, stringResource(
                        R.string.app_name)
                ),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.CloudOff, null, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun LocationPermission(
    modifier: Modifier = Modifier
) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        Manifest.permission.ACCESS_FINE_LOCATION    // since Android 10, fine location is required
    else
        Manifest.permission.ACCESS_COARSE_LOCATION  // Android 8+: coarse location is enough

    PermissionSwitchRow(
        text = stringResource(R.string.wifi_permissions_location_permission),
        permissions = listOf(permission),
        summaryWhenGranted = stringResource(R.string.wifi_permissions_location_permission_on),
        summaryWhenNotGranted = stringResource(R.string.wifi_permissions_location_permission_off),
        modifier = modifier
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun BackgroundLocationPermission(
    backgroundPermissionOptionLabel: String,
    modifier: Modifier = Modifier
) {
    PermissionSwitchRow(
        text = stringResource(R.string.wifi_permissions_background_location_permission),
        permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        summaryWhenGranted = stringResource(R.string.wifi_permissions_background_location_permission_on, backgroundPermissionOptionLabel),
        summaryWhenNotGranted = stringResource(R.string.wifi_permissions_background_location_permission_off, backgroundPermissionOptionLabel),
        modifier = modifier
    )
}

@Composable
fun LocationService(
    locationServiceEnabled: Boolean,
    modifier: Modifier = Modifier,
    onEnableLocationService: (Boolean) -> Unit
) {
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.wifi_permissions_location_enabled),
                style = MaterialTheme.typography.body1
            )
            Text(
                stringResource(
                    if (locationServiceEnabled)
                        R.string.wifi_permissions_location_enabled_on
                    else
                        R.string.wifi_permissions_location_enabled_off
                ),
                style = MaterialTheme.typography.body2
            )
        }
        Switch(
            checked = locationServiceEnabled,
            onCheckedChange = onEnableLocationService
        )
    }
}

@Composable
@Preview
fun Content_Preview() {
    WifiPermissionsScreenContent(
        backgroundPermissionOptionLabel = stringResource(R.string.wifi_permissions_background_location_permission_label),
        locationServiceEnabled = true
    ) {}
}