/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.composable.PermissionSwitchRow
import at.bitfire.davdroid.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalNetworkAccessPermissionScreen(
    hostname: String,
    onNavUp: () -> Unit
) {
    val context = LocalContext.current

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
                    title = { Text(stringResource(R.string.permissions_local_network_title)) },
                    actions = {
                        val uriHandler = LocalUriHandler.current
                        IconButton(onClick = {
                            uriHandler.openUri(
                                ExternalUris.Manual.baseUrl.buildUpon()
                                    .appendPath(ExternalUris.Manual.PATH_PERMISSIONS)
                                    .withStatParams(context, "LocalNetworkAccessPermissionScreen")
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
                LocalNetworkAccessPermissionScreenContent(hostname = hostname)
            }
        }
    }
}

@Composable
fun LocalNetworkAccessPermissionScreenContent(hostname: String) {
    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())) {

        // Disclaimer
        Row {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(
                        R.string.permissions_local_network_disclaimer,
                        stringResource(R.string.app_name),
                        hostname
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.padding(8.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // Permission switch
        Text(
            stringResource(R.string.permissions_local_network_intro),
            style = MaterialTheme.typography.bodyLarge
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN)
            LocalNetworkPermission(
                modifier = Modifier.padding(top = 16.dp)
            )

        // If permissions have actively been denied
        Text(
            stringResource(R.string.permissions_app_settings_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        val context = LocalContext.current
        OutlinedButton(
            modifier = Modifier.padding(top = 8.dp),
            onClick = { PermissionUtils.showAppSettings(context) }
        ) {
            Text(stringResource(R.string.permissions_app_settings))
        }
    }
}

@Composable
fun LocalNetworkPermission(
    modifier: Modifier = Modifier
) {
    PermissionSwitchRow(
        text = stringResource(R.string.permissions_local_network_title),
        permissions = listOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
        summaryWhenGranted = stringResource(R.string.permissions_local_network_status_on),
        summaryWhenNotGranted = stringResource(R.string.permissions_local_network_status_off),
        modifier = modifier
    )
}

@Composable
@Preview
fun LocalNetworkAccessPermissionScreen_Preview() {
    AppTheme {
        LocalNetworkAccessPermissionScreen(
            hostname = "example.com",
            onNavUp = {}
        )
    }
}
